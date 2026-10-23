package io.tallyard.outbox;

import java.time.Instant;
import java.util.UUID;

/** Wire envelope on {@code billing.events.v1}. {@code payload} is the event-type specific JSON. */
public record OutboxMessage(
        UUID eventId,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Instant occurredAt,
        String payload) {

    public static final String HEADER_EVENT_TYPE = "ty-event-type";
    public static final String HEADER_EVENT_ID = "ty-event-id";
}
