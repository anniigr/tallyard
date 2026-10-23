package io.tallyard.billing.internal;

import io.tallyard.billing.InvoiceView;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoice_draft")
class InvoiceDraft {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal subtotal;

    @Column(name = "credits_applied", nullable = false, precision = 19, scale = 6)
    private BigDecimal creditsApplied;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal total;

    @Column(name = "reproducibility_hash", nullable = false, length = 64)
    private String reproducibilityHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    protected InvoiceDraft() {}

    InvoiceDraft(
            UUID id,
            UUID tenantId,
            UUID periodId,
            UUID customerId,
            String currency,
            BigDecimal subtotal,
            BigDecimal creditsApplied,
            BigDecimal total,
            String reproducibilityHash,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.periodId = periodId;
        this.customerId = customerId;
        this.currency = currency;
        this.subtotal = subtotal;
        this.creditsApplied = creditsApplied;
        this.total = total;
        this.reproducibilityHash = reproducibilityHash;
        this.createdAt = createdAt;
    }

    void addLine(InvoiceLine line) {
        line.attach(this);
        lines.add(line);
    }

    InvoiceView toView() {
        return new InvoiceView(
                id,
                periodId,
                customerId,
                currency,
                subtotal,
                creditsApplied,
                total,
                reproducibilityHash,
                createdAt,
                lines.stream().map(InvoiceLine::toView).toList());
    }

    UUID getId() {
        return id;
    }

    String getReproducibilityHash() {
        return reproducibilityHash;
    }

    List<InvoiceLine> getLines() {
        return lines;
    }

    BigDecimal getSubtotal() {
        return subtotal;
    }

    BigDecimal getCreditsApplied() {
        return creditsApplied;
    }

    BigDecimal getTotal() {
        return total;
    }

    String getCurrency() {
        return currency;
    }
}
