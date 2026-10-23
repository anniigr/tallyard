package io.tallyard.billing;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Period lifecycle, rating, late-event policy, replay and concurrent rating (B1–B8, A5). */
class BillingLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    TestTenant tenants;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    record Env(TestTenant.Fixture fx, ApiClient admin, ApiClient ingest, Instant now) {}

    Env env(String customer, BillingInterval interval) {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", interval, 0);
        Instant now = Instant.now().minusSeconds(2);
        tenants.customer(fx.tenantId(), customer, "std", now.minusSeconds(120));
        return new Env(fx, new ApiClient(port, fx.adminKey(), json), new ApiClient(port, fx.ingestKey(), json), now);
    }

    @Test
    void subscriptionOpensExactlyOnePeriodCoveringItsStart() {
        Env e = env("one", new BillingInterval(BillingInterval.Unit.DAYS, 30));
        JsonNode periods = e.admin().getJson("/v1/periods?customer=one");
        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).get("state").asString()).isEqualTo("OPEN");
        assertThat(Instant.parse(periods.get(0).get("startsAt").asString())).isBeforeOrEqualTo(e.now());
    }

    @Test
    void rateProducesDeterministicInvoiceThatReplayReproduces() {
        Env e = env("acme", new BillingInterval(BillingInterval.Unit.DAYS, 30));
        assertThat(e.ingest()
                        .ingest(ApiClient.batch("r1", "request", "acme", e.now(), 1000, Map.of("tokens", 100)))
                        .getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        List<ApiClient.Event> batch =
                new ArrayList<>(ApiClient.batch("r2", "request", "acme", e.now(), 600, Map.of("tokens", 100)));
        batch.add(ApiClient.event("gb", "storage", "acme", e.now(), Map.of("gb", 12)));
        batch.add(ApiClient.event("d1", "heartbeat", "acme", e.now(), Map.of("device_id", "a")));
        batch.add(ApiClient.event("d2", "heartbeat", "acme", e.now(), Map.of("device_id", "b")));
        assertThat(e.ingest().ingest(batch).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID period = e.admin().openPeriodId("acme");
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(e.admin().usage(period).get("api_calls")).isEqualByComparingTo("1600"));

        JsonNode invoice = e.admin().postJson("/v1/periods/" + period + "/rate", null);
        // api_calls graduated: 100 included, 1000 @ 0.01 = 10.00, 500 @ 0.005 = 2.50 → 12.50
        // tokens 160,000 × 0.0001 = 16.00; peak_gb 12 → volume tier 2 → 12 × 0.25 = 3.00; devices 2 → 1 package = 2.00
        assertThat(invoice.get("subtotal").decimalValue()).isEqualByComparingTo("33.50");
        assertThat(invoice.get("lines")).hasSize(4);
        assertThat(invoice.get("reproducibilityHash").asString()).hasSize(64);

        JsonNode replay = e.admin().postJson("/v1/periods/" + period + "/replay", null);
        assertThat(replay.get("matches").asBoolean()).as(replay.toString()).isTrue();
        assertThat(replay.get("replayedHash").asString())
                .isEqualTo(invoice.get("reproducibilityHash").asString());

        // The successor period is open and contiguous.
        JsonNode periods = e.admin().getJson("/v1/periods?customer=acme");
        assertThat(periods).hasSize(2);
        JsonNode open = periods.get(0).get("state").asString().equals("OPEN") ? periods.get(0) : periods.get(1);
        JsonNode rated = open == periods.get(0) ? periods.get(1) : periods.get(0);
        assertThat(rated.get("state").asString()).isEqualTo("RATED");
        assertThat(open.get("startsAt").asString())
                .isEqualTo(rated.get("endsAt").asString());
    }

    @Test
    void lateEventForRatedPeriodBecomesAdjustmentAndReplayStillMatches() {
        Env e = env("late", new BillingInterval(BillingInterval.Unit.DAYS, 30));
        e.ingest().ingest(ApiClient.batch("base", "request", "late", e.now(), 10, Map.of("tokens", 1)));
        UUID first = e.admin().openPeriodId("late");
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(e.admin().usage(first).get("api_calls")).isEqualByComparingTo("10"));
        JsonNode invoice = e.admin().postJson("/v1/periods/" + first + "/rate", null);
        BigDecimal ratedCalls = invoice.get("lines").get(0).get("quantity").decimalValue();
        assertThat(ratedCalls).isEqualByComparingTo("10");

        // Events whose occurredAt falls inside the now-RATED period: additive meters become adjustments,
        // a MAX meter cannot be carried forward and is rejected with a reason.
        e.ingest()
                .ingest(List.of(
                        ApiClient.event("late-1", "request", "late", e.now().minusSeconds(1), Map.of("tokens", 50)),
                        ApiClient.event("late-gb", "storage", "late", e.now().minusSeconds(1), Map.of("gb", 99))));
        UUID open = e.admin().openPeriodId("late");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode adjustments = e.admin().getJson("/v1/periods/" + open + "/adjustments");
            assertThat(adjustments).hasSize(2); // api_calls (+1) and tokens (+50)
            assertThat(adjustments.get(0).get("originalPeriodId").asString()).isEqualTo(first.toString());
        });
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<String> reasons = new ArrayList<>();
            e.admin()
                    .getJson("/v1/rejections?limit=20")
                    .forEach(r -> reasons.add(r.get("reason").asString()));
            assertThat(reasons).contains("LATE_NOT_ADJUSTABLE");
        });
        // Rated period is untouched (buckets frozen) and still reproducible from its attributed events.
        assertThat(e.admin().usage(first).get("api_calls")).isEqualByComparingTo("10");
        assertThat(e.admin()
                        .postJson("/v1/periods/" + first + "/replay", null)
                        .get("matches")
                        .asBoolean())
                .isTrue();

        // Rating the open period prices the late usage as an ADJUSTMENT line.
        JsonNode second = e.admin().postJson("/v1/periods/" + open + "/rate", null);
        List<String> kinds = new ArrayList<>();
        second.get("lines").forEach(l -> kinds.add(l.get("kind").asString()));
        assertThat(kinds).contains("ADJUSTMENT");
    }

    @Test
    void prepaidCustomerIsRatedWithCreditsAppliedThroughThePublicApi() {
        Env e = env("prepaid", new BillingInterval(BillingInterval.Unit.DAYS, 30));
        e.admin().post("/v1/credits", Map.of("customerExternalId", "prepaid", "currency", "USD", "amount", 5));
        e.ingest().ingest(ApiClient.batch("pp", "request", "prepaid", e.now(), 200, Map.of("tokens", 1000)));
        UUID period = e.admin().openPeriodId("prepaid");
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(e.admin().usage(period).get("api_calls")).isEqualByComparingTo("200"));
        // 100 billable calls × 0.01 = 1.00; 200,000 tokens × 0.0001 = 20.00 → subtotal 21.00, credits 5.00
        JsonNode invoice = e.admin().postJson("/v1/periods/" + period + "/rate", null);
        assertThat(invoice.get("subtotal").decimalValue()).isEqualByComparingTo("21.00");
        assertThat(invoice.get("creditsApplied").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(invoice.get("total").decimalValue()).isEqualByComparingTo("16.00");
        // what is persisted must match what the rating call returned
        JsonNode stored = e.admin().getJson("/v1/periods/" + period + "/invoice");
        assertThat(stored.get("creditsApplied").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(stored.get("total").decimalValue()).isEqualByComparingTo("16.00");
        JsonNode grants = e.admin().getJson("/v1/customers/prepaid/credits");
        assertThat(grants.get(0).get("remaining").decimalValue()).isEqualByComparingTo("0");
        assertThat(e.admin()
                        .postJson("/v1/periods/" + period + "/replay", null)
                        .get("matches")
                        .asBoolean())
                .isTrue();
    }

    @Test
    void illegalTransitionsAreRejected() {
        Env e = env("ill", new BillingInterval(BillingInterval.Unit.DAYS, 30));
        UUID period = e.admin().openPeriodId("ill");
        ResponseEntity<String> finalizeOpen = e.admin().post("/v1/periods/" + period + "/finalize", null);
        assertThat(finalizeOpen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.admin().tree(finalizeOpen).get("code").asString()).isEqualTo("period.illegal_transition");

        e.admin().post("/v1/periods/" + period + "/rate", null);
        ResponseEntity<String> rateAgain = e.admin().post("/v1/periods/" + period + "/rate", null);
        assertThat(rateAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.admin().post("/v1/periods/" + period + "/finalize", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(e.admin().post("/v1/periods/" + period + "/replay", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentRatingProducesExactlyOneInvoice() throws Exception {
        Env e = env("race", new BillingInterval(BillingInterval.Unit.DAYS, 30));
        e.ingest().ingest(ApiClient.batch("race", "request", "race", e.now(), 5, Map.of("tokens", 1)));
        UUID period = e.admin().openPeriodId("race");
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(e.admin().usage(period).get("api_calls")).isEqualByComparingTo("5"));

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> e.admin()
                            .post("/v1/periods/" + period + "/rate", null)
                            .getStatusCode()
                            .value(),
                    pool));
        }
        List<Integer> statuses = new ArrayList<>();
        for (CompletableFuture<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        assertThat(e.admin().getJson("/v1/invoices")).hasSize(1);
    }

    @Test
    void schedulerClosesAndRatesPeriodsWhenDue() {
        Env e = env("sched", new BillingInterval(BillingInterval.Unit.MINUTES, 1));
        // a prepaid customer in the same tenant must not stall the scheduler for the others
        tenants.customer(e.fx().tenantId(), "sched-prepaid", "std", e.now().minusSeconds(120));
        e.admin().post("/v1/credits", Map.of("customerExternalId", "sched-prepaid", "currency", "USD", "amount", 3));
        // subscription started 120 s ago with 1-minute periods and no grace: the scheduler must have
        // rated the first period and opened successors.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode periods = e.admin().getJson("/v1/periods?customer=sched");
            List<String> states = new ArrayList<>();
            periods.forEach(p -> states.add(p.get("state").asString()));
            assertThat(states).contains("RATED").contains("OPEN");
            assertThat(states.stream().filter("OPEN"::equals).count()).isEqualTo(1);
        });
    }
}
