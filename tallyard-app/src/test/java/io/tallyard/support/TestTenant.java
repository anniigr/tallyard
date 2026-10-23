package io.tallyard.support;

import io.tallyard.catalog.Aggregation;
import io.tallyard.catalog.BillingInterval;
import io.tallyard.catalog.CatalogCommands;
import io.tallyard.catalog.CatalogCommands.NewComponent;
import io.tallyard.catalog.CatalogCommands.NewMeter;
import io.tallyard.catalog.CatalogCommands.NewPlan;
import io.tallyard.catalog.CatalogCommands.NewTier;
import io.tallyard.catalog.PricingModel;
import io.tallyard.catalog.SubscriptionView;
import io.tallyard.shared.tenant.ApiScope;
import io.tallyard.tenancy.IssuedApiKey;
import io.tallyard.tenancy.TenantProvisioning;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Builds an isolated tenant with a standard catalog for integration tests. */
@Component
public class TestTenant {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final TenantProvisioning tenants;
    private final CatalogCommands catalog;

    public TestTenant(TenantProvisioning tenants, CatalogCommands catalog) {
        this.tenants = tenants;
        this.catalog = catalog;
    }

    public record Fixture(UUID tenantId, String adminKey, String ingestKey, String readKey) {}

    public Fixture create() {
        UUID tenantId = tenants.createTenant("test-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID());
        IssuedApiKey admin = tenants.issueKey(tenantId, "admin", Set.of(ApiScope.ADMIN));
        IssuedApiKey ingest = tenants.issueKey(tenantId, "ingest", Set.of(ApiScope.INGEST));
        IssuedApiKey read = tenants.issueKey(tenantId, "read", Set.of(ApiScope.READ));
        return new Fixture(tenantId, admin.secret(), ingest.secret(), read.secret());
    }

    /** Meters: api_calls (COUNT of request), tokens (SUM request.tokens), peak_gb (MAX storage.gb), devices (UNIQUE_COUNT heartbeat.device_id). */
    public void standardMeters(UUID tenantId) {
        Duration window = Duration.ofDays(35);
        catalog.createMeter(
                tenantId, new NewMeter("api_calls", "API calls", "request", Aggregation.COUNT, null, "call", window));
        catalog.createMeter(
                tenantId, new NewMeter("tokens", "Tokens", "request", Aggregation.SUM, "tokens", "token", window));
        catalog.createMeter(
                tenantId, new NewMeter("peak_gb", "Peak GB", "storage", Aggregation.MAX, "gb", "GB", window));
        catalog.createMeter(
                tenantId,
                new NewMeter(
                        "devices", "Devices", "heartbeat", Aggregation.UNIQUE_COUNT, "device_id", "device", window));
    }

    /** Graduated api_calls (100 included; 0–1000 @0.01; above @0.005), per-unit tokens @0.0001, package devices (5 per $2), min commitment $1. */
    public void standardPlan(UUID tenantId, String code, BillingInterval interval, int graceSeconds) {
        catalog.createPlan(
                tenantId,
                new NewPlan(
                        code,
                        "Standard",
                        Currency.getInstance("USD"),
                        interval,
                        graceSeconds,
                        new BigDecimal("1.00"),
                        List.of(
                                new NewComponent(
                                        "api_calls",
                                        PricingModel.GRADUATED,
                                        new BigDecimal("100"),
                                        null,
                                        new BigDecimal("10000"),
                                        List.of(
                                                new NewTier(
                                                        new BigDecimal("1000"),
                                                        new BigDecimal("0.01"),
                                                        BigDecimal.ZERO),
                                                new NewTier(null, new BigDecimal("0.005"), BigDecimal.ZERO))),
                                new NewComponent(
                                        "tokens",
                                        PricingModel.PER_UNIT,
                                        BigDecimal.ZERO,
                                        null,
                                        null,
                                        List.of(new NewTier(null, new BigDecimal("0.0001"), BigDecimal.ZERO))),
                                new NewComponent(
                                        "peak_gb",
                                        PricingModel.VOLUME,
                                        BigDecimal.ZERO,
                                        null,
                                        null,
                                        List.of(
                                                new NewTier(
                                                        new BigDecimal("10"), new BigDecimal("0.50"), BigDecimal.ZERO),
                                                new NewTier(null, new BigDecimal("0.25"), BigDecimal.ZERO))),
                                new NewComponent(
                                        "devices",
                                        PricingModel.PACKAGE,
                                        BigDecimal.ZERO,
                                        new BigDecimal("5"),
                                        null,
                                        List.of(new NewTier(null, BigDecimal.ZERO, new BigDecimal("2.00")))))));
    }

    public SubscriptionView customer(UUID tenantId, String externalId, String planCode, Instant startsAt) {
        catalog.createCustomer(tenantId, externalId, "Customer " + externalId);
        return catalog.subscribe(tenantId, externalId, planCode, startsAt);
    }
}
