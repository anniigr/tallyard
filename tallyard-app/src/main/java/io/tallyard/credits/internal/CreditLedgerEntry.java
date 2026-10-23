package io.tallyard.credits.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_ledger_entry")
class CreditLedgerEntry {

    @Id
    private UUID id;

    @Column(name = "grant_id", nullable = false)
    private UUID grantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreditLedgerEntry() {}

    CreditLedgerEntry(UUID id, UUID grantId, UUID invoiceId, BigDecimal amount, Instant createdAt) {
        this.id = id;
        this.grantId = grantId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.createdAt = createdAt;
    }
}
