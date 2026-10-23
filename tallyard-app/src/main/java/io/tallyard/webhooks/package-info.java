/**
 * Delivers billing events to tenant HTTP endpoints. Consumes {@code billing.events.v1}, fans each
 * event out into one durable delivery per subscribed endpoint (idempotent on event id), and
 * dispatches with signed requests, exponential backoff, a per-endpoint circuit breaker and
 * dead-lettering after a bounded number of attempts.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Webhooks")
package io.tallyard.webhooks;
