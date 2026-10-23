package io.tallyard.catalog;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** Command API of the catalog; used by the REST layer and the demo seeder. */
public interface CatalogCommands {

    record NewMeter(
            String code,
            String name,
            String eventType,
            Aggregation aggregation,
            String valueProperty,
            String unit,
            Duration acceptanceWindow) {}

    record NewPlan(
            String code,
            String name,
            Currency currency,
            BillingInterval interval,
            int graceSeconds,
            BigDecimal minimumAmount,
            List<NewComponent> components) {}

    record NewComponent(
            String meterCode,
            PricingModel pricingModel,
            BigDecimal includedUnits,
            BigDecimal packageSize,
            BigDecimal hardLimit,
            List<NewTier> tiers) {}

    record NewTier(BigDecimal upTo, BigDecimal unitPrice, BigDecimal flatAmount) {}

    MeterView createMeter(UUID tenantId, NewMeter meter);

    CustomerView createCustomer(UUID tenantId, String externalId, String name);

    PlanView createPlan(UUID tenantId, NewPlan plan);

    SubscriptionView subscribe(UUID tenantId, String customerExternalId, String planCode, Instant startsAt);

    SubscriptionView endSubscription(UUID tenantId, UUID subscriptionId, Instant at);
}
