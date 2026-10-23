package io.tallyard.catalog;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionView(
        UUID id, UUID tenantId, UUID customerId, UUID planId, Instant startsAt, Instant endsAt, Status status) {

    public enum Status {
        ACTIVE,
        ENDED
    }

    public boolean coversInstant(Instant at) {
        return !at.isBefore(startsAt) && (endsAt == null || at.isBefore(endsAt));
    }
}
