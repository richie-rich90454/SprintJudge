package com.sprintjudge.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void writeThenReadRoundTrip() {
        String json = Json.write(Map.of("a", 1, "b", "two"));
        Map<String, Object> back = Json.readMap(json);
        assertEquals(1, ((Number) back.get("a")).intValue());
        assertEquals("two", back.get("b"));
    }

    @Test
    void readTreeParsesNested() {
        JsonNode node = Json.readTree("{\"x\":{\"y\":[1,2]}}");
        assertEquals(2, node.path("x").path("y").size());
    }

    @Test
    void readMapReturnsTypedValues() {
        Map<String, Object> m = Json.readMap("{\"correctIndex\":2,\"tag\":\"mcq\"}");
        assertEquals(2, ((Number) m.get("correctIndex")).intValue());
        assertEquals("mcq", m.get("tag"));
    }

    @Test
    void readMapEmptyObject() {
        assertTrue(Json.readMap("{}").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not json", "[1,2]", "\"scalar\"", "42", ""})
    void readMapRejectsNonObjects(String raw) {
        assertThrows(Exception.class, () -> Json.readMap(raw));
    }

    @Test
    void readTreeRejectsGarbage() {
        assertThrows(IllegalStateException.class, () -> Json.readTree("{nope"));
    }

    @Test
    void unknownFieldsAreIgnoredOnBinding() throws Exception {
        record Target(int a) {}
        Target t = Json.MAPPER.readValue("{\"a\":1,\"zzz\":9}", Target.class);
        assertEquals(1, t.a());
    }

    @Test
    void nullInputToWriteProducesNullLiteral() {
        assertEquals("null", Json.write(null));
    }

    @Test
    void readMapPreservesNestedStructures() {
        Map<String, Object> m = Json.readMap("{\"cfg\":{\"list\":[1,2],\"flag\":true}}");
        Object nested = m.get("cfg");
        assertTrue(nested instanceof Map);
        assertEquals(2, ((java.util.List<?>) ((Map<?, ?>) nested).get("list")).size());
        assertEquals(Boolean.TRUE, ((Map<?, ?>) nested).get("flag"));
    }
}
