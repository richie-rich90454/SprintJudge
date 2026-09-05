package com.sprintjudge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCorpusTest {

    @Test
    void writeNullYieldsNullLiteral() {
        assertEquals("null", Json.write(null));
    }

    @Test
    void readTreeNullReferenceThrows() {
        assertThrows(IllegalArgumentException.class, () -> Json.readTree(null));
    }

    @Test
    void readNullLiteralIntoMapYieldsNull() {
        assertNull(Json.read("null", Map.class));
    }

    @Test
    void readMapNullLiteralYieldsNull() {
        assertNull(Json.readMap("null"));
    }

    @Test
    void readMapEmptyObjectYieldsEmptyMap() {
        assertTrue(Json.readMap("{}").isEmpty());
    }

    @Test
    void readTreeEmptyArrayYieldsEmptyArray() {
        JsonNode n = Json.readTree("[]");
        assertTrue(n.isArray());
        assertEquals(0, n.size());
    }

    @Test
    void readTreeTruncatedObjectThrows() {
        assertThrows(IllegalStateException.class, () -> Json.readTree("{\"a\":1"));
    }

    @Test
    void readTreeBareValueThrows() {
        assertThrows(IllegalStateException.class, () -> Json.readTree("{bad"));
    }

    @Test
    void readTreeUnterminatedStringThrows() {
        assertThrows(IllegalStateException.class, () -> Json.readTree("\"abc"));
    }

    @Test
    void readMapEmptyStringThrows() {
        assertThrows(IllegalStateException.class, () -> Json.readMap(""));
    }

    @Test
    void readMapTruncatedThrows() {
        assertThrows(IllegalStateException.class, () -> Json.readMap("{\"a\":"));
    }

    @Test
    void readIntoWrongTypeThrows() {
        assertThrows(IllegalStateException.class, () -> Json.read("{\"x\":1}", Integer.class));
    }

    @Test
    void readEmptyStringIntoRecordThrows() {
        assertThrows(IllegalStateException.class, () -> Json.read("", Quiz.class));
    }

    @Test
    void questionRecordRoundTrips() {
        Question q = new Question("id1", "quiz1", "Title", "Desc", "MCQ",
                List.of("java"), 30, 100, "{\"correctIndex\":0}", 2, null);
        Question back = Json.read(Json.write(q), Question.class);
        assertEquals(q, back);
    }

    @Test
    void questionWithNullablesRoundTrips() {
        Question q = new Question("id2", "quiz1", "T", null, "NUMERIC", null, 60, 0, null, 0, null);
        Question back = Json.read(Json.write(q), Question.class);
        assertEquals(q, back);
    }

    @Test
    void quizRecordRoundTrips() {
        Quiz quiz = new Quiz("q1", "Title", "D", "admin", null, true);
        assertEquals(quiz, Json.read(Json.write(quiz), Quiz.class));
    }

    @Test
    void exportBundleWithNullSettingsRoundTrips() {
        ExportBundle bundle = new ExportBundle("1.0", 5L, List.of(), null);
        ExportBundle back = Json.read(Json.write(bundle), ExportBundle.class);
        assertEquals(bundle, back);
        assertNull(back.adminSettings());
    }

    @Test
    void exportBundleWithQuestionsRoundTrips() {
        ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport(
                "a", "MCQ", "Q", "d", 30, 100, Map.of("correctIndex", 1), List.of("java", "python"));
        ExportBundle bundle = new ExportBundle("1.0", 7L,
                List.of(new ExportBundle.QuizExport("q1", "T", "D", false, List.of(qe))),
                Map.of("k", "v"));
        ExportBundle back = Json.read(Json.write(bundle), ExportBundle.class);
        assertEquals(bundle, back);
        assertEquals(List.of("java", "python"), back.quizzes().get(0).questions().get(0).languagesAllowed());
    }

    @Test
    void unknownFieldsAreIgnoredOnRead() {
        Quiz back = Json.read("{\"id\":\"q\",\"title\":\"T\",\"zzz_unknown\":999}", Quiz.class);
        assertEquals("q", back.id());
        assertEquals("T", back.title());
    }

    @Test
    void unicodePayloadRoundTripsThroughTree() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "caf" + (char) 0x00E9 + " quiz");
        JsonNode back = Json.readTree(Json.write(payload));
        assertEquals("caf" + (char) 0x00E9 + " quiz", back.get("name").asText());
    }

    @Test
    void nestedConfigMapKeepsTypes() {
        Map<String, Object> back = Json.readMap("{\"a\":{\"n\":3,\"flag\":false},\"list\":[1,2]}");
        assertEquals(3, ((Number) ((Map<?, ?>) back.get("a")).get("n")).intValue());
        assertEquals(Boolean.FALSE, ((Map<?, ?>) back.get("a")).get("flag"));
        assertEquals(2, ((List<?>) back.get("list")).size());
    }

    @Test
    void writeMapThenReadTreeSeesAllKeys() {
        Map<String, Object> input = new HashMap<>();
        input.put("s", "v");
        input.put("n", 7);
        input.put("b", true);
        JsonNode n = Json.readTree(Json.write(input));
        assertEquals("v", n.get("s").asText());
        assertEquals(7, n.get("n").asInt());
        assertTrue(n.get("b").asBoolean());
    }

    @Test
    void escapedCharactersRoundTrip() {
        Map<String, Object> input = Map.of("q", "a\"b\\c\nd");
        Map<String, Object> back = Json.readMap(Json.write(input));
        assertEquals("a\"b\\c\nd", back.get("q"));
    }

    @Test
    void largeNumbersSurviveMapRoundTrip() {
        Map<String, Object> back = Json.readMap(Json.write(Map.of("big", 9_000_000_000L)));
        assertEquals(9_000_000_000L, ((Number) back.get("big")).longValue());
    }

    @Test
    void readTreeScalarStringLiteral() {
        assertEquals("hi", Json.readTree("\"hi\"").asText());
    }

    @Test
    void readTreeScalarNumberLiteral() {
        assertEquals(42, Json.readTree("42").asInt());
    }

    @Test
    void readTreeScalarBooleanLiteral() {
        assertTrue(Json.readTree("true").asBoolean());
        assertFalse(Json.readTree("false").asBoolean());
    }
}
