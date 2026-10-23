package io.tallyard.billing.internal;

import io.tallyard.billing.BillingPeriodView;
import io.tallyard.billing.PeriodState;
import io.tallyard.shared.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for the period lifecycle. Transitions are methods that enforce the state machine;
 * {@code @Version} turns two concurrent transitions into one success and one
 * {@code OptimisticLockingFailureException} rather than two invoices.
 */
@Entity
@Table(name = "billing_period")
class BillingPeriod {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PeriodState state;

    @Column(name = "grace_until", nullable = false)
    private Instant graceUntil;

    @Column(name = "rated_at")
    private Instant ratedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BillingPeriod() {}

    BillingPeriod(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            UUID customerId,
            Instant startsAt,
            Instant endsAt,
            Instant graceUntil,
            Instant createdAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Period end must be after start");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.subscriptionId = subscriptionId;
        this.customerId = customerId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.graceUntil = graceUntil;
        this.state = PeriodState.OPEN;
        this.createdAt = createdAt;
    }

    void startClosing() {
        transition(PeriodState.CLOSING);
    }

    void markRated(Instant now) {
        transition(PeriodState.RATED);
        this.ratedAt = now;
    }

    void finalizePeriod(Instant now) {
        transition(PeriodState.FINALIZED);
        this.finalizedAt = now;
    }

    private void transition(PeriodState next) {
        if (!state.canTransitionTo(next)) {
            throw new ConflictException(
                    "period.illegal_transition", "Billing period " + id + " cannot move from " + state + " to " + next);
        }
        this.state = next;
    }

    boolean isDueForClosing(Instant now) {
        return state == PeriodState.OPEN && !now.isBefore(endsAt);
    }

    boolean isDueForRating(Instant now) {
        return state == PeriodState.CLOSING && !now.isBefore(graceUntil);
    }

    BillingPeriodView toView() {
        return new BillingPeriodView(
                id, tenantId, subscriptionId, customerId, startsAt, endsAt, state, graceUntil, ratedAt, finalizedAt);
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getSubscriptionId() {
        return subscriptionId;
    }

    UUID getCustomerId() {
        return customerId;
    }

    Instant getStartsAt() {
        return startsAt;
    }

    Instant getEndsAt() {
        return endsAt;
    }

    PeriodState getState() {
        return state;
    }

    Instant getGraceUntil() {
        return graceUntil;
    }

    Instant getRatedAt() {
        return ratedAt;
    }

    long getVersion() {
        return version;
    }
}
