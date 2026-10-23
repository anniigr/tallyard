/**
 * Tenants and API keys. Authenticates every request, resolves the tenant principal and exposes
 * key management endpoints. Other modules never see keys; they only read
 * {@link io.tallyard.shared.tenant.TenantContext}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Tenancy")
package io.tallyard.tenancy;
