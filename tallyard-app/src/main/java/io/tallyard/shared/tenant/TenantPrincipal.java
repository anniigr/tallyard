package io.tallyard.shared.tenant;

import java.util.Set;
import java.util.UUID;

/** The authenticated caller: a tenant acting through one API key. */
public record TenantPrincipal(UUID tenantId, UUID apiKeyId, String keyPrefix, Set<ApiScope> scopes) {

    public boolean has(ApiScope required) {
        return scopes.stream().anyMatch(s -> s.implies(required));
    }
}
