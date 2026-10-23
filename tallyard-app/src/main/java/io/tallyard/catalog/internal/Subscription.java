package io.tallyard.catalog.internal;

import io.tallyard.catalog.SubscriptionView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription")
class Subscription {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionView.Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Subscription() {}

    Subscription(UUID id, UUID tenantId, UUID customerId, UUID planId, Instant startsAt, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.planId = planId;
        this.startsAt = startsAt;
        this.status = SubscriptionView.Status.ACTIVE;
        this.createdAt = createdAt;
    }

    void end(Instant at) {
        this.endsAt = at;
        this.status = SubscriptionView.Status.ENDED;
    }

    SubscriptionView toView() {
        return new SubscriptionView(id, tenantId, customerId, planId, startsAt, endsAt, status);
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getCustomerId() {
        return customerId;
    }

    UUID getPlanId() {
        return planId;
    }
}
