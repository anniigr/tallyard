package io.tallyard.ingestion.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/** One event as submitted by a client. Structural validation here; semantic validation in {@link EventValidator}. */
record IncomingEvent(
        @NotBlank @Size(max = 128) String eventId,

        @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_.]{0,63}")
        String eventType,

        @NotBlank @Size(max = 128) String subject,
        @NotNull Instant occurredAt,
        @Size(max = 50) Map<String, Object> properties) {}
