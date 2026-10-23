package io.tallyard.catalog;

import java.time.Duration;
import java.util.UUID;

public record MeterView(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String eventType,
        Aggregation aggregation,
        String valueProperty,
        String unit,
        Duration acceptanceWindow) {}
