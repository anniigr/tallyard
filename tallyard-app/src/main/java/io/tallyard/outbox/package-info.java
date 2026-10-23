/**
 * Transactional outbox. Domain modules append events inside their own transaction; a poller
 * publishes them to Kafka ({@code billing.events.v1}) with at-least-once semantics and marks them
 * published. Consumers must deduplicate on the event id.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Outbox")
package io.tallyard.outbox;
