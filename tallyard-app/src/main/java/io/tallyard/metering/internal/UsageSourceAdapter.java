package io.tallyard.metering.internal;

import io.tallyard.billing.BillingPeriodView;
import io.tallyard.billing.UsageSource;
import io.tallyard.catalog.CatalogQueries;
import io.tallyard.catalog.CustomerView;
import io.tallyard.catalog.MeterView;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.SubscriptionView;
import io.tallyard.metering.MeterUsage;
import io.tallyard.metering.MeteringStats;
import io.tallyard.metering.RejectedEventView;
import io.tallyard.metering.UsageQueries;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.money.Decimals;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements billing's {@link UsageSource} SPI and the dashboard-facing {@link UsageQueries}. */
@Service
class UsageSourceAdapter implements UsageSource, UsageQueries {

    private final UsageEventStore store;
    private final CatalogQueries catalog;
    private final Clock clock;

    UsageSourceAdapter(UsageEventStore store, CatalogQueries catalog, Clock clock) {
        this.store = store;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> quantitiesFromBuckets(Window w, List<MeterView> meters) {
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        for (MeterView meter : meters) {
            if (!meter.aggregation().isNumeric()) {
                out.put(meter.id(), Decimals.quantity(store.quantityFromLog(w, meter)));
                continue;
            }
            UsageEventStore.BucketTotals t = store.bucketTotals(w.periodId(), meter.id(), w.customerId());
            BigDecimal q = switch (meter.aggregation()) {
                case SUM -> t.sum();
                case COUNT -> BigDecimal.valueOf(t.count());
                case MIN -> t.min();
                case MAX -> t.max();
                case LAST -> t.last();
                case UNIQUE_COUNT -> throw new IllegalStateException();
            };
            out.put(meter.id(), Decimals.quantity(q == null ? BigDecimal.ZERO : q));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> quantitiesFromEventLog(Window w, List<MeterView> meters) {
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        for (MeterView meter : meters) {
            out.put(meter.id(), Decimals.quantity(store.quantityFromLog(w, meter)));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdjustmentView> adjustments(UUID periodId) {
        return store.adjustments(periodId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterUsage> usageForPeriod(BillingPeriodView period) {
        SubscriptionView subscription = catalog.subscription(period.subscriptionId())
                .orElseThrow(() -> new NotFoundException("Subscription", period.subscriptionId()));
        CustomerView customer = catalog.customer(subscription.customerId())
                .orElseThrow(() -> new NotFoundException("Customer", subscription.customerId()));
        PlanView plan = catalog.plan(subscription.planId())
                .orElseThrow(() -> new NotFoundException("Plan", subscription.planId()));
        List<MeterView> meters = plan.components().stream()
                .map(c -> catalog.meter(c.meterId()).orElseThrow(() -> new NotFoundException("Meter", c.meterId())))
                .toList();
        Window w = new Window(
                period.tenantId(),
                period.id(),
                customer.id(),
                customer.externalId(),
                period.startsAt(),
                period.endsAt());
        Map<UUID, BigDecimal> quantities = quantitiesFromBuckets(w, meters);
        return meters.stream()
                .map(m -> new MeterUsage(
                        m.id(),
                        m.code(),
                        m.unit(),
                        m.aggregation(),
                        quantities.get(m.id()),
                        store.eventCountFromBuckets(period.id(), m.id(), customer.id())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RejectedEventView> recentRejections(UUID tenantId, int limit) {
        return store.recentRejections(tenantId, Math.min(Math.max(limit, 1), 500));
    }

    @Override
    @Transactional(readOnly = true)
    public MeteringStats stats(UUID tenantId) {
        long[] s = store.stats(tenantId);
        return new MeteringStats(s[0], s[1], s[2]);
    }
}
