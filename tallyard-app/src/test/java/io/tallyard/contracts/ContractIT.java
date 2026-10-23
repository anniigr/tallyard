package io.tallyard.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.tallyard.ingestion.UsageEventMessage;
import io.tallyard.support.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * D6/D7: the Kafka message contract is enforced by its published JSON Schema, and the HTTP contract
 * is captured as {@code docs/api/openapi.json}. Run with {@code -Dcontracts.update=true} to refresh
 * the committed document; otherwise the test fails when the API drifts from it.
 */
class ContractIT extends AbstractIntegrationTest {

    static final Path OPENAPI = Path.of("..", "docs", "api", "openapi.json");

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    @Test
    void usageEventMessagesConformToPublishedSchema() throws IOException {
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(Files.readString(Path.of("src/main/resources/schemas/usage-event.v1.schema.json")));
        UsageEventMessage ok = new UsageEventMessage(
                UUID.randomUUID(),
                "evt-1",
                "request",
                "acme",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"),
                Map.of("tokens", 12));
        Set<ValidationMessage> errors =
                schema.validate(json.writeValueAsString(ok), com.networknt.schema.InputFormat.JSON);
        assertThat(errors).isEmpty();

        String missingSubject = json.writeValueAsString(Map.of(
                "tenantId",
                UUID.randomUUID().toString(),
                "eventId",
                "e",
                "eventType",
                "request",
                "occurredAt",
                "2026-01-01T00:00:00Z",
                "ingestedAt",
                "2026-01-01T00:00:01Z",
                "properties",
                Map.of()));
        assertThat(schema.validate(missingSubject, com.networknt.schema.InputFormat.JSON))
                .isNotEmpty();

        String badType = json.writeValueAsString(Map.of(
                "tenantId",
                UUID.randomUUID().toString(),
                "eventId",
                "e",
                "eventType",
                "9 bad",
                "subject",
                "s",
                "occurredAt",
                "2026-01-01T00:00:00Z",
                "ingestedAt",
                "2026-01-01T00:00:01Z",
                "properties",
                Map.of()));
        assertThat(schema.validate(badType, com.networknt.schema.InputFormat.JSON))
                .isNotEmpty();
    }

    @Test
    void openApiDocumentMatchesCommittedContract() throws IOException {
        String live = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/v3/api-docs")
                .retrieve()
                .body(String.class);
        var tree = json.readTree(live);
        assertThat(tree.get("paths").has("/v1/events")).isTrue();
        assertThat(tree.get("paths").has("/v1/entitlements/check")).isTrue();
        assertThat(tree.get("paths").has("/v1/periods/{id}/replay")).isTrue();
        String pretty = json.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        if (Boolean.getBoolean("contracts.update") || !Files.exists(OPENAPI)) {
            Files.createDirectories(OPENAPI.getParent());
            Files.writeString(OPENAPI, pretty + "\n", StandardCharsets.UTF_8);
        }
        var committed = json.readTree(Files.readString(OPENAPI));
        assertThat(tree.get("paths").properties().stream()
                        .map(Map.Entry::getKey)
                        .toList())
                .as("paths drifted from docs/api/openapi.json; run with -Dcontracts.update=true if intended")
                .containsExactlyInAnyOrderElementsOf(committed.get("paths").properties().stream()
                        .map(Map.Entry::getKey)
                        .toList());
        assertThat(tree.get("components").get("schemas").properties().stream()
                        .map(Map.Entry::getKey)
                        .toList())
                .containsExactlyInAnyOrderElementsOf(committed.get("components").get("schemas").properties().stream()
                        .map(Map.Entry::getKey)
                        .toList());
    }
}
