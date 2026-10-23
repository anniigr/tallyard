package io.tallyard.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.tallyard.catalog.BillingInterval;
import io.tallyard.credits.CreditLedger;
import io.tallyard.shared.money.Money;
import io.tallyard.support.AbstractIntegrationTest;
import io.tallyard.support.ApiClient;
import io.tallyard.support.TestTenant;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** C3/C4: limits deny once exceeded; decisions follow usage within the staleness bound; credits gate access. */
class EntitlementIT extends AbstractIntegrationTest {

    @Autowired
    TestTenant tenants;

    @Autowired
    CreditLedger credits;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    @Test
    void deniesWhenHardLimitWouldBeExceededAndReflectsNewUsageWithinTtl() {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(
                fx.tenantId(),
                "std",
                new BillingInterval(BillingInterval.Unit.DAYS, 30),
                0); // api_calls hardLimit 10,000
        Instant now = Instant.now().minusSeconds(2);
        tenants.customer(fx.tenantId(), "capped", "std", now.minusSeconds(60));
        ApiClient read = new ApiClient(port, fx.readKey(), json);
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);

        JsonNode fresh = read.postJson(
                "/v1/entitlements/check", Map.of("subject", "capped", "meter", "api_calls", "requested", 1));
        assertThat(fresh.get("decision").asString()).isEqualTo("ALLOW");
        assertThat(fresh.get("limit").decimalValue()).isEqualByComparingTo("10000");

        JsonNode tooMuch = read.postJson(
                "/v1/entitlements/check", Map.of("subject", "capped", "meter", "api_calls", "requested", 10001));
        assertThat(tooMuch.get("decision").asString()).isEqualTo("DENY");
        assertThat(tooMuch.get("reason").asString()).isEqualTo("LIMIT_EXCEEDED");

        ingest.ingest(ApiClient.batch("cap", "request", "capped", now, 1000, Map.of("tokens", 1)));
        // Usage becomes visible to decisions within consumer latency + cache TTL (0.5 s in tests).
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode d = read.postJson(
                    "/v1/entitlements/check", Map.of("subject", "capped", "meter", "api_calls", "requested", 9001));
            assertThat(d.get("used").decimalValue()).isEqualByComparingTo("1000");
            assertThat(d.get("decision").asString()).isEqualTo("DENY");
        });
        JsonNode ok = read.postJson(
                "/v1/entitlements/check", Map.of("subject", "capped", "meter", "api_calls", "requested", 9000));
        assertThat(ok.get("decision").asString()).isEqualTo("ALLOW");
        assertThat(ok.get("remaining").decimalValue()).isEqualByComparingTo("9000");

        JsonNode noLimit =
                read.postJson("/v1/entitlements/check", Map.of("subject", "capped", "meter", "tokens", "requested", 1));
        assertThat(noLimit.get("decision").asString()).isEqualTo("ALLOW");
        assertThat(noLimit.get("reason").asString()).isEqualTo("NO_LIMIT");
    }

    @Test
    void unknownSubjectsAndMetersAreDenied() {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 0);
        tenants.customer(fx.tenantId(), "known", "std", Instant.now().minusSeconds(60));
        ApiClient read = new ApiClient(port, fx.readKey(), json);
        assertThat(read.postJson("/v1/entitlements/check", Map.of("subject", "ghost", "meter", "api_calls"))
                        .get("reason")
                        .asString())
                .isEqualTo("UNKNOWN_SUBJECT");
        assertThat(read.postJson("/v1/entitlements/check", Map.of("subject", "known", "meter", "nope"))
                        .get("reason")
                        .asString())
                .isEqualTo("METER_NOT_IN_PLAN");
    }

    @Test
    void prepaidCustomersAreDeniedWhenEstimatedChargesExhaustCredit() {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 0);
        Instant now = Instant.now().minusSeconds(2);
        tenants.customer(fx.tenantId(), "prepaid", "std", now.minusSeconds(60));
        credits.grant(fx.tenantId(), "prepaid", Money.of("5.00", "USD"), 0, null);
        ApiClient read = new ApiClient(port, fx.readKey(), json);
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);

        JsonNode before = read.postJson(
                "/v1/entitlements/check", Map.of("subject", "prepaid", "meter", "tokens", "requested", 1));
        assertThat(before.get("decision").asString()).isEqualTo("ALLOW");
        // minimum commitment is $1, so remaining credit is 5 − 1 = 4
        assertThat(before.get("creditRemaining").decimalValue()).isEqualByComparingTo("4.00");

        // 60,000 tokens × 0.0001 = $6 > $5 of credit
        ingest.ingest(ApiClient.batch("pp", "request", "prepaid", now, 60, Map.of("tokens", 1000)));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode d = read.postJson(
                    "/v1/entitlements/check", Map.of("subject", "prepaid", "meter", "tokens", "requested", 1));
            assertThat(d.get("decision").asString()).isEqualTo("DENY");
            assertThat(d.get("reason").asString()).isEqualTo("CREDIT_EXHAUSTED");
            assertThat(d.get("creditRemaining").decimalValue()).isLessThanOrEqualTo(java.math.BigDecimal.ZERO);
        });
    }
}
