package com.sprintjudge.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

public final class Json {

    private Json() {}

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static JsonNode readTree(String value) {
        try {
            return MAPPER.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON parse failed", e);
        }
    }

    public static <T> T read(String value, Class<T> type) {
        try {
            return MAPPER.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON parse failed", e);
        }
    }

    /** Generic-safe map parsing; avoids unchecked raw-collection casts at call sites. */
    public static Map<String, Object> readMap(String value) {
        try {
            return MAPPER.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON map parse failed", e);
        }
    }
}
