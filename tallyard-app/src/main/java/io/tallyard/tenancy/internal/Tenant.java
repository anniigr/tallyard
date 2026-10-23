package io.tallyard.tenancy.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Tenant() {}

    Tenant(UUID id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
