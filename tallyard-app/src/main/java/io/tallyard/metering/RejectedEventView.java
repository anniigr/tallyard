package io.tallyard.metering;

import java.time.Instant;
import java.util.UUID;

public record RejectedEventView(
        UUID id,
        String eventId,
        String eventType,
        String subject,
        Instant occurredAt,
        RejectionReason reason,
        String detail,
        Instant createdAt) {}
