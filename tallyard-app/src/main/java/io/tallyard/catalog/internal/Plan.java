package io.tallyard.catalog.internal;

import io.tallyard.catalog.BillingInterval;
import io.tallyard.catalog.PlanView;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "plan")
class Plan {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_unit", nullable = false)
    private BillingInterval.Unit intervalUnit;

    @Column(name = "interval_length", nullable = false)
    private int intervalLength;

    @Column(name = "grace_seconds", nullable = false)
    private int graceSeconds;

    @Column(name = "minimum_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal minimumAmount;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Sets (not Lists) so Hibernate can join-fetch components and tiers in one query without the
    // MultipleBagFetchException; @OrderBy keeps the insertion-ordered LinkedHashSet sorted by position.
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private Set<PlanComponent> components = new LinkedHashSet<>();

    protected Plan() {}

    Plan(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            Currency currency,
            BillingInterval interval,
            int graceSeconds,
            BigDecimal minimumAmount,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.currency = currency.getCurrencyCode();
        this.intervalUnit = interval.unit();
        this.intervalLength = interval.length();
        this.graceSeconds = graceSeconds;
        this.minimumAmount = minimumAmount;
        this.createdAt = createdAt;
    }

    void addComponent(PlanComponent component) {
        component.attach(this);
        components.add(component);
    }

    void lock() {
        locked = true;
    }

    boolean isLocked() {
        return locked;
    }

    PlanView toView(Map<UUID, String> meterCodes) {
        return new PlanView(
                id,
                tenantId,
                code,
                name,
                Currency.getInstance(currency),
                new BillingInterval(intervalUnit, intervalLength),
                graceSeconds,
                minimumAmount,
                getComponents().stream()
                        .map(c -> c.toView(meterCodes.getOrDefault(c.getMeterId(), "?")))
                        .toList());
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getCurrency() {
        return currency;
    }

    BillingInterval interval() {
        return new BillingInterval(intervalUnit, intervalLength);
    }

    List<PlanComponent> getComponents() {
        return components.stream()
                .sorted(java.util.Comparator.comparingInt(PlanComponent::getPosition))
                .toList();
    }
}
