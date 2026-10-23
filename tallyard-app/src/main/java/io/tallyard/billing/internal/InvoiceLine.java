package io.tallyard.billing.internal;

import io.tallyard.billing.InvoiceView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoice_line")
class InvoiceLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private InvoiceDraft invoice;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String kind;

    @Column(name = "component_id")
    private UUID componentId;

    @Column(name = "meter_id")
    private UUID meterId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String breakdown;

    protected InvoiceLine() {}

    InvoiceLine(
            UUID id,
            int position,
            String kind,
            UUID componentId,
            UUID meterId,
            String description,
            BigDecimal quantity,
            BigDecimal amount,
            String breakdown) {
        this.id = id;
        this.position = position;
        this.kind = kind;
        this.componentId = componentId;
        this.meterId = meterId;
        this.description = description;
        this.quantity = quantity;
        this.amount = amount;
        this.breakdown = breakdown;
    }

    void attach(InvoiceDraft invoice) {
        this.invoice = invoice;
    }

    InvoiceView.LineView toView() {
        return new InvoiceView.LineView(position, kind, componentId, meterId, description, quantity, amount, breakdown);
    }

    String getKind() {
        return kind;
    }

    UUID getMeterId() {
        return meterId;
    }

    BigDecimal getQuantity() {
        return quantity;
    }

    BigDecimal getAmount() {
        return amount;
    }

    UUID getComponentId() {
        return componentId;
    }
}
