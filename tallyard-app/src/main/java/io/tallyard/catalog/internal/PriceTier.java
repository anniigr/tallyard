package io.tallyard.catalog.internal;

import io.tallyard.catalog.PlanView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "price_tier")
class PriceTier {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_id")
    private PlanComponent component;

    @Column(nullable = false)
    private int position;

    @Column(name = "up_to", precision = 19, scale = 6)
    private BigDecimal upTo;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 10)
    private BigDecimal unitPrice;

    @Column(name = "flat_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal flatAmount;

    protected PriceTier() {}

    PriceTier(UUID id, int position, BigDecimal upTo, BigDecimal unitPrice, BigDecimal flatAmount) {
        this.id = id;
        this.position = position;
        this.upTo = upTo;
        this.unitPrice = unitPrice;
        this.flatAmount = flatAmount;
    }

    void attach(PlanComponent component) {
        this.component = component;
    }

    int getPosition() {
        return position;
    }

    PlanView.TierView toView() {
        return new PlanView.TierView(upTo, unitPrice, flatAmount);
    }
}
