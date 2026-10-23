package io.tallyard.webhooks.internal;

import io.tallyard.outbox.OutboxMessage;
import io.tallyard.shared.error.NotFoundException;
import io.tallyard.shared.error.ValidationException;
import io.tallyard.shared.id.Ids;
import io.tallyard.webhooks.WebhookRegistry;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WebhookService implements WebhookRegistry {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final SecretEncryptor encryptor;
    private final Clock clock;

    WebhookService(
            WebhookEndpointRepository endpoints,
            WebhookDeliveryRepository deliveries,
            SecretEncryptor encryptor,
            Clock clock) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.encryptor = encryptor;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Registered register(UUID tenantId, String url, Set<String> eventTypes) {
        URI uri = URI.create(url);
        if (uri.getScheme() == null
                || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))
                || uri.getHost() == null) {
            throw new ValidationException("webhook.url", "Webhook URL must be an absolute http(s) URL");
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new ValidationException("webhook.event_types", "At least one event type (or '*') is required");
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        WebhookEndpoint endpoint = new WebhookEndpoint(
                Ids.newId(clock), tenantId, url, encryptor.encrypt(secret), eventTypes, clock.instant());
        endpoints.save(endpoint);
        return new Registered(endpoint.getId(), url, secret, endpoint.eventTypeSet());
    }

    @Transactional(readOnly = true)
    List<WebhookEndpoint> list(UUID tenantId) {
        return endpoints.findByTenantIdOrderByCreatedAt(tenantId);
    }

    @Transactional
    void deactivate(UUID tenantId, UUID endpointId) {
        endpoints
                .findByIdAndTenantId(endpointId, tenantId)
                .orElseThrow(() -> new NotFoundException("Webhook endpoint", endpointId))
                .deactivate();
    }

    /** Fan-out: one durable delivery per subscribed endpoint; the unique key makes redelivery of the Kafka record a no-op. */
    @Transactional
    int enqueue(OutboxMessage message) {
        int created = 0;
        for (WebhookEndpoint endpoint : endpoints.findByTenantIdAndActiveTrue(message.tenantId())) {
            if (!endpoint.accepts(message.eventType())) {
                continue;
            }
            try {
                deliveries.saveAndFlush(new WebhookDelivery(
                        Ids.newId(clock),
                        message.tenantId(),
                        endpoint.getId(),
                        message.eventId(),
                        message.eventType(),
                        envelope(message),
                        clock.instant()));
                created++;
            } catch (DataIntegrityViolationException duplicate) {
                // already enqueued by an earlier delivery of the same Kafka record
            }
        }
        return created;
    }

    private static String envelope(OutboxMessage m) {
        return "{\"id\":\"" + m.eventId() + "\",\"type\":\"" + m.eventType() + "\",\"occurredAt\":\"" + m.occurredAt()
                + "\",\"aggregateType\":\"" + m.aggregateType() + "\",\"aggregateId\":\"" + m.aggregateId()
                + "\",\"data\":"
                + m.payload() + "}";
    }

    /** Decrypted secrets of active endpoints whose URL ends with {@code suffix}; used by the demo sink to verify signatures. */
    @Transactional(readOnly = true)
    List<String> secretsForEndpointsEndingWith(String suffix) {
        if (!encryptor.isConfigured()) {
            return List.of();
        }
        return endpoints.findAll().stream()
                .filter(e -> e.isActive() && e.getUrl().endsWith(suffix))
                .map(e -> encryptor.decrypt(e.getSecretEncrypted()))
                .toList();
    }

    @Transactional(readOnly = true)
    List<WebhookDelivery> recentDeliveries(UUID tenantId, int limit) {
        return deliveries.findByTenantIdOrderByCreatedAtDesc(tenantId, Limit.of(limit));
    }

    @Transactional
    WebhookDelivery retry(UUID tenantId, UUID deliveryId) {
        WebhookDelivery d = deliveries
                .findByIdAndTenantId(deliveryId, tenantId)
                .orElseThrow(() -> new NotFoundException("Webhook delivery", deliveryId));
        d.requeue(clock.instant());
        return d;
    }
}
