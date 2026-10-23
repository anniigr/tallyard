package io.tallyard.catalog;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record PlanView(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        Currency currency,
        BillingInterval interval,
        int graceSeconds,
        BigDecimal minimumAmount,
        List<ComponentView> components) {

    public record ComponentView(
            UUID id,
            UUID meterId,
            String meterCode,
            int position,
            PricingModel pricingModel,
            BigDecimal includedUnits,
            BigDecimal packageSize,
            BigDecimal hardLimit,
            List<TierView> tiers) {}

    /** {@code upTo == null} means unbounded. Prices are per unit; {@code flatAmount} is charged once when the tier is entered. */
    public record TierView(BigDecimal upTo, BigDecimal unitPrice, BigDecimal flatAmount) {}
}
