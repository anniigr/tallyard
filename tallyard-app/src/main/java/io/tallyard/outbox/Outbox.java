package io.tallyard.outbox;

import java.util.UUID;

/** Append-only API; must be called inside the caller's transaction. */
public interface Outbox {

    /**
     * @param aggregateType e.g. {@code BillingPeriod}
     * @param aggregateId used as Kafka partition key so events of one aggregate stay ordered
     * @param eventType e.g. {@code period.rated}
     * @param payload serialised to JSON
     * @return the outbox event id (also the downstream deduplication key)
     */
    UUID append(UUID tenantId, String aggregateType, UUID aggregateId, String eventType, Object payload);
}
