package io.tallyard.credits.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_grant")
class CreditGrant {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_granted", nullable = false, precision = 19, scale = 6)
    private BigDecimal amountGranted;

    @Column(name = "amount_consumed", nullable = false, precision = 19, scale = 6)
    private BigDecimal amountConsumed;

    @Column(nullable = false)
    private int priority;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreditGrant() {}

    CreditGrant(
            UUID id,
            UUID tenantId,
            UUID customerId,
            String currency,
            BigDecimal amountGranted,
            int priority,
            Instant expiresAt,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.currency = currency;
        this.amountGranted = amountGranted;
        this.amountConsumed = BigDecimal.ZERO;
        this.priority = priority;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    BigDecimal remaining() {
        return amountGranted.subtract(amountConsumed);
    }

    /** Consumes up to {@code wanted}; returns what was taken. Never overdraws. */
    BigDecimal consumeUpTo(BigDecimal wanted) {
        BigDecimal take = wanted.min(remaining());
        if (take.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        amountConsumed = amountConsumed.add(take);
        return take;
    }

    boolean isUsableAt(Instant at) {
        return (expiresAt == null || expiresAt.isAfter(at)) && remaining().signum() > 0;
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

    String getCurrency() {
        return currency;
    }

    BigDecimal getAmountGranted() {
        return amountGranted;
    }

    BigDecimal getAmountConsumed() {
        return amountConsumed;
    }

    int getPriority() {
        return priority;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
