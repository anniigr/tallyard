package io.tallyard.demo;

import io.tallyard.catalog.Aggregation;
import io.tallyard.catalog.BillingInterval;
import io.tallyard.catalog.CatalogCommands;
import io.tallyard.catalog.CatalogCommands.NewComponent;
import io.tallyard.catalog.CatalogCommands.NewMeter;
import io.tallyard.catalog.CatalogCommands.NewPlan;
import io.tallyard.catalog.CatalogCommands.NewTier;
import io.tallyard.catalog.PricingModel;
import io.tallyard.credits.CreditLedger;
import io.tallyard.shared.money.Money;
import io.tallyard.shared.tenant.ApiScope;
import io.tallyard.tenancy.IssuedApiKey;
import io.tallyard.tenancy.TenantProvisioning;
import io.tallyard.webhooks.WebhookRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(DemoProperties.class)
@ConditionalOnProperty(prefix = "tallyard.demo", name = "enabled", havingValue = "true")
class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    static final String TENANT_NAME = "Acme Demo";

    private final TenantProvisioning tenants;
    private final CatalogCommands catalog;
    private final CreditLedger credits;
    private final WebhookRegistry webhooks;
    private final JdbcTemplate jdbc;
    private final DemoProperties props;
    private final Clock clock;

    DemoSeeder(
            TenantProvisioning tenants,
            CatalogCommands catalog,
            CreditLedger credits,
            WebhookRegistry webhooks,
            JdbcTemplate jdbc,
            DemoProperties props,
            Clock clock) {
        this.tenants = tenants;
        this.catalog = catalog;
        this.credits = credits;
        this.webhooks = webhooks;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        Integer existing =
                jdbc.queryForObject("SELECT count(*) FROM tenant WHERE name = ?", Integer.class, TENANT_NAME);
        if (existing != null && existing > 0) {
            log.info("Demo tenant already present; skipping seed");
            return;
        }
        UUID tenantId = tenants.createTenant(TENANT_NAME);
        Set<ApiScope> scopes = Set.of(ApiScope.ADMIN, ApiScope.INGEST);
        IssuedApiKey key = props.adminKey() == null || props.adminKey().isBlank()
                ? tenants.issueKey(tenantId, "demo-admin", scopes)
                : tenants.installKey(
                        tenantId, "demo-admin", scopes, props.adminKey().trim());
        String secret = key.secret();

        Duration window = Duration.ofDays(35);
        catalog.createMeter(
                tenantId, new NewMeter("api_calls", "API calls", "request", Aggregation.COUNT, null, "call", window));
        catalog.createMeter(
                tenantId, new NewMeter("tokens", "LLM tokens", "request", Aggregation.SUM, "tokens", "token", window));
        catalog.createMeter(
                tenantId, new NewMeter("storage_gb", "Peak storage", "storage", Aggregation.MAX, "gb", "GB", window));
        catalog.createMeter(
                tenantId,
                new NewMeter(
                        "active_devices",
                        "Active devices",
                        "heartbeat",
                        Aggregation.UNIQUE_COUNT,
                        "device_id",
                        "device",
                        window));

        BillingInterval interval = new BillingInterval(BillingInterval.Unit.MINUTES, props.periodMinutes());
        catalog.createPlan(
                tenantId,
                new NewPlan(
                        "pro",
                        "Pro",
                        Currency.getInstance("USD"),
                        interval,
                        props.graceSeconds(),
                        new BigDecimal("20"),
                        List.of(
                                new NewComponent(
                                        "api_calls",
                                        PricingModel.GRADUATED,
                                        new BigDecimal("1000"),
                                        null,
                                        null,
                                        List.of(
                                                new NewTier(
                                                        new BigDecimal("10000"),
                                                        new BigDecimal("0.0010"),
                                                        BigDecimal.ZERO),
                                                new NewTier(
                                                        new BigDecimal("100000"),
                                                        new BigDecimal("0.0005"),
                                                        BigDecimal.ZERO),
                                                new NewTier(null, new BigDecimal("0.0002"), BigDecimal.ZERO))),
                                new NewComponent(
                                        "tokens",
                                        PricingModel.PER_UNIT,
                                        new BigDecimal("100000"),
                                        null,
                                        null,
                                        List.of(new NewTier(null, new BigDecimal("0.000002"), BigDecimal.ZERO))),
                                new NewComponent(
                                        "storage_gb",
                                        PricingModel.VOLUME,
                                        BigDecimal.ZERO,
                                        null,
                                        null,
                                        List.of(
                                                new NewTier(
                                                        new BigDecimal("100"), new BigDecimal("0.10"), BigDecimal.ZERO),
                                                new NewTier(null, new BigDecimal("0.07"), BigDecimal.ZERO))),
                                new NewComponent(
                                        "active_devices",
                                        PricingModel.PACKAGE,
                                        BigDecimal.ZERO,
                                        new BigDecimal("10"),
                                        null,
                                        List.of(new NewTier(null, BigDecimal.ZERO, new BigDecimal("5")))))));
        catalog.createPlan(
                tenantId,
                new NewPlan(
                        "starter",
                        "Starter (capped)",
                        Currency.getInstance("USD"),
                        interval,
                        props.graceSeconds(),
                        BigDecimal.ZERO,
                        List.of(
                                new NewComponent(
                                        "api_calls",
                                        PricingModel.PER_UNIT,
                                        new BigDecimal("500"),
                                        null,
                                        new BigDecimal("5000"),
                                        List.of(new NewTier(null, new BigDecimal("0.002"), BigDecimal.ZERO))),
                                new NewComponent(
                                        "tokens",
                                        PricingModel.PER_UNIT,
                                        BigDecimal.ZERO,
                                        null,
                                        null,
                                        List.of(new NewTier(null, new BigDecimal("0.000003"), BigDecimal.ZERO))))));
        catalog.createPlan(
                tenantId,
                new NewPlan(
                        "prepaid",
                        "Prepaid credits",
                        Currency.getInstance("USD"),
                        interval,
                        props.graceSeconds(),
                        BigDecimal.ZERO,
                        List.of(
                                new NewComponent(
                                        "api_calls",
                                        PricingModel.PER_UNIT,
                                        BigDecimal.ZERO,
                                        null,
                                        null,
                                        List.of(new NewTier(null, new BigDecimal("0.005"), BigDecimal.ZERO))),
                                new NewComponent(
                                        "tokens",
                                        PricingModel.PER_UNIT,
                                        BigDecimal.ZERO,
                                        null,
                                        null,
                                        List.of(new NewTier(null, new BigDecimal("0.000004"), BigDecimal.ZERO))))));

        int n = Math.max(props.customers(), 3);
        for (int i = 1; i <= n; i++) {
            String id = String.format("cust-%03d", i);
            String plan = i % 5 == 0 ? "starter" : i % 7 == 0 ? "prepaid" : "pro";
            catalog.createCustomer(tenantId, id, "Customer " + i + " (" + plan + ")");
            catalog.subscribe(tenantId, id, plan, clock.instant().minusSeconds(5));
            if (plan.equals("prepaid")) {
                credits.grant(tenantId, id, Money.of("25.00", "USD"), 0, null);
            }
        }

        if (props.webhookUrl() != null && !props.webhookUrl().isBlank()) {
            try {
                webhooks.register(tenantId, props.webhookUrl(), Set.of("*"));
            } catch (RuntimeException e) {
                log.warn("Demo webhook not registered: {}", e.getMessage());
            }
        }
        log.info("""

                ================================================================================
                  Tallyard demo tenant seeded: {} customers, plans pro/starter/prepaid
                  Admin API key: {}
                  Dashboard:     http://localhost:8080/   (paste the key)
                  OpenAPI:       http://localhost:8080/swagger-ui.html
                ================================================================================
                """, n, secret);
    }
}
