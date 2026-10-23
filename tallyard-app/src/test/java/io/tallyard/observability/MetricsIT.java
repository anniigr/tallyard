package io.tallyard.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.tallyard.catalog.BillingInterval;
import io.tallyard.support.AbstractIntegrationTest;
import io.tallyard.support.ApiClient;
import io.tallyard.support.TestTenant;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** F3: the Prometheus endpoint exposes the metrics the Grafana dashboard and runbooks rely on. */
@AutoConfigureMetrics // Spring Boot disables metrics export in tests by default; this test is about the export
class MetricsIT extends AbstractIntegrationTest {

    @Autowired
    TestTenant tenants;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Test
    void customMetricsAreExposedAfterTraffic() {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 0);
        Instant now = Instant.now().minusSeconds(2);
        tenants.customer(fx.tenantId(), "metered", "std", now.minusSeconds(60));
        ApiClient admin = new ApiClient(port, fx.adminKey(), json);
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);
        ingest.ingest(ApiClient.batch("m", "request", "metered", now, 10, Map.of("tokens", 1)));
        ingest.ingest(ApiClient.batch("m", "request", "metered", now, 10, Map.of("tokens", 1)));
        admin.postJson("/v1/entitlements/check", Map.of("subject", "metered", "meter", "api_calls", "requested", 1));

        RestClient http = RestClient.create("http://localhost:" + managementPort);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String scrape = http.get().uri("/actuator/prometheus").retrieve().body(String.class);
            assertThat(scrape)
                    .contains("tallyard_ingest_events_total{")
                    .contains("tallyard_metering_events_total{")
                    .contains("outcome=\"duplicate\"")
                    .contains("tallyard_metering_batch_seconds")
                    .contains("tallyard_entitlements_check_seconds")
                    .contains("tallyard_outbox_backlog")
                    .contains("tallyard_webhooks_pending")
                    .contains("kafka_consumer_fetch_manager_records_lag");
        });
    }
}
