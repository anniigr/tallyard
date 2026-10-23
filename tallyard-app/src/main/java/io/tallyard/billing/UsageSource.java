package io.tallyard.billing;

import io.tallyard.catalog.MeterView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SPI through which billing obtains quantities. Implemented by the metering module.
 *
 * <p>Two sources exist on purpose: hourly buckets are the fast path used when rating; the immutable
 * event log is the audit path used by replay. Each counted event records the period it was folded
 * into, so replay reads exactly the events that built the buckets — events that arrived after rating
 * carry no membership and are accounted for as adjustments instead.
 */
public interface UsageSource {

    record Window(UUID tenantId, UUID periodId, UUID customerId, String subject, Instant start, Instant end) {}

    /** Quantity per meter id from buckets of the period (UNIQUE_COUNT is computed from the log by membership). */
    Map<UUID, BigDecimal> quantitiesFromBuckets(Window window, List<MeterView> meters);

    /** Quantity per meter id recomputed from the immutable event log by period membership. */
    Map<UUID, BigDecimal> quantitiesFromEventLog(Window window, List<MeterView> meters);

    /** Late events applied to this period as adjustments. */
    List<AdjustmentView> adjustments(UUID periodId);

    record AdjustmentView(
            UUID id, UUID meterId, UUID originalPeriodId, String eventId, BigDecimal value, Instant occurredAt) {}
}
