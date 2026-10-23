package io.tallyard.tenancy.internal;

import io.tallyard.shared.tenant.ApiScope;
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
@Table(name = "api_key")
class ApiKey {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String prefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(nullable = false, columnDefinition = "text[]")
    private String[] scopes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {}

    ApiKey(
            UUID id,
            UUID tenantId,
            String name,
            String prefix,
            String keyHash,
            Set<ApiScope> scopes,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.prefix = prefix;
        this.keyHash = keyHash;
        this.scopes = scopes.stream().map(Enum::name).sorted().toArray(String[]::new);
        this.createdAt = createdAt;
    }

    void revoke(Instant when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }

    boolean isActive() {
        return revokedAt == null;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getName() {
        return name;
    }

    String getPrefix() {
        return prefix;
    }

    String getKeyHash() {
        return keyHash;
    }

    Set<ApiScope> scopeSet() {
        return Arrays.stream(scopes).map(ApiScope::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }
}
