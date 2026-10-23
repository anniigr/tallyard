package io.tallyard.webhooks.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstration receiver: an unauthenticated endpoint that stores the last 200 webhook requests in
 * memory and verifies their signatures against the (decrypted) secrets of every active endpoint that
 * points at this sink. It is what the demo tenant's webhook targets, so a reviewer can see signed
 * deliveries land without running a second service. Not part of the product surface.
 */
@RestController
@RequestMapping("/v1/webhook-sink")
@Tag(name = "Demo", description = "Local webhook receiver used by the demo")
public class WebhookSinkController {

    public record Received(
            Instant receivedAt,
            String eventType,
            String delivery,
            String attempt,
            boolean signatureValid,
            String body) {}

    private static final String SINK_PATH = "/v1/webhook-sink";

    private final Deque<Received> received = new ArrayDeque<>();
    private final AtomicReference<CachedSecrets> secrets = new AtomicReference<>();
    private final WebhookService webhooks;
    private final Clock clock;

    private record CachedSecrets(Instant loadedAt, List<String> values) {}

    WebhookSinkController(WebhookService webhooks, Clock clock) {
        this.webhooks = webhooks;
        this.clock = clock;
    }

    private List<String> sinkSecrets() {
        CachedSecrets cached = secrets.get();
        Instant now = clock.instant();
        if (cached != null && cached.loadedAt().plusSeconds(15).isAfter(now)) {
            return cached.values();
        }
        List<String> values = webhooks.secretsForEndpointsEndingWith(SINK_PATH);
        secrets.set(new CachedSecrets(now, values));
        return values;
    }

    @Operation(summary = "Receive a webhook (demo sink; unauthenticated)")
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = WebhookSigner.HEADER, required = false) String signature,
            @RequestHeader(value = "X-Tallyard-Event", required = false) String eventType,
            @RequestHeader(value = "X-Tallyard-Delivery", required = false) String delivery,
            @RequestHeader(value = "X-Tallyard-Attempt", required = false) String attempt,
            @RequestBody String body) {
        boolean valid = sinkSecrets().stream().anyMatch(s -> WebhookSigner.verify(s, signature, body));
        synchronized (received) {
            received.addFirst(new Received(clock.instant(), eventType, delivery, attempt, valid, body));
            while (received.size() > 200) {
                received.removeLast();
            }
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "Webhooks received by the demo sink")
    @GetMapping
    @PreAuthorize("@scopes.has('READ')")
    public List<Received> list() {
        synchronized (received) {
            return new ArrayList<>(received);
        }
    }
}
