package io.tallyard.metering;

import io.tallyard.billing.BillingPeriodView;
import java.util.List;
import java.util.UUID;

/** Read API for dashboards and entitlements. */
public interface UsageQueries {

    List<MeterUsage> usageForPeriod(BillingPeriodView period);

    List<RejectedEventView> recentRejections(UUID tenantId, int limit);

    MeteringStats stats(UUID tenantId);
}
