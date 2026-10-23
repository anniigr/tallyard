package io.tallyard.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceView(
        UUID id,
        UUID periodId,
        UUID customerId,
        String currency,
        BigDecimal subtotal,
        BigDecimal creditsApplied,
        BigDecimal total,
        String reproducibilityHash,
        Instant createdAt,
        List<LineView> lines) {

    public record LineView(
            int position,
            String kind,
            UUID componentId,
            UUID meterId,
            String description,
            BigDecimal quantity,
            BigDecimal amount,
            String breakdownJson) {}
}
