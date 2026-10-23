package io.tallyard.tenancy.internal;

import io.tallyard.shared.tenant.TenantPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code Authorization: Bearer ty_...} or {@code X-Api-Key} into a {@link TenantPrincipal}.
 * Unknown or revoked keys leave the request unauthenticated; the security configuration turns that
 * into 401 for protected paths. The tenant id is also placed in the logging MDC for correlation.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-Api-Key";
    static final String MDC_TENANT = "tenant";

    private final ApiKeyService apiKeys;

    ApiKeyAuthenticationFilter(ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = extract(request);
        if (raw != null) {
            apiKeys.authenticate(raw).ifPresent(principal -> {
                SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthentication(principal));
                MDC.put(MDC_TENANT, principal.tenantId().toString());
            });
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TENANT);
        }
    }

    private static String extract(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return null;
    }

    static final class ApiKeyAuthentication extends AbstractAuthenticationToken {

        private final TenantPrincipal principal;

        ApiKeyAuthentication(TenantPrincipal principal) {
            super(principal.scopes().stream()
                    .map(s -> new SimpleGrantedAuthority("SCOPE_" + s.name()))
                    .toList());
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }
}
