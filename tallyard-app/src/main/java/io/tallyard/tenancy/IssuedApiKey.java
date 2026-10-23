package io.tallyard.tenancy;

import io.tallyard.shared.tenant.ApiScope;
import java.util.Set;
import java.util.UUID;

/** A freshly issued key. {@code secret} is shown exactly once and never stored. */
public record IssuedApiKey(UUID id, UUID tenantId, String prefix, String secret, Set<ApiScope> scopes) {}
