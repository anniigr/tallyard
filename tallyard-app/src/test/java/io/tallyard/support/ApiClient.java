package io.tallyard.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Minimal HTTP client used by integration tests; mirrors what an SDK would do. */
public final class ApiClient {

    private final RestClient http;
    private final ObjectMapper json;

    public ApiClient(int port, String apiKey, ObjectMapper json) {
        this.http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {})
                .build();
        this.json = json;
    }

    public record Event(
            String eventId, String eventType, String subject, Instant occurredAt, Map<String, Object> properties) {}

    public static Event event(String eventId, String type, String subject, Instant at, Map<String, Object> props) {
        return new Event(eventId, type, subject, at, props);
    }

    public ResponseEntity<String> ingest(List<Event> events) {
        return http.post()
                .uri("/v1/events")
                .body(Map.of("events", events))
                .retrieve()
                .toEntity(String.class);
    }

    /** Strings are sent verbatim as JSON (lets tests post malformed bodies); other objects are serialised. */
    public ResponseEntity<String> post(String path, Object body) {
        RestClient.RequestBodySpec spec = http.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (body instanceof String raw) {
            spec = spec.body(raw);
        } else if (body != null) {
            spec = spec.body(body);
        }
        return spec.retrieve().toEntity(String.class);
    }

    public ResponseEntity<String> get(String path) {
        return http.get().uri(path).retrieve().toEntity(String.class);
    }

    public JsonNode getJson(String path) {
        return json.readTree(get(path).getBody());
    }

    public JsonNode postJson(String path, Object body) {
        return json.readTree(post(path, body).getBody());
    }

    public JsonNode tree(ResponseEntity<String> response) {
        return json.readTree(response.getBody());
    }

    public UUID openPeriodId(String customer) {
        for (JsonNode p : getJson("/v1/periods?customer=" + customer)) {
            if (p.get("state").asString().equals("OPEN")) {
                return UUID.fromString(p.get("id").asString());
            }
        }
        throw new AssertionError("no open period for " + customer);
    }

    public Map<String, java.math.BigDecimal> usage(UUID periodId) {
        Map<String, java.math.BigDecimal> out = new java.util.LinkedHashMap<>();
        for (JsonNode u : getJson("/v1/periods/" + periodId + "/usage")) {
            out.put(u.get("meterCode").asString(), u.get("quantity").decimalValue());
        }
        return out;
    }

    public static List<Event> batch(
            String prefix, String type, String subject, Instant at, int n, Map<String, Object> props) {
        List<Event> events = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            events.add(new Event(prefix + "-" + i, type, subject, at, props));
        }
        return events;
    }
}
