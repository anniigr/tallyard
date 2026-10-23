package io.tallyard.tenancy.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.shared.error.ForbiddenException;
import io.tallyard.shared.tenant.ApiScope;
import io.tallyard.shared.tenant.TenantContext;
import io.tallyard.tenancy.IssuedApiKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Tenancy", description = "Tenant bootstrap and API key management")
class TenancyController {

    private final ApiKeyService apiKeys;
    private final String bootstrapToken;

    TenancyController(ApiKeyService apiKeys, @Value("${tallyard.bootstrap-token:}") String bootstrapToken) {
        this.apiKeys = apiKeys;
        this.bootstrapToken = bootstrapToken;
    }

    record CreateTenantRequest(@NotBlank @Size(max = 120) String name) {}

    record CreateTenantResponse(UUID tenantId, String name, IssuedKeyResponse adminKey) {}

    record IssueKeyRequest(
            @NotBlank @Size(max = 120) String name,
            @NotEmpty Set<ApiScope> scopes) {}

    record IssuedKeyResponse(UUID id, String prefix, String secret, Set<ApiScope> scopes) {
        static IssuedKeyResponse from(IssuedApiKey k) {
            return new IssuedKeyResponse(k.id(), k.prefix(), k.secret(), k.scopes());
        }
    }

    record ApiKeyResponse(
            UUID id, String name, String prefix, Set<ApiScope> scopes, Instant createdAt, Instant revokedAt) {}

    @Operation(
            summary = "Create a tenant and its first ADMIN key",
            description =
                    "Protected by the X-Bootstrap-Token header (TALLYARD_BOOTSTRAP_TOKEN). Disabled when the token is empty.")
    @PostMapping("/admin/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    CreateTenantResponse createTenant(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String token,
            @Valid @RequestBody CreateTenantRequest request) {
        if (bootstrapToken.isBlank() || token == null || !constantTimeEquals(bootstrapToken, token)) {
            throw new ForbiddenException("Invalid bootstrap token");
        }
        UUID tenantId = apiKeys.createTenant(request.name());
        IssuedApiKey key = apiKeys.issueKey(tenantId, "bootstrap-admin", Set.of(ApiScope.ADMIN, ApiScope.INGEST));
        return new CreateTenantResponse(tenantId, request.name(), IssuedKeyResponse.from(key));
    }

    @Operation(summary = "Issue a new API key for the current tenant")
    @PostMapping("/api-keys")
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    IssuedKeyResponse issueKey(@Valid @RequestBody IssueKeyRequest request) {
        return IssuedKeyResponse.from(apiKeys.issueKey(TenantContext.tenantId(), request.name(), request.scopes()));
    }

    @Operation(summary = "List API keys (prefixes only)")
    @GetMapping("/api-keys")
    @PreAuthorize("@scopes.has('ADMIN')")
    List<ApiKeyResponse> list() {
        return apiKeys.list(TenantContext.tenantId()).stream()
                .map(k -> new ApiKeyResponse(
                        k.getId(), k.getName(), k.getPrefix(), k.scopeSet(), k.getCreatedAt(), k.getRevokedAt()))
                .toList();
    }

    @Operation(summary = "Revoke an API key")
    @DeleteMapping("/api-keys/{id}")
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID id) {
        apiKeys.revoke(TenantContext.tenantId(), id);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
