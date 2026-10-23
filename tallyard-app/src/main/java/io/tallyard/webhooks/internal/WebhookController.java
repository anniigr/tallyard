package io.tallyard.webhooks.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.shared.tenant.TenantContext;
import io.tallyard.webhooks.WebhookRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks")
@Tag(name = "Webhooks", description = "Endpoint registration and delivery inspection")
class WebhookController {

    private final WebhookService webhooks;

    WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    record RegisterRequest(@NotBlank String url, @NotEmpty Set<String> eventTypes) {}

    record EndpointResponse(UUID id, String url, Set<String> eventTypes, boolean active, Instant createdAt) {}

    record DeliveryResponse(
            UUID id,
            UUID endpointId,
            UUID eventId,
            String eventType,
            String state,
            int attempts,
            Instant nextAttemptAt,
            Integer lastStatus,
            String lastError,
            Instant createdAt,
            Instant deliveredAt) {
        static DeliveryResponse from(WebhookDelivery d) {
            return new DeliveryResponse(
                    d.getId(),
                    d.getEndpointId(),
                    d.getOutboxEventId(),
                    d.getEventType(),
                    d.getState().name(),
                    d.getAttempts(),
                    d.getNextAttemptAt(),
                    d.getLastStatus(),
                    d.getLastError(),
                    d.getCreatedAt(),
                    d.getDeliveredAt());
        }
    }

    @Operation(summary = "Register a webhook endpoint (secret returned once)")
    @PostMapping
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    WebhookRegistry.Registered register(@Valid @RequestBody RegisterRequest r) {
        return webhooks.register(TenantContext.tenantId(), r.url(), r.eventTypes());
    }

    @Operation(summary = "List endpoints")
    @GetMapping
    @PreAuthorize("@scopes.has('READ')")
    List<EndpointResponse> list() {
        return webhooks.list(TenantContext.tenantId()).stream()
                .map(e -> new EndpointResponse(e.getId(), e.getUrl(), e.eventTypeSet(), e.isActive(), e.getCreatedAt()))
                .toList();
    }

    @Operation(summary = "Deactivate an endpoint")
    @DeleteMapping("/{id}")
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable UUID id) {
        webhooks.deactivate(TenantContext.tenantId(), id);
    }

    @Operation(summary = "Recent deliveries with state, attempts and last error")
    @GetMapping("/deliveries")
    @PreAuthorize("@scopes.has('READ')")
    List<DeliveryResponse> deliveries(@RequestParam(defaultValue = "100") int limit) {
        return webhooks.recentDeliveries(TenantContext.tenantId(), Math.min(Math.max(limit, 1), 500)).stream()
                .map(DeliveryResponse::from)
                .toList();
    }

    @Operation(summary = "Re-queue a dead or pending delivery for immediate retry")
    @PostMapping("/deliveries/{id}/retry")
    @PreAuthorize("@scopes.has('ADMIN')")
    DeliveryResponse retry(@PathVariable UUID id) {
        return DeliveryResponse.from(webhooks.retry(TenantContext.tenantId(), id));
    }
}
