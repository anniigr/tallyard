package io.tallyard.metering.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.billing.BillingPeriodView;
import io.tallyard.billing.BillingPeriods;
import io.tallyard.billing.UsageSource;
import io.tallyard.metering.MeterUsage;
import io.tallyard.metering.MeteringStats;
import io.tallyard.metering.RejectedEventView;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Usage", description = "Live usage, adjustments and rejections")
class UsageController {

    private final UsageSourceAdapter usage;
    private final BillingPeriods periods;

    UsageController(UsageSourceAdapter usage, BillingPeriods periods) {
        this.usage = usage;
        this.periods = periods;
    }

    @Operation(summary = "Live usage per meter for a billing period")
    @GetMapping("/periods/{id}/usage")
    @PreAuthorize("@scopes.has('READ')")
    List<MeterUsage> usage(@PathVariable UUID id) {
        return usage.usageForPeriod(owned(id));
    }

    @Operation(summary = "Late events applied to a period as adjustments")
    @GetMapping("/periods/{id}/adjustments")
    @PreAuthorize("@scopes.has('READ')")
    List<UsageSource.AdjustmentView> adjustments(@PathVariable UUID id) {
        return usage.adjustments(owned(id).id());
    }

    @Operation(summary = "Most recent rejected events with reasons")
    @GetMapping("/rejections")
    @PreAuthorize("@scopes.has('READ')")
    List<RejectedEventView> rejections(@RequestParam(defaultValue = "100") int limit) {
        return usage.recentRejections(TenantContext.tenantId(), limit);
    }

    @Operation(summary = "Tenant-level metering counts")
    @GetMapping("/usage/stats")
    @PreAuthorize("@scopes.has('READ')")
    MeteringStats stats() {
        return usage.stats(TenantContext.tenantId());
    }

    private BillingPeriodView owned(UUID periodId) {
        return periods.period(periodId)
                .filter(p -> p.tenantId().equals(TenantContext.tenantId()))
                .orElseThrow(() -> new NotFoundException("Billing period", periodId));
    }
}
