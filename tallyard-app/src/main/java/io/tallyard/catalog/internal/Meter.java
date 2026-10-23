package io.tallyard.catalog.internal;

import io.tallyard.catalog.Aggregation;
import io.tallyard.catalog.MeterView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter")
class Meter {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Aggregation aggregation;

    @Column(name = "value_property")
    private String valueProperty;

    @Column(nullable = false)
    private String unit;

    @Column(name = "acceptance_window_seconds", nullable = false)
    private long acceptanceWindowSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Meter() {}

    Meter(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            String eventType,
            Aggregation aggregation,
            String valueProperty,
            String unit,
            Duration acceptanceWindow,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.eventType = eventType;
        this.aggregation = aggregation;
        this.valueProperty = valueProperty;
        this.unit = unit;
        this.acceptanceWindowSeconds = acceptanceWindow.toSeconds();
        this.createdAt = createdAt;
    }

    MeterView toView() {
        return new MeterView(
                id,
                tenantId,
                code,
                name,
                eventType,
                aggregation,
                valueProperty,
                unit,
                Duration.ofSeconds(acceptanceWindowSeconds));
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getCode() {
        return code;
    }
}
