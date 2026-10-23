package io.tallyard.webhooks.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_delivery")
class WebhookDelivery {

    enum State {
        PENDING,
        DELIVERED,
        DEAD
    }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "outbox_event_id", nullable = false)
    private UUID outboxEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_status")
    private Integer lastStatus;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected WebhookDelivery() {}

    WebhookDelivery(
            UUID id,
            UUID tenantId,
            UUID endpointId,
            UUID outboxEventId,
            String eventType,
            String payload,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.endpointId = endpointId;
        this.outboxEventId = outboxEventId;
        this.eventType = eventType;
        this.payload = payload;
        this.state = State.PENDING;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
    }

    void delivered(int status, Instant now) {
        this.attempts++;
        this.lastStatus = status;
        this.lastError = null;
        this.state = State.DELIVERED;
        this.deliveredAt = now;
    }

    void failed(Integer status, String error, Instant now, Instant retryAt, int maxAttempts) {
        this.attempts++;
        this.lastStatus = status;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            this.state = State.DEAD;
        } else {
            this.nextAttemptAt = retryAt;
        }
    }

    void requeue(Instant now) {
        this.state = State.PENDING;
        this.nextAttemptAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getEndpointId() {
        return endpointId;
    }

    UUID getOutboxEventId() {
        return outboxEventId;
    }

    String getEventType() {
        return eventType;
    }

    String getPayload() {
        return payload;
    }

    State getState() {
        return state;
    }

    int getAttempts() {
        return attempts;
    }

    Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    Integer getLastStatus() {
        return lastStatus;
    }

    String getLastError() {
        return lastError;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getDeliveredAt() {
        return deliveredAt;
    }
}
