package io.tallyard.metering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.tallyard.catalog.BillingInterval;
import io.tallyard.metering.internal.MeteringHooks;
import io.tallyard.support.AbstractIntegrationTest;
import io.tallyard.support.ApiClient;
import io.tallyard.support.TestTenant;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

/**
 * A3: the worker commits the batch transaction, then dies before acknowledging Kafka offsets. The
 * batch is redelivered; every event hits the unique key and totals do not change.
 */
class ConsumerRedeliveryIT extends AbstractIntegrationTest {

    @Autowired
    TestTenant tenants;

    @Autowired
    MeteringHooks hooks;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    @AfterEach
    void resetHooks() {
        hooks.reset();
    }

    @Test
    void crashAfterCommitBeforeAckDoesNotDoubleCount() {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 60);
        Instant now = Instant.now().minusSeconds(5);
        tenants.customer(fx.tenantId(), "crashy", "std", now.minusSeconds(60));
        ApiClient admin = new ApiClient(port, fx.adminKey(), json);
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);

        AtomicInteger crashes = new AtomicInteger();
        hooks.setAfterCommit(() -> {
            if (crashes.getAndIncrement() < 2) {
                throw new IllegalStateException("simulated worker crash after commit, before ack");
            }
        });

        ingest.ingest(ApiClient.batch("crash", "request", "crashy", now, 120, Map.of("tokens", 5)));

        UUID period = admin.openPeriodId("crashy");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(crashes.get()).isGreaterThanOrEqualTo(3);
            Map<String, BigDecimal> usage = admin.usage(period);
            assertThat(usage.get("api_calls")).isEqualByComparingTo("120");
            assertThat(usage.get("tokens")).isEqualByComparingTo("600");
        });
        // Give any further redelivery a chance to (not) change anything.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(admin.usage(period).get("api_calls")).isEqualByComparingTo("120");
        });
        assertThat(admin.getJson("/v1/usage/stats").get("storedEvents").asLong())
                .isEqualTo(120);
    }
}
