package io.tallyard.billing;

import java.time.Instant;
import java.util.UUID;

public record BillingPeriodView(
        UUID id,
        UUID tenantId,
        UUID subscriptionId,
        UUID customerId,
        Instant startsAt,
        Instant endsAt,
        PeriodState state,
        Instant graceUntil,
        Instant ratedAt,
        Instant finalizedAt) {

    public boolean covers(Instant at) {
        return !at.isBefore(startsAt) && at.isBefore(endsAt);
    }
}
