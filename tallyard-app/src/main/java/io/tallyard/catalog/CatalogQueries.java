package io.tallyard.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read API used by metering, billing and entitlements. Hot-path lookups are cached. */
public interface CatalogQueries {

    List<MeterView> metersForEventType(UUID tenantId, String eventType);

    List<MeterView> meters(UUID tenantId);

    Optional<MeterView> meter(UUID meterId);

    Optional<MeterView> meterByCode(UUID tenantId, String code);

    Optional<CustomerView> customerByExternalId(UUID tenantId, String externalId);

    Optional<CustomerView> customer(UUID customerId);

    List<CustomerView> customers(UUID tenantId);

    Optional<SubscriptionView> activeSubscriptionAt(UUID customerId, Instant at);

    Optional<SubscriptionView> subscription(UUID subscriptionId);

    List<SubscriptionView> subscriptions(UUID tenantId);

    Optional<PlanView> plan(UUID planId);

    List<PlanView> plans(UUID tenantId);
}
