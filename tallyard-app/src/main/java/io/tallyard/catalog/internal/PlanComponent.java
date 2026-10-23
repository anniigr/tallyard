package io.tallyard.catalog.internal;

import io.tallyard.catalog.PlanView;
import io.tallyard.catalog.PricingModel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "plan_component")
class PlanComponent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(name = "meter_id", nullable = false)
    private UUID meterId;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false)
    private PricingModel pricingModel;

    @Column(name = "included_units", nullable = false, precision = 19, scale = 6)
    private BigDecimal includedUnits;

    @Column(name = "package_size", precision = 19, scale = 6)
    private BigDecimal packageSize;

    @Column(name = "hard_limit", precision = 19, scale = 6)
    private BigDecimal hardLimit;

    @OneToMany(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private Set<PriceTier> tiers = new LinkedHashSet<>();

    protected PlanComponent() {}

    PlanComponent(
            UUID id,
            UUID meterId,
            int position,
            PricingModel pricingModel,
            BigDecimal includedUnits,
            BigDecimal packageSize,
            BigDecimal hardLimit) {
        this.id = id;
        this.meterId = meterId;
        this.position = position;
        this.pricingModel = pricingModel;
        this.includedUnits = includedUnits;
        this.packageSize = packageSize;
        this.hardLimit = hardLimit;
    }

    void attach(Plan plan) {
        this.plan = plan;
    }

    void addTier(PriceTier tier) {
        tier.attach(this);
        tiers.add(tier);
    }

    PlanView.ComponentView toView(String meterCode) {
        return new PlanView.ComponentView(
                id,
                meterId,
                meterCode,
                position,
                pricingModel,
                includedUnits,
                packageSize,
                hardLimit,
                tiers.stream()
                        .sorted(java.util.Comparator.comparingInt(PriceTier::getPosition))
                        .map(PriceTier::toView)
                        .toList());
    }

    UUID getMeterId() {
        return meterId;
    }

    int getPosition() {
        return position;
    }
}
