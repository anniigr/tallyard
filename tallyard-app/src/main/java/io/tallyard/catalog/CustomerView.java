package io.tallyard.catalog;

import java.util.UUID;

public record CustomerView(UUID id, UUID tenantId, String externalId, String name) {}
