package io.tallyard.tenancy;

import io.tallyard.shared.tenant.ApiScope;
import java.util.Set;
import java.util.UUID;

/** Public API for creating tenants and keys; used by the demo seeder and the admin endpoints. */
public interface TenantProvisioning {

    UUID createTenant(String name);

    IssuedApiKey issueKey(UUID tenantId, String name, Set<ApiScope> scopes);

    /**
     * Installs a caller-supplied key (format {@code ty_} + 40 alphanumerics). Only for local demos
     * and tests where tooling needs a known credential; production keys are always generated.
     */
    IssuedApiKey installKey(UUID tenantId, String name, Set<ApiScope> scopes, String rawKey);
}
