/**
 * Accepts usage events over HTTP, validates them, and publishes them durably to Kafka. Owns the
 * {@code usage.events.v1} message contract ({@link io.tallyard.ingestion.UsageEventMessage}). Does
 * not touch the database: Kafka is the durable log, and ingestion fails fast (503) when it cannot
 * write to it, pushing back on clients that retry with the same event ids.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Ingestion")
package io.tallyard.ingestion;
