package io.tallyard.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.tallyard.support.AbstractIntegrationTest;
import io.tallyard.support.ApiClient;
import io.tallyard.support.TestTenant;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** D3 and tenant isolation: scopes are enforced per endpoint and tenants cannot see each other's data. */
class SecurityIT extends AbstractIntegrationTest {

    @Autowired
    TestTenant tenants;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Test
    void missingKeyIsUnauthorizedAndWrongScopeIsForbidden() {
        TestTenant.Fixture fx = tenants.create();
        ApiClient anonymous = new ApiClient(port, "", json);
        ApiClient read = new ApiClient(port, fx.readKey(), json);
        ApiClient ingest = new ApiClient(port, fx.ingestKey(), json);
        ApiClient bogus = new ApiClient(port, "ty_" + "x".repeat(40), json);

        assertThat(anonymous.get("/v1/meters").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bogus.get("/v1/meters").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> forbidden = read.post(
                "/v1/meters",
                Map.of(
                        "code",
                        "calls",
                        "name",
                        "Calls",
                        "eventType",
                        "request",
                        "aggregation",
                        "COUNT",
                        "unit",
                        "call"));
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(read.tree(forbidden).get("code").asString()).isEqualTo("forbidden");
        // INGEST may post events but not read usage.
        assertThat(ingest.get("/v1/meters").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ingest.ingest(List.of(ApiClient.event("e", "t", "s", Instant.now(), Map.of())))
                        .getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        // READ may not ingest.
        assertThat(read.ingest(List.of(ApiClient.event("e", "t", "s", Instant.now(), Map.of())))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantsAreIsolated() {
        TestTenant.Fixture a = tenants.create();
        TestTenant.Fixture b = tenants.create();
        tenants.standardMeters(a.tenantId());
        ApiClient adminA = new ApiClient(port, a.adminKey(), json);
        ApiClient adminB = new ApiClient(port, b.adminKey(), json);
        assertThat(adminA.getJson("/v1/meters")).hasSize(4);
        assertThat(adminB.getJson("/v1/meters")).isEmpty();
        // B cannot act on A's period ids even when guessing them.
        tenants.standardPlan(
                a.tenantId(),
                "p",
                new io.tallyard.catalog.BillingInterval(io.tallyard.catalog.BillingInterval.Unit.DAYS, 30),
                0);
        tenants.customer(a.tenantId(), "c", "p", Instant.now().minusSeconds(10));
        var period = adminA.openPeriodId("c");
        assertThat(adminB.get("/v1/periods/" + period).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(adminB.post("/v1/periods/" + period + "/rate", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(adminB.get("/v1/periods/" + period + "/usage").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void revokedKeysStopWorkingAndSecretsAreNeverListed() {
        TestTenant.Fixture fx = tenants.create();
        ApiClient admin = new ApiClient(port, fx.adminKey(), json);
        JsonNode issued = admin.postJson("/v1/api-keys", Map.of("name", "temp", "scopes", List.of("READ")));
        String secret = issued.get("secret").asString();
        assertThat(secret).startsWith("ty_").hasSize(43);
        ApiClient temp = new ApiClient(port, secret, json);
        assertThat(temp.get("/v1/meters").getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode listed = admin.getJson("/v1/api-keys");
        listed.forEach(k -> assertThat(k.has("secret")).isFalse());
        assertThat(listed.toString()).doesNotContain(secret);

        assertThat(admin.get("/v1/api-keys/" + issued.get("id").asString()).getStatusCode())
                .isIn(HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.NOT_FOUND);
        var delete = org.springframework.web.client.RestClient.create("http://localhost:" + port)
                .delete()
                .uri("/v1/api-keys/" + issued.get("id").asString())
                .header("X-Api-Key", fx.adminKey())
                .retrieve()
                .toBodilessEntity();
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(temp.get("/v1/meters").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void bootstrapEndpointRequiresToken() {
        ApiClient anonymous = new ApiClient(port, "", json);
        ResponseEntity<String> r = anonymous.post("/v1/admin/tenants", Map.of("name", "x"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void actuatorIsReachableWithoutKeyAndReportsReadiness() {
        ApiClient anonymous = new ApiClient(managementPort, "", json);
        JsonNode health = anonymous.getJson("/actuator/health/readiness");
        assertThat(health.get("status").asString()).isEqualTo("UP");
        assertThat(health.get("components").get("kafka").get("status").asString())
                .isEqualTo("UP");
        assertThat(health.get("components").get("db").get("status").asString()).isEqualTo("UP");
    }
}
