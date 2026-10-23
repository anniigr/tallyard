package io.tallyard.dashboard.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.billing.BillingPeriodView;
import io.tallyard.billing.BillingPeriods;
import io.tallyard.billing.BillingQueries;
import io.tallyard.billing.InvoiceView;
import io.tallyard.billing.PeriodState;
import io.tallyard.catalog.CatalogQueries;
import io.tallyard.catalog.CustomerView;
import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.SubscriptionView;
import io.tallyard.credits.CreditLedger;
import io.tallyard.metering.MeterUsage;
import io.tallyard.metering.MeteringStats;
import io.tallyard.metering.RejectedEventView;
import io.tallyard.metering.UsageQueries;
import io.tallyard.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/dashboard")
@Tag(name = "Dashboard", description = "Aggregated read model for the operations UI")
class DashboardController {

    private final CatalogQueries catalog;
    private final BillingPeriods periods;
    private final BillingQueries billing;
    private final UsageQueries usage;
    private final CreditLedger credits;
    private final Clock clock;

    DashboardController(
            CatalogQueries catalog,
            BillingPeriods periods,
            BillingQueries billing,
            UsageQueries usage,
            CreditLedger credits,
            Clock clock) {
        this.catalog = catalog;
        this.periods = periods;
        this.billing = billing;
        this.usage = usage;
        this.credits = credits;
        this.clock = clock;
    }

    record CustomerRow(
            UUID customerId,
            String externalId,
            String name,
            String plan,
            String currency,
            UUID openPeriodId,
            Instant periodEndsAt,
            List<MeterUsage> usage,
            BigDecimal estimate,
            BigDecimal creditBalance) {}

    record Overview(
            Instant asOf,
            MeteringStats stats,
            List<CustomerRow> customers,
            List<BillingPeriodView> periods,
            List<InvoiceView> invoices,
            List<RejectedEventView> rejections) {}

    @Operation(summary = "Everything the operations UI shows, in one call")
    @GetMapping("/overview")
    @PreAuthorize("@scopes.has('READ')")
    Overview overview() {
        UUID tenantId = TenantContext.tenantId();
        Map<UUID, PlanView> plans = catalog.plans(tenantId).stream().collect(Collectors.toMap(PlanView::id, p -> p));
        List<SubscriptionView> subscriptions = catalog.subscriptions(tenantId);
        List<CustomerRow> rows = new ArrayList<>();
        Instant now = clock.instant();
        for (CustomerView c : catalog.customers(tenantId)) {
            Optional<SubscriptionView> sub = subscriptions.stream()
                    .filter(s -> s.customerId().equals(c.id()) && s.coversInstant(now))
                    .findFirst();
            if (sub.isEmpty()) {
                rows.add(new CustomerRow(
                        c.id(), c.externalId(), c.name(), null, null, null, null, List.of(), null, null));
                continue;
            }
            PlanView plan = plans.get(sub.get().planId());
            Optional<BillingPeriodView> open = periods.openPeriod(sub.get().id());
            List<MeterUsage> u = open.map(usage::usageForPeriod).orElse(List.of());
            BigDecimal estimate = open.map(
                            p -> billing.liveEstimate(p.id()).rounded().amount())
                    .orElse(null);
            BigDecimal balance = plan != null && credits.hasGrants(c.id(), plan.currency())
                    ? credits.balance(c.id(), plan.currency(), now).rounded().amount()
                    : null;
            rows.add(new CustomerRow(
                    c.id(),
                    c.externalId(),
                    c.name(),
                    plan == null ? null : plan.code(),
                    plan == null ? null : plan.currency().getCurrencyCode(),
                    open.map(BillingPeriodView::id).orElse(null),
                    open.map(BillingPeriodView::endsAt).orElse(null),
                    u,
                    estimate,
                    balance));
        }
        List<BillingPeriodView> allPeriods = periods.periodsForTenant(tenantId).stream()
                .sorted((a, b) -> {
                    int byState = Integer.compare(order(a.state()), order(b.state()));
                    return byState != 0 ? byState : b.startsAt().compareTo(a.startsAt());
                })
                .limit(60)
                .toList();
        return new Overview(
                now,
                usage.stats(tenantId),
                rows,
                allPeriods,
                billing.invoicesForTenant(tenantId, 25),
                usage.recentRejections(tenantId, 25));
    }

    private static int order(PeriodState s) {
        return switch (s) {
            // settled and closing periods carry results and actions; open ones are routine
            case CLOSING -> 0;
            case RATED -> 1;
            case FINALIZED -> 2;
            case OPEN -> 3;
        };
    }
}
