package io.tallyard.ingestion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wire contract of the {@code usage.events.v1} topic. Serialised as JSON (see
 * {@code schemas/usage-event.v1.schema.json}); the schema version travels in the
 * {@value #HEADER_SCHEMA_VERSION} header so consumers can dispatch on it when v2 appears.
 *
 * <p>Partition key is {@code tenantId:subject}: all events of one customer land on one partition,
 * which gives per-customer ordering and makes bucket updates for a customer single-writer.
 */
public record UsageEventMessage(
        UUID tenantId,
        String eventId,
        String eventType,
        String subject,
        Instant occurredAt,
        Instant ingestedAt,
        Map<String, Object> properties) {

    public static final int SCHEMA_VERSION = 1;
    public static final String HEADER_SCHEMA_VERSION = "ty-schema-version";
    public static final String HEADER_TENANT = "ty-tenant";

    public String partitionKey() {
        return tenantId + ":" + subject;
    }
}
