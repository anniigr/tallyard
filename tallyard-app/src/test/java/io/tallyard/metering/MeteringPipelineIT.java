package io.tallyard.metering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.tallyard.catalog.BillingInterval;
import io.tallyard.support.AbstractIntegrationTest;
import io.tallyard.support.ApiClient;
import io.tallyard.support.TestTenant;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end counting correctness through HTTP → Kafka → consumer → PostgreSQL (acceptance
 * criteria A1, A2, A4, A6, A7, A8).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeteringPipelineIT extends AbstractIntegrationTest {

    @Autowired
    TestTenant tenants;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    TestTenant.Fixture fx;
    ApiClient admin;
    ApiClient ingest;
    Instant now;

    @BeforeAll
    void setUp() {
        fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 60);
        now = Instant.now().minusSeconds(5);
        tenants.customer(fx.tenantId(), "acme", "std", now.minusSeconds(3600));
        tenants.customer(fx.tenantId(), "globex", "std", now.minusSeconds(3600));
        tenants.customer(fx.tenantId(), "initech", "std", now.minusSeconds(3600));
        admin = new ApiClient(port, fx.adminKey(), json);
        ingest = new ApiClient(port, fx.ingestKey(), json);
    }

    @Test
    void duplicateEventIdsAreCountedOnce() throws Exception {
        List<ApiClient.Event> batch = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            batch.add(ApiClient.event("dup-" + i, "request", "acme", now, Map.of("tokens", 10)));
        }
        // Same batch three times, plus an in-batch duplicate.
        batch.add(ApiClient.event("dup-0", "request", "acme", now, Map.of("tokens", 999)));
        assertThat(ingest.ingest(batch).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(ingest.ingest(batch).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(ingest.ingest(batch).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        UUID period = admin.openPeriodId("acme");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, BigDecimal> usage = admin.usage(period);
            assertThat(usage.get("api_calls")).isEqualByComparingTo("50");
            assertThat(usage.get("tokens")).isEqualByComparingTo("500");
        });
        // and it stays that way
        Thread.sleep(1500);
        assertThat(admin.usage(period).get("api_calls")).isEqualByComparingTo("50");
    }

    @Test
    void concurrentSubmissionOfTheSameBatchIsIdempotent() throws Exception {
        List<ApiClient.Event> batch = ApiClient.batch("conc", "request", "globex", now, 200, Map.of("tokens", 3));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> ingest.ingest(batch).getStatusCode().value(), pool));
        }
        for (CompletableFuture<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(202);
        }
        pool.shutdown();
        UUID period = admin.openPeriodId("globex");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(admin.usage(period).get("api_calls")).isEqualByComparingTo("200");
            assertThat(admin.usage(period).get("tokens")).isEqualByComparingTo("600");
        });
    }

    @Test
    void allAggregationsProduceExpectedValues() {
        List<ApiClient.Event> batch = List.of(
                ApiClient.event("agg-s1", "storage", "acme", now.minusSeconds(30), Map.of("gb", "12.5")),
                ApiClient.event("agg-s2", "storage", "acme", now.minusSeconds(20), Map.of("gb", 40)),
                ApiClient.event("agg-s3", "storage", "acme", now.minusSeconds(10), Map.of("gb", "7.25")),
                ApiClient.event("agg-h1", "heartbeat", "acme", now, Map.of("device_id", "d-1")),
                ApiClient.event("agg-h2", "heartbeat", "acme", now, Map.of("device_id", "d-2")),
                ApiClient.event("agg-h3", "heartbeat", "acme", now, Map.of("device_id", "d-1")));
        assertThat(ingest.ingest(batch).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID period = admin.openPeriodId("acme");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, BigDecimal> usage = admin.usage(period);
            assertThat(usage.get("peak_gb")).isEqualByComparingTo("40");
            assertThat(usage.get("devices")).isEqualByComparingTo("2");
        });
    }

    @Test
    void outOfOrderEventsYieldTheSameTotalsAsSorted() {
        List<ApiClient.Event> shuffled = new ArrayList<>();
        for (int i = 20; i >= 1; i--) {
            shuffled.add(ApiClient.event("ooo-" + i, "request", "initech", now.minusSeconds(i), Map.of("tokens", i)));
        }
        shuffled.add(ApiClient.event("ooo-gb-late", "storage", "initech", now.minusSeconds(30), Map.of("gb", 5)));
        shuffled.add(ApiClient.event("ooo-gb-first", "storage", "initech", now.minusSeconds(10), Map.of("gb", 1)));
        assertThat(ingest.ingest(shuffled).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID period = admin.openPeriodId("initech");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, BigDecimal> usage = admin.usage(period);
            assertThat(usage.get("api_calls")).isEqualByComparingTo("20");
            assertThat(usage.get("tokens")).isEqualByComparingTo("210"); // 1 + 2 + … + 20
            assertThat(usage.get("peak_gb")).isEqualByComparingTo("5");
        });
    }

    @Test
    void semanticallyInvalidEventsAreRejectedWithReasons() {
        List<ApiClient.Event> batch = List.of(
                ApiClient.event("bad-1", "request", "nobody", now, Map.of("tokens", 1)),
                ApiClient.event("bad-2", "unknown.type", "acme", now, Map.of()),
                ApiClient.event("bad-3", "request", "acme", now, Map.of("tokens", "lots")),
                ApiClient.event("bad-4", "request", "acme", now.minus(Duration.ofDays(40)), Map.of("tokens", 1)));
        assertThat(ingest.ingest(batch).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode rejections = admin.getJson("/v1/rejections?limit=100");
            Map<String, String> byEvent = new java.util.HashMap<>();
            for (JsonNode r : rejections) {
                byEvent.put(r.get("eventId").asString(), r.get("reason").asString());
            }
            assertThat(byEvent).containsEntry("bad-1", "UNKNOWN_SUBJECT");
            assertThat(byEvent).containsEntry("bad-2", "NO_MATCHING_METER");
            assertThat(byEvent).containsEntry("bad-3", "INVALID_VALUE");
            // bad-4 is 40 days old: no active subscription at that time (subscription started an hour ago)
            assertThat(byEvent).containsKey("bad-4");
        });
    }

    @Test
    void structurallyInvalidEventsAreReportedPerItemWithoutFailingTheBatch() {
        var response = ingest.post(
                "/v1/events",
                Map.of(
                        "events",
                        List.of(
                                Map.of(
                                        "eventId",
                                        "ok-1",
                                        "eventType",
                                        "request",
                                        "subject",
                                        "acme",
                                        "occurredAt",
                                        now.toString(),
                                        "properties",
                                        Map.of("tokens", 1)),
                                Map.of(
                                        "eventId",
                                        "future",
                                        "eventType",
                                        "request",
                                        "subject",
                                        "acme",
                                        "occurredAt",
                                        now.plus(Duration.ofHours(1)).toString(),
                                        "properties",
                                        Map.of()))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode body = ingest.tree(response);
        assertThat(body.get("accepted").asInt()).isEqualTo(1);
        assertThat(body.get("invalid").asInt()).isEqualTo(1);
        assertThat(body.get("results").get(1).get("status").asString()).isEqualTo("INVALID");
        assertThat(body.get("results").get(1).get("errors").get(0).asString()).contains("future");
    }
}
