package io.tallyard.catalog.internal;

import io.tallyard.catalog.CustomerView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
class Customer {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Customer() {}

    Customer(UUID id, UUID tenantId, String externalId, String name, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.externalId = externalId;
        this.name = name;
        this.createdAt = createdAt;
    }

    CustomerView toView() {
        return new CustomerView(id, tenantId, externalId, name);
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }
}
