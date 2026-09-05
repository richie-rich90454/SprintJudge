package com.sprintjudge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.dto.export.ExportBundle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonBreadthTest {

    @Test
    void writeListRoundTrips() {
        String json = Json.write(List.of("a", "b", "c"));
        JsonNode back = Json.readTree(json);
        assertEquals(3, back.size());
        assertEquals("b", back.get(1).asText());
    }

    @Test
    void writeRecordRoundTrips() {
        record Point(int x, int y) {}
        Point back = Json.read(Json.write(new Point(3, 4)), Point.class);
        assertEquals(new Point(3, 4), back);
    }

    @Test
    void writeBooleansAndNumbers() {
        Map<String, Object> back = Json.readMap(Json.write(Map.of("t", true, "n", 42)));
        assertEquals(Boolean.TRUE, back.get("t"));
        assertEquals(42, ((Number) back.get("n")).intValue());
    }

    @Test
    void readTreeArrayLiteral() {
        JsonNode n = Json.readTree("[1,2,3]");
        assertTrue(n.isArray());
        assertEquals(3, n.size());
    }

    @Test
    void readTreeNullLiteralYieldsNullNode() {
        assertTrue(Json.readTree("null").isNull());
    }

    @Test
    void readTreeEmptyStringYieldsMissingNode() {
        assertTrue(Json.readTree("").isMissingNode());
    }

    @Test
    void readMapNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> Json.readMap(null));
    }

    @Test
    void writeMapWithNullValueRoundTrips() {
        Map<String, String> input = new java.util.HashMap<>();
        input.put("k", null);
        Map<String, Object> back = Json.readMap(Json.write(input));
        assertTrue(back.containsKey("k"));
        assertNull(back.get("k"));
    }

    @Test
    void exportBundleRoundTrips() {
        ExportBundle bundle = new ExportBundle("1.0", 9L,
                List.of(new ExportBundle.QuizExport("q1", "T", "D", true, List.of())),
                Map.of("theme", "dark"));
        ExportBundle back = Json.read(Json.write(bundle), ExportBundle.class);
        assertEquals(bundle, back);
        assertEquals("dark", back.adminSettings().get("theme"));
    }

    @Test
    void writeThenReadTreeKeepsNesting() {
        Map<String, Object> inner = Map.of("list", List.of(1, 2));
        JsonNode n = Json.readTree(Json.write(Map.of("cfg", inner)));
        assertEquals(2, n.path("cfg").path("list").size());
    }

    @Test
    void readMapDeeplyNestedTypes() {
        Map<String, Object> m = Json.readMap("{\"a\":[1,\"two\",true,null]}");
        assertEquals(4, ((List<?>) m.get("a")).size());
    }

    @Test
    void readTreeRejectsTruncatedArray() {
        assertThrows(IllegalStateException.class, () -> Json.readTree("[1,2"));
    }
}
