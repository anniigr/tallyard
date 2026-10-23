package io.tallyard.shared.tenant;

import io.tallyard.shared.error.ForbiddenException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the current tenant from the Spring Security context. Tenant identity always comes from
 * the authenticated API key, never from request parameters or bodies.
 */
public final class TenantContext {

    private TenantContext() {}

    public static Optional<TenantPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static TenantPrincipal require() {
        return current().orElseThrow(() -> new ForbiddenException("No authenticated tenant"));
    }

    public static UUID tenantId() {
        return require().tenantId();
    }
}
