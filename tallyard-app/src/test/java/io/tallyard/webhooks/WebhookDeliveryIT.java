package io.tallyard.webhooks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.tallyard.catalog.BillingInterval;
import io.tallyard.support.AbstractIntegrationTest;
import io.tallyard.support.ApiClient;
import io.tallyard.support.TestTenant;
import io.tallyard.webhooks.internal.WebhookSigner;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** E1–E4: outbox → Kafka → webhook fan-out; signatures; retry with backoff; dead-lettering; manual retry. */
class WebhookDeliveryIT extends AbstractIntegrationTest {

    static WireMockServer wiremock;

    @Autowired
    TestTenant tenants;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    @BeforeAll
    static void startWireMock() {
        wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wiremock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wiremock.stop();
    }

    record Env(TestTenant.Fixture fx, ApiClient admin, ApiClient ingest, Instant now) {}

    Env env(String customer) {
        TestTenant.Fixture fx = tenants.create();
        tenants.standardMeters(fx.tenantId());
        tenants.standardPlan(fx.tenantId(), "std", new BillingInterval(BillingInterval.Unit.DAYS, 30), 0);
        Instant now = Instant.now().minusSeconds(2);
        tenants.customer(fx.tenantId(), customer, "std", now.minusSeconds(60));
        return new Env(fx, new ApiClient(port, fx.adminKey(), json), new ApiClient(port, fx.ingestKey(), json), now);
    }

    @Test
    void ratedPeriodProducesSignedWebhookDeliveredExactlyOnce() {
        Env e = env("hooked");
        wiremock.stubFor(post(urlEqualTo("/hooks/ok")).willReturn(aResponse().withStatus(200)));
        JsonNode registered = e.admin()
                .postJson(
                        "/v1/webhooks",
                        Map.of(
                                "url",
                                wiremock.baseUrl() + "/hooks/ok",
                                "eventTypes",
                                List.of("period.rated", "invoice.drafted")));
        String secret = registered.get("secret").asString();
        assertThat(secret).startsWith("whsec_");

        UUID period = e.admin().openPeriodId("hooked");
        e.admin().post("/v1/periods/" + period + "/rate", null);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> wiremock.verify(2, postRequestedFor(urlEqualTo("/hooks/ok"))));
        List<LoggedRequest> requests = wiremock.findAll(postRequestedFor(urlEqualTo("/hooks/ok")));
        for (LoggedRequest r : requests) {
            String body = r.getBodyAsString();
            assertThat(WebhookSigner.verify(secret, r.getHeader(WebhookSigner.HEADER), body))
                    .isTrue();
            assertThat(WebhookSigner.verify("whsec_wrong", r.getHeader(WebhookSigner.HEADER), body))
                    .isFalse();
            JsonNode envelope = json.readTree(body);
            assertThat(envelope.get("type").asString()).isIn("period.rated", "invoice.drafted");
            assertThat(envelope.get("data").get("periodId").asString()).isEqualTo(period.toString());
            assertThat(r.getHeader("X-Tallyard-Event"))
                    .isEqualTo(envelope.get("type").asString());
        }
        // the registration listing never reveals the secret
        assertThat(e.admin().getJson("/v1/webhooks").toString()).doesNotContain(secret);
        // exactly two deliveries, both DELIVERED, one attempt each (period.opened was not subscribed)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode deliveries = e.admin().getJson("/v1/webhooks/deliveries");
            assertThat(deliveries).hasSize(2);
            deliveries.forEach(d -> {
                assertThat(d.get("state").asString()).isEqualTo("DELIVERED");
                assertThat(d.get("attempts").asInt()).isEqualTo(1);
            });
        });
    }

    @Test
    void failingEndpointIsRetriedWithBackoffThenDeadLetteredAndCanBeRetriedManually() {
        Env e = env("flaky");
        wiremock.stubFor(post(urlEqualTo("/hooks/down")).willReturn(aResponse().withStatus(500)));
        e.admin()
                .postJson(
                        "/v1/webhooks",
                        Map.of("url", wiremock.baseUrl() + "/hooks/down", "eventTypes", List.of("period.rated")));
        UUID period = e.admin().openPeriodId("flaky");
        e.admin().post("/v1/periods/" + period + "/rate", null);

        // max-attempts is 3 in the test profile with 0.5 s initial backoff
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            JsonNode deliveries = e.admin().getJson("/v1/webhooks/deliveries");
            assertThat(deliveries).hasSize(1);
            assertThat(deliveries.get(0).get("state").asString()).isEqualTo("DEAD");
            assertThat(deliveries.get(0).get("attempts").asInt()).isEqualTo(3);
            assertThat(deliveries.get(0).get("lastStatus").asInt()).isEqualTo(500);
        });
        wiremock.verify(3, postRequestedFor(urlEqualTo("/hooks/down")));

        // Operator fixes the endpoint and re-queues the dead delivery.
        wiremock.stubFor(post(urlEqualTo("/hooks/down")).willReturn(aResponse().withStatus(204)));
        String id =
                e.admin().getJson("/v1/webhooks/deliveries").get(0).get("id").asString();
        assertThat(e.admin()
                        .postJson("/v1/webhooks/deliveries/" + id + "/retry", null)
                        .get("state")
                        .asString())
                .isEqualTo("PENDING");
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(e.admin()
                                .getJson("/v1/webhooks/deliveries")
                                .get(0)
                                .get("state")
                                .asString())
                        .isEqualTo("DELIVERED"));
    }

    @Test
    void transientFailureRecoversWithoutDuplicateDelivery() {
        Env e = env("transient");
        wiremock.stubFor(post(urlEqualTo("/hooks/flap"))
                .inScenario("flap")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("up"));
        wiremock.stubFor(post(urlEqualTo("/hooks/flap"))
                .inScenario("flap")
                .whenScenarioStateIs("up")
                .willReturn(aResponse().withStatus(200)));
        e.admin()
                .postJson(
                        "/v1/webhooks",
                        Map.of("url", wiremock.baseUrl() + "/hooks/flap", "eventTypes", List.of("invoice.drafted")));
        UUID period = e.admin().openPeriodId("transient");
        e.admin().post("/v1/periods/" + period + "/rate", null);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode deliveries = e.admin().getJson("/v1/webhooks/deliveries");
            assertThat(deliveries).hasSize(1);
            assertThat(deliveries.get(0).get("state").asString()).isEqualTo("DELIVERED");
            assertThat(deliveries.get(0).get("attempts").asInt()).isEqualTo(2);
        });
        wiremock.verify(2, postRequestedFor(urlEqualTo("/hooks/flap")));
    }
}
