package io.tallyard.shared.json;

import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Thin wrapper over the application's {@link ObjectMapper} used where JSON is stored as text in
 * {@code jsonb} columns. Centralising it keeps serialisation settings identical across modules.
 */
@Component
public class Json {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    public Map<String, Object> readMap(String json) {
        return mapper.readValue(json, MAP);
    }

    public JsonNode readTree(String json) {
        return mapper.readTree(json);
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
