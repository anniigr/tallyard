package io.tallyard.tenancy.internal;

import io.tallyard.shared.tenant.ApiScope;
import io.tallyard.shared.tenant.TenantContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;

/**
 * Stateless API-key security. Public: the dashboard static assets, OpenAPI documents, the tenant
 * bootstrap endpoint (which enforces its own bootstrap token) and the actuator, which lives on a
 * separate management port that deployments keep off the public network. Everything under
 * {@code /v1} requires an authenticated key; scopes are enforced per endpoint with {@code @PreAuthorize}.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ApiKeyService apiKeys) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .headers(h -> h.frameOptions(f -> f.deny()))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/v1/webhook-sink")
                        .permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v1/admin/tenants",
                                "/actuator/**",
                                "/error")
                        .permitAll()
                        .requestMatchers("/v1/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> problem(
                                response, HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid API key is required"))
                        .accessDeniedHandler((request, response, e) -> problem(
                                response, HttpStatus.FORBIDDEN, "forbidden", "The API key lacks the required scope")))
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeys), BasicAuthenticationFilter.class);
        return http.build();
    }

    private static void problem(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter()
                .write("{\"type\":\"https://tallyard.io/problems/" + code + "\",\"title\":\""
                        + status.getReasonPhrase() + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail
                        + "\",\"code\":\"" + code + "\"}");
    }

    /** SpEL helper for {@code @PreAuthorize("@scopes.has('ADMIN')")}. */
    @Component("scopes")
    static class ScopeGuard {
        public boolean has(String scope) {
            return TenantContext.current()
                    .map(p -> p.has(ApiScope.valueOf(scope)))
                    .orElse(false);
        }
    }
}
