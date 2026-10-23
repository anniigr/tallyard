package io.tallyard.tenancy.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.error.ValidationException;
import io.tallyard.shared.id.Ids;
import io.tallyard.shared.tenant.ApiScope;
import io.tallyard.shared.tenant.TenantPrincipal;
import io.tallyard.tenancy.IssuedApiKey;
import io.tallyard.tenancy.TenantProvisioning;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ApiKeyService implements TenantProvisioning {

    private final TenantRepository tenants;
    private final ApiKeyRepository keys;
    private final Clock clock;

    /**
     * Authentication happens on every request, so successful lookups are cached briefly. Revocation
     * evicts locally; across nodes the TTL bounds how long a revoked key stays usable.
     */
    private final Cache<String, Optional<TenantPrincipal>> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    ApiKeyService(TenantRepository tenants, ApiKeyRepository keys, Clock clock) {
        this.tenants = tenants;
        this.keys = keys;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID createTenant(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Tenant name must not be blank");
        }
        Tenant tenant = new Tenant(Ids.newId(clock), name.trim(), clock.instant());
        tenants.save(tenant);
        return tenant.getId();
    }

    @Override
    @Transactional
    public IssuedApiKey issueKey(UUID tenantId, String name, Set<ApiScope> scopes) {
        return store(tenantId, name, scopes, ApiKeyCodec.generate());
    }

    @Override
    @Transactional
    public IssuedApiKey installKey(UUID tenantId, String name, Set<ApiScope> scopes, String rawKey) {
        if (!ApiKeyCodec.looksLikeKey(rawKey)) {
            throw new ValidationException(
                    "api_key.format", "Key must be 'ty_' followed by exactly 40 alphanumeric characters");
        }
        return store(tenantId, name, scopes, rawKey);
    }

    private IssuedApiKey store(UUID tenantId, String name, Set<ApiScope> scopes, String raw) {
        if (!tenants.existsById(tenantId)) {
            throw new NotFoundException("Tenant", tenantId);
        }
        if (scopes == null || scopes.isEmpty()) {
            throw new ValidationException("At least one scope is required");
        }
        ApiKey key = new ApiKey(
                Ids.newId(clock),
                tenantId,
                name == null || name.isBlank() ? "default" : name.trim(),
                ApiKeyCodec.publicPrefix(raw),
                ApiKeyCodec.hash(raw),
                scopes,
                clock.instant());
        keys.save(key);
        return new IssuedApiKey(key.getId(), tenantId, key.getPrefix(), raw, key.scopeSet());
    }

    @Transactional(readOnly = true)
    public Optional<TenantPrincipal> authenticate(String rawKey) {
        if (!ApiKeyCodec.looksLikeKey(rawKey)) {
            return Optional.empty();
        }
        String hash = ApiKeyCodec.hash(rawKey);
        return cache.get(
                hash,
                h -> keys.findByKeyHash(h)
                        .filter(ApiKey::isActive)
                        .map(k -> new TenantPrincipal(k.getTenantId(), k.getId(), k.getPrefix(), k.scopeSet())));
    }

    @Transactional(readOnly = true)
    List<ApiKey> list(UUID tenantId) {
        return keys.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    void revoke(UUID tenantId, UUID keyId) {
        ApiKey key =
                keys.findByIdAndTenantId(keyId, tenantId).orElseThrow(() -> new NotFoundException("API key", keyId));
        key.revoke(clock.instant());
        cache.invalidate(key.getKeyHash());
    }

    @Transactional(readOnly = true)
    Optional<String> tenantName(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::getName);
    }
}
