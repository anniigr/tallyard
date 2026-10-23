package io.tallyard.webhooks.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "webhook_endpoint")
class WebhookEndpoint {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String url;

    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @Column(name = "event_types", nullable = false, columnDefinition = "text[]")
    private String[] eventTypes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebhookEndpoint() {}

    WebhookEndpoint(
            UUID id, UUID tenantId, String url, String secretEncrypted, Set<String> eventTypes, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.url = url;
        this.secretEncrypted = secretEncrypted;
        this.eventTypes = eventTypes.stream().sorted().toArray(String[]::new);
        this.active = true;
        this.createdAt = createdAt;
    }

    boolean accepts(String eventType) {
        for (String t : eventTypes) {
            if (t.equals("*") || t.equals(eventType)) {
                return true;
            }
        }
        return false;
    }

    void deactivate() {
        active = false;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getUrl() {
        return url;
    }

    String getSecretEncrypted() {
        return secretEncrypted;
    }

    Set<String> eventTypeSet() {
        return Arrays.stream(eventTypes).collect(Collectors.toUnmodifiableSet());
    }

    boolean isActive() {
        return active;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
