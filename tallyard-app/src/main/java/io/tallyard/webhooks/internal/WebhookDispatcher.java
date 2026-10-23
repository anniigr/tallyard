package io.tallyard.webhooks.internal;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.tallyard.shared.config.TallyardProperties;
import io.tallyard.shared.role.WorkerRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Claims due deliveries with {@code FOR UPDATE SKIP LOCKED}, POSTs them with an HMAC signature and
 * records the outcome. Failures back off exponentially ({@code initial-backoff × 2^attempts}) until
 * {@code max-attempts}, then the delivery is DEAD and visible to operators. A per-endpoint circuit
 * breaker stops hammering an endpoint that is down; while open, due deliveries are simply pushed back.
 */
@Component
@WorkerRole
class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final SecretEncryptor encryptor;
    private final TransactionTemplate tx;
    private final RestClient http;
    private final Clock clock;
    private final TallyardProperties.Webhooks config;
    private final CircuitBreakerRegistry breakers;
    /** Short-lived so URL/secret changes and deactivation are picked up without a restart. */
    private final com.github.benmanes.caffeine.cache.Cache<UUID, WebhookEndpoint> endpointCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(Duration.ofSeconds(30))
                    .build();

    private final Counter delivered;
    private final Counter failed;
    private final Counter dead;
    private final AtomicLong pending = new AtomicLong();

    WebhookDispatcher(
            WebhookDeliveryRepository deliveries,
            WebhookEndpointRepository endpoints,
            SecretEncryptor encryptor,
            TransactionTemplate tx,
            RestClient.Builder restClientBuilder,
            Clock clock,
            TallyardProperties props,
            MeterRegistry registry) {
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.encryptor = encryptor;
        this.tx = tx;
        this.clock = clock;
        this.config = props.webhooks();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(config.requestTimeout());
        this.http = restClientBuilder.requestFactory(factory).build();
        this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
        this.delivered = Counter.builder("tallyard.webhooks.deliveries")
                .tag("outcome", "delivered")
                .register(registry);
        this.failed = Counter.builder("tallyard.webhooks.deliveries")
                .tag("outcome", "failed")
                .register(registry);
        this.dead = Counter.builder("tallyard.webhooks.deliveries")
                .tag("outcome", "dead")
                .register(registry);
        Gauge.builder("tallyard.webhooks.pending", pending, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${tallyard.webhooks.poll-interval}")
    public void dispatchDue() {
        int n;
        do {
            n = dispatchBatch();
        } while (n >= config.batchSize());
        pending.set(deliveries.countByState(WebhookDelivery.State.PENDING));
    }

    int dispatchBatch() {
        Integer n = tx.execute(status -> {
            List<WebhookDelivery> due = deliveries.lockDue(clock.instant(), Limit.of(config.batchSize()));
            for (WebhookDelivery d : due) {
                attempt(d);
            }
            return due.size();
        });
        return n == null ? 0 : n;
    }

    private void attempt(WebhookDelivery d) {
        Instant now = clock.instant();
        WebhookEndpoint endpoint = endpointCache.get(
                d.getEndpointId(), id -> endpoints.findById(id).orElse(null));
        if (endpoint == null || !endpoint.isActive()) {
            d.failed(null, "endpoint missing or inactive", now, now, 0);
            dead.increment();
            return;
        }
        CircuitBreaker breaker = breakers.circuitBreaker(endpoint.getId().toString());
        try {
            ResponseEntity<Void> response = breaker.executeSupplier(() -> send(endpoint, d, now));
            d.delivered(response.getStatusCode().value(), now);
            delivered.increment();
        } catch (CallNotPermittedException open) {
            // Breaker open: do not count as an attempt, just push the delivery back.
            d.requeue(now.plus(Duration.ofSeconds(15)));
        } catch (RestClientResponseException e) {
            fail(d, e.getStatusCode().value(), "HTTP " + e.getStatusCode().value(), now);
        } catch (ResourceAccessException e) {
            fail(d, null, "connection: " + e.getMessage(), now);
        } catch (RuntimeException e) {
            fail(d, null, e.toString(), now);
        }
    }

    private ResponseEntity<Void> send(WebhookEndpoint endpoint, WebhookDelivery d, Instant now) {
        String secret = encryptor.decrypt(endpoint.getSecretEncrypted());
        String body = d.getPayload();
        return http.post()
                .uri(endpoint.getUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebhookSigner.HEADER, WebhookSigner.sign(secret, now.getEpochSecond(), body))
                .header("X-Tallyard-Event", d.getEventType())
                .header("X-Tallyard-Delivery", d.getId().toString())
                .header("X-Tallyard-Attempt", Integer.toString(d.getAttempts() + 1))
                .header(HttpHeaders.USER_AGENT, "Tallyard-Webhooks/1.0")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void fail(WebhookDelivery d, Integer status, String error, Instant now) {
        Duration backoff = config.initialBackoff().multipliedBy(1L << Math.min(d.getAttempts(), 10));
        d.failed(status, error, now, now.plus(backoff), config.maxAttempts());
        if (d.getState() == WebhookDelivery.State.DEAD) {
            dead.increment();
            log.warn("Webhook delivery {} dead-lettered after {} attempts: {}", d.getId(), d.getAttempts(), error);
        } else {
            failed.increment();
        }
    }
}
