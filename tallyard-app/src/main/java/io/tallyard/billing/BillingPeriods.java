package io.tallyard.billing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Period queries used by metering and entitlements. */
public interface BillingPeriods {

    /**
     * Finds the period covering {@code at} and takes a shared row lock on it for the duration of the
     * caller's transaction. Metering uses this so that a concurrent rating transaction (which takes
     * an exclusive lock) cannot settle the period while usage is being written into it: either the
     * usage commits first and is rated, or rating commits first and the usage is seen as late.
     */
    Optional<BillingPeriodView> lockPeriodCovering(UUID subscriptionId, Instant at);

    Optional<BillingPeriodView> openPeriod(UUID subscriptionId);

    /** The subscription's OPEN period (found by state, not by time: it may start in the future after an early rating), share-locked. */
    Optional<BillingPeriodView> lockOpenPeriod(UUID subscriptionId);

    Optional<BillingPeriodView> period(UUID periodId);

    List<BillingPeriodView> periodsForTenant(UUID tenantId);
}
