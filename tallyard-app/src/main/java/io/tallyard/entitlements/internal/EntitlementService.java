package io.tallyard.entitlements.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.tallyard.billing.BillingPeriodView;
import io.tallyard.billing.BillingPeriods;
import io.tallyard.billing.BillingQueries;
import io.tallyard.catalog.CatalogQueries;
import io.tallyard.catalog.CustomerView;
import io.tallyard.catalog.MeterView;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.SubscriptionView;
import io.tallyard.credits.CreditLedger;
import io.tallyard.entitlements.EntitlementDecision;
import io.tallyard.entitlements.EntitlementDecision.Decision;
import io.tallyard.entitlements.EntitlementDecision.Reason;
import io.tallyard.metering.MeterUsage;
import io.tallyard.metering.UsageQueries;
import io.tallyard.shared.config.TallyardProperties;
import io.tallyard.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The hot path. A snapshot (usage, limit, credit remaining) per (tenant, subject, meter) is computed
 * from the database and cached for {@code cache-ttl}; decisions are then pure arithmetic on the
 * snapshot. The TTL is the staleness bound: usage counted in the last {@code cache-ttl} may not yet be
 * reflected in a decision. That is an explicit trade-off (documented in the architecture notes) in
 * favour of predictable low latency over read-your-writes.
 */
@Service
class EntitlementService {

    private final CatalogQueries catalog;
    private final BillingPeriods periods;
    private final BillingQueries billing;
    private final UsageQueries usage;
    private final CreditLedger credits;
    private final Clock clock;
    private final Cache<String, Snapshot> snapshots;
    private final Timer checkTimer;
    private final Counter allowed;
    private final Counter denied;
    private final Counter cacheMisses;

    record Snapshot(
            Reason blocker,
            BigDecimal used,
            BigDecimal limit,
            BigDecimal creditRemaining,
            boolean hasCredits,
            Instant asOf) {}

    EntitlementService(
            CatalogQueries catalog,
            BillingPeriods periods,
            BillingQueries billing,
            UsageQueries usage,
            CreditLedger credits,
            Clock clock,
            TallyardProperties props,
            MeterRegistry registry) {
        this.catalog = catalog;
        this.periods = periods;
        this.billing = billing;
        this.usage = usage;
        this.credits = credits;
        this.clock = clock;
        this.snapshots = Caffeine.newBuilder()
                .maximumSize(props.entitlements().cacheMaxSize())
                .expireAfterWrite(props.entitlements().cacheTtl())
                .build();
        this.checkTimer = Timer.builder("tallyard.entitlements.check")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.allowed = Counter.builder("tallyard.entitlements.decisions")
                .tag("decision", "ALLOW")
                .register(registry);
        this.denied = Counter.builder("tallyard.entitlements.decisions")
                .tag("decision", "DENY")
                .register(registry);
        this.cacheMisses =
                Counter.builder("tallyard.entitlements.snapshot_misses").register(registry);
    }

    EntitlementDecision check(UUID tenantId, String subject, String meterCode, BigDecimal requested) {
        Timer.Sample sample = Timer.start();
        try {
            Snapshot s = snapshots.get(tenantId + "|" + subject + "|" + meterCode, k -> {
                cacheMisses.increment();
                return snapshot(tenantId, subject, meterCode);
            });
            EntitlementDecision d = decide(s, subject, meterCode, requested);
            (d.decision() == Decision.ALLOW ? allowed : denied).increment();
            return d;
        } finally {
            sample.stop(checkTimer);
        }
    }

    /** Drops the cached snapshot so the next check re-reads the ledger (used after writes in tests). */
    void invalidate(UUID tenantId, String subject, String meterCode) {
        snapshots.invalidate(tenantId + "|" + subject + "|" + meterCode);
    }

    private static EntitlementDecision decide(Snapshot s, String subject, String meter, BigDecimal requested) {
        if (s.blocker() != null) {
            return new EntitlementDecision(
                    Decision.DENY,
                    s.blocker(),
                    subject,
                    meter,
                    requested,
                    s.used(),
                    s.limit(),
                    null,
                    s.creditRemaining(),
                    s.asOf());
        }
        if (s.hasCredits() && s.creditRemaining().signum() <= 0) {
            return new EntitlementDecision(
                    Decision.DENY,
                    Reason.CREDIT_EXHAUSTED,
                    subject,
                    meter,
                    requested,
                    s.used(),
                    s.limit(),
                    remaining(s),
                    s.creditRemaining(),
                    s.asOf());
        }
        if (s.limit() == null) {
            return new EntitlementDecision(
                    Decision.ALLOW,
                    Reason.NO_LIMIT,
                    subject,
                    meter,
                    requested,
                    s.used(),
                    null,
                    null,
                    s.creditRemaining(),
                    s.asOf());
        }
        BigDecimal remaining = remaining(s);
        if (s.used().add(requested).compareTo(s.limit()) > 0) {
            return new EntitlementDecision(
                    Decision.DENY,
                    Reason.LIMIT_EXCEEDED,
                    subject,
                    meter,
                    requested,
                    s.used(),
                    s.limit(),
                    remaining,
                    s.creditRemaining(),
                    s.asOf());
        }
        return new EntitlementDecision(
                Decision.ALLOW,
                Reason.WITHIN_LIMIT,
                subject,
                meter,
                requested,
                s.used(),
                s.limit(),
                remaining,
                s.creditRemaining(),
                s.asOf());
    }

    private static BigDecimal remaining(Snapshot s) {
        return s.limit() == null ? null : s.limit().subtract(s.used()).max(BigDecimal.ZERO);
    }

    private Snapshot snapshot(UUID tenantId, String subject, String meterCode) {
        Instant now = clock.instant();
        Optional<CustomerView> customer = catalog.customerByExternalId(tenantId, subject);
        if (customer.isEmpty()) {
            return blocked(Reason.UNKNOWN_SUBJECT, now);
        }
        Optional<SubscriptionView> subscription =
                catalog.activeSubscriptionAt(customer.get().id(), now);
        if (subscription.isEmpty()) {
            return blocked(Reason.NO_ACTIVE_SUBSCRIPTION, now);
        }
        Optional<BillingPeriodView> period =
                periods.openPeriod(subscription.get().id());
        if (period.isEmpty()) {
            return blocked(Reason.NO_OPEN_PERIOD, now);
        }
        PlanView plan = catalog.plan(subscription.get().planId()).orElseThrow();
        Optional<MeterView> meter = catalog.meterByCode(tenantId, meterCode);
        Optional<PlanView.ComponentView> component = meter.flatMap(m -> plan.components().stream()
                .filter(c -> c.meterId().equals(m.id()))
                .findFirst());
        if (component.isEmpty()) {
            return blocked(Reason.METER_NOT_IN_PLAN, now);
        }
        BigDecimal used = usage.usageForPeriod(period.get()).stream()
                .filter(u -> u.meterId().equals(meter.get().id()))
                .map(MeterUsage::quantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        boolean hasCredits = credits.hasGrants(customer.get().id(), plan.currency());
        BigDecimal creditRemaining = null;
        if (hasCredits) {
            Money balance = credits.balance(customer.get().id(), plan.currency(), now);
            Money estimate = billing.liveEstimate(period.get().id());
            creditRemaining = balance.minus(estimate).rounded().amount();
        }
        return new Snapshot(null, used, component.get().hardLimit(), creditRemaining, hasCredits, now);
    }

    private static Snapshot blocked(Reason reason, Instant now) {
        return new Snapshot(reason, BigDecimal.ZERO, null, null, false, now);
    }
}
