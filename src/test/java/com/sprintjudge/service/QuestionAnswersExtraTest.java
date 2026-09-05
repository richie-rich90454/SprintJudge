package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprintjudge.domain.enums.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionAnswersExtraTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode answerHeavyConfig() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("correctIndex", 2);
        raw.put("correct", true);
        raw.putArray("correctIndices").add(1).add(3);
        raw.put("answer", "secret");
        raw.put("tolerance", 0.1);
        raw.putArray("correctOrder").add("x").add("y");
        raw.put("bugLine", 9);
        raw.put("expected", "hidden-code");
        return raw;
    }

    private static Set<String> fields(JsonNode n) {
        Set<String> out = new HashSet<>();
        n.fieldNames().forEachRemaining(out::add);
        return out;
    }

    @Test
    void sanitizeNullRawReturnsNull() {
        assertNull(QuestionAnswers.sanitize(QuestionType.MCQ, null));
    }

    @Test
    void sanitizeNullNodeReturnsNullNode() {
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, mapper.nullNode());
        assertTrue(out.isNull());
    }

    @Test
    void sanitizeEmptyObjectStaysEmpty() {
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, mapper.createObjectNode());
        assertEquals(0, out.size());
    }

    @Test
    void sanitizeOjFullStripsEveryAnswerKey() {
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_FULL, answerHeavyConfig());
        for (String key : new String[]{"correctIndex", "correct", "correctIndices", "answer",
                "correctOrder", "bugLine", "expected"}) {
            assertFalse(out.has(key), key);
        }
    }

    @Test
    void sanitizeKeepsUnknownExtraKeys() {
        ObjectNode raw = answerHeavyConfig();
        raw.put("prompt", "visible?");
        raw.put("points", 5);
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, raw);
        assertEquals("visible?", out.get("prompt").asText());
        assertEquals(5, out.get("points").asInt());
    }

    @Test
    void sanitizeKeepsStarterAndLinesForCoding() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("starter", "def f():");
        raw.put("lines", "a\nb");
        raw.put("expected", "nope");
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_PATCH, raw);
        assertEquals("def f():", out.get("starter").asText());
        assertEquals("a\nb", out.get("lines").asText());
        assertFalse(out.has("expected"));
    }

    @Test
    void sanitizeCodingMixedHiddenKeepsOnlyVisibleInputs() {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases")
                .add(mapper.createObjectNode().put("input", "in1").put("expectedOutput", "o1").put("isHidden", false))
                .add(mapper.createObjectNode().put("input", "in2").put("expectedOutput", "o2").put("isHidden", true));
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_FULL, q);
        assertEquals(1, out.get("testCases").size());
        assertEquals("in1", out.get("testCases").get(0).get("input").asText());
    }

    @Test
    void sanitizeVisibleCaseKeepsItsOwnFieldsIntact() {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases")
                .add(mapper.createObjectNode().put("input", "in1").put("expectedOutput", "o1").put("isHidden", false));
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_PATCH, q);
        assertEquals("o1", out.get("testCases").get(0).get("expectedOutput").asText());
        assertFalse(out.get("testCases").get(0).get("isHidden").asBoolean());
    }

    @Test
    void sanitizeNonCodingLeavesTestCasesUntouched() {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases")
                .add(mapper.createObjectNode().put("input", "a").put("isHidden", true))
                .add(mapper.createObjectNode().put("input", "b").put("isHidden", false));
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, q);
        assertEquals(2, out.get("testCases").size());
    }

    @Test
    void sanitizeCodingWithoutTestCasesAddsNothing() {
        ObjectNode q = mapper.createObjectNode();
        q.put("starter", "x");
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_FULL, q);
        assertFalse(out.has("testCases"));
        assertEquals("x", out.get("starter").asText());
    }

    @Test
    void sanitizeNumericKeepsToleranceDropsAnswer() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("answer", "7");
        raw.put("tolerance", 0.25);
        JsonNode out = QuestionAnswers.sanitize(QuestionType.NUMERIC, raw);
        assertFalse(out.has("answer"));
        assertEquals(0.25, out.get("tolerance").asDouble());
    }

    @Test
    void sanitizeKeepsOptionsArrayForMcq() {
        ObjectNode raw = mapper.createObjectNode();
        raw.putArray("options").add("a").add("b").add("c");
        raw.put("correctIndex", 0);
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, raw);
        assertEquals(3, out.get("options").size());
        assertFalse(out.has("correctIndex"));
    }

    @Test
    void sanitizeOutputPredKeepsOptionsDropsIndex() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("code", "print(1)");
        raw.put("correctIndex", 1);
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OUTPUT_PRED, raw);
        assertEquals("print(1)", out.get("code").asText());
        assertFalse(out.has("correctIndex"));
    }

    @Test
    void sanitizeFillBlankKeepsPromptDropsAnswer() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("prompt", "The capital is ___");
        raw.put("answer", "Paris");
        JsonNode out = QuestionAnswers.sanitize(QuestionType.FILL_BLANK, raw);
        assertEquals("The capital is ___", out.get("prompt").asText());
        assertFalse(out.has("answer"));
    }

    @Test
    void sanitizeDragSortKeepsItemsDropsOrder() {
        ObjectNode raw = mapper.createObjectNode();
        raw.putArray("items").add("b").add("a");
        raw.putArray("correctOrder").add("a").add("b");
        JsonNode out = QuestionAnswers.sanitize(QuestionType.DRAG_SORT, raw);
        assertEquals(2, out.get("items").size());
        assertFalse(out.has("correctOrder"));
    }

    @Test
    void sanitizeClickBugKeepsCodeDropsBugLine() {
        ObjectNode raw = mapper.createObjectNode();
        raw.putArray("lines").add("x = 1").add("y = 2");
        raw.put("bugLine", 2);
        JsonNode out = QuestionAnswers.sanitize(QuestionType.CLICK_BUG, raw);
        assertEquals(2, out.get("lines").size());
        assertFalse(out.has("bugLine"));
    }

    @Test
    void payloadOutputPredExposesExactlyCorrectIndex() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.OUTPUT_PRED, answerHeavyConfig());
        assertEquals(Set.of("correctIndex"), fields(out));
    }

    @Test
    void payloadComplexityExposesExactlyCorrectIndex() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.COMPLEXITY, answerHeavyConfig());
        assertEquals(Set.of("correctIndex"), fields(out));
    }

    @Test
    void payloadMultipleSelectExposesExactlyCorrectIndices() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.MULTIPLE_SELECT, answerHeavyConfig());
        assertEquals(Set.of("correctIndices"), fields(out));
    }

    @Test
    void payloadFillBlankExposesExactlyAnswer() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.FILL_BLANK, answerHeavyConfig());
        assertEquals(Set.of("answer"), fields(out));
    }

    @Test
    void payloadClickBugExposesExactlyBugLine() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.CLICK_BUG, answerHeavyConfig());
        assertEquals(Set.of("bugLine"), fields(out));
        assertEquals(9, out.get("bugLine").asInt());
    }

    @Test
    void payloadOjFullIsNullEvenWithAnswerKeys() {
        assertNull(QuestionAnswers.answerPayload(QuestionType.OJ_FULL, answerHeavyConfig()));
    }

    @Test
    void payloadOjPatchIsNullEvenWithAnswerKeys() {
        assertNull(QuestionAnswers.answerPayload(QuestionType.OJ_PATCH, answerHeavyConfig()));
    }

    @Test
    void payloadNullRawIsNull() {
        assertNull(QuestionAnswers.answerPayload(QuestionType.MCQ, null));
    }

    @Test
    void payloadNullNodeIsNull() {
        assertNull(QuestionAnswers.answerPayload(QuestionType.MCQ, mapper.nullNode()));
    }

    @Test
    void payloadMissingKeysYieldEmptyObject() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.DRAG_SORT, mapper.createObjectNode());
        assertNotNull(out);
        assertEquals(0, out.size());
    }

    @Test
    void payloadFillBlankMissingAnswerYieldsEmpty() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("prompt", "only-prompt");
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.FILL_BLANK, raw);
        assertEquals(0, out.size());
        assertFalse(out.has("prompt"));
    }

    @Test
    void payloadTrueFalseValueRoundTrips() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("correct", false);
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.TRUE_FALSE, raw);
        assertEquals(Set.of("correct"), fields(out));
        assertFalse(out.get("correct").asBoolean());
    }

    @Test
    void payloadNumericAnswerOnlyWhenToleranceAbsent() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("answer", "3");
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.NUMERIC, raw);
        assertEquals(Set.of("answer"), fields(out));
    }

    @Test
    void payloadNumericToleranceOnlyWhenAnswerAbsent() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("tolerance", 0.5);
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.NUMERIC, raw);
        assertEquals(Set.of("tolerance"), fields(out));
    }

    @Test
    void payloadCodeCompletionDropsStarterKeepsExpected() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("starter", "int x =");
        raw.put("expected", "int x = 1;");
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.CODE_COMPLETION, raw);
        assertEquals(Set.of("expected"), fields(out));
    }

    @Test
    void payloadDragSortKeepsOrderValues() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.DRAG_SORT, answerHeavyConfig());
        assertEquals(2, out.get("correctOrder").size());
        assertEquals("x", out.get("correctOrder").get(0).asText());
    }

    @Test
    void payloadMultiSelectKeepsIndexValues() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.MULTIPLE_SELECT, answerHeavyConfig());
        assertEquals(2, out.get("correctIndices").size());
        assertEquals(1, out.get("correctIndices").get(0).asInt());
    }

    @Test
    void payloadDoesNotMutateSource() {
        ObjectNode raw = answerHeavyConfig();
        QuestionAnswers.answerPayload(QuestionType.MCQ, raw);
        assertTrue(raw.has("correct"));
        assertTrue(raw.has("answer"));
    }

    @Test
    void sanitizeResultIsIndependentCopy() {
        ObjectNode raw = answerHeavyConfig();
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, raw);
        ((ObjectNode) out).put("injected", true);
        assertFalse(raw.has("injected"));
    }

    @Test
    void sanitizeTrueFalseKeepsStatementDropsCorrect() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("statement", "Sky is blue");
        raw.put("correct", true);
        JsonNode out = QuestionAnswers.sanitize(QuestionType.TRUE_FALSE, raw);
        assertEquals("Sky is blue", out.get("statement").asText());
        assertFalse(out.has("correct"));
    }

    @Test
    void sanitizeComplexityKeepsSnippetDropsIndex() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("snippet", "for(;;){}");
        raw.put("correctIndex", 0);
        JsonNode out = QuestionAnswers.sanitize(QuestionType.COMPLEXITY, raw);
        assertEquals("for(;;){}", out.get("snippet").asText());
        assertFalse(out.has("correctIndex"));
    }

    @Test
    void payloadMcqValueRoundTrips() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("correctIndex", 3);
        raw.putArray("options").add("a").add("b");
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.MCQ, raw);
        assertEquals(3, out.get("correctIndex").asInt());
        assertFalse(out.has("options"));
    }

    @Test
    void sanitizeMultiSelectKeepsOptionsArray() {
        ObjectNode raw = mapper.createObjectNode();
        raw.putArray("options").add("a").add("b");
        raw.putArray("correctIndices").add(0);
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MULTIPLE_SELECT, raw);
        assertEquals(2, out.get("options").size());
        assertFalse(out.has("correctIndices"));
    }

    @Test
    void sanitizeCodingSingleVisibleSurvives() {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases").add(mapper.createObjectNode().put("input", "solo").put("isHidden", false));
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_FULL, q);
        assertEquals(1, out.get("testCases").size());
        assertEquals("solo", out.get("testCases").get(0).get("input").asText());
    }
}
