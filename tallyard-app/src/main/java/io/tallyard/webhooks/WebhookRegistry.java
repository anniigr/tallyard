package io.tallyard.webhooks;

import java.util.Set;
import java.util.UUID;

public interface WebhookRegistry {

    /** Registers an endpoint; the returned secret is shown once and stored encrypted. */
    Registered register(UUID tenantId, String url, Set<String> eventTypes);

    record Registered(UUID id, String url, String secret, Set<String> eventTypes) {}
}
