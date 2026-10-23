/**
 * Turns the Kafka usage stream into counted usage. Owns the immutable event log, hourly buckets,
 * late-event adjustments and rejections. Guarantees effectively-once accounting: at-least-once
 * delivery from Kafka combined with an idempotent, transactional write ({@code usage_event}
 * unique key) means redelivered or duplicated events never change a total.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Metering")
package io.tallyard.metering;
