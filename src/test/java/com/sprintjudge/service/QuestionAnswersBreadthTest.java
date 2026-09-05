package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprintjudge.domain.enums.QuestionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionAnswersBreadthTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode fullConfig() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("correctIndex", 1);
        raw.put("correct", true);
        raw.putArray("correctIndices").add(0).add(1);
        raw.put("answer", "42");
        raw.put("tolerance", 0.5);
        raw.putArray("correctOrder").add("a").add("b");
        raw.put("bugLine", 5);
        raw.put("expected", "e");
        raw.put("options", "keep-me");
        return raw;
    }

    @Test
    void sanitizeMcqStripsEveryAnswerKey() {
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, fullConfig());
        assertFalse(out.has("correctIndex"));
        assertFalse(out.has("correct"));
        assertFalse(out.has("correctIndices"));
        assertFalse(out.has("answer"));
        assertFalse(out.has("correctOrder"));
        assertFalse(out.has("bugLine"));
        assertFalse(out.has("expected"));
        assertTrue(out.has("options"));
        assertTrue(out.has("tolerance"));
    }

    @Test
    void sanitizeOutputPredAndComplexityStripCorrectIndex() {
        assertFalse(QuestionAnswers.sanitize(QuestionType.OUTPUT_PRED, fullConfig()).has("correctIndex"));
        assertFalse(QuestionAnswers.sanitize(QuestionType.COMPLEXITY, fullConfig()).has("correctIndex"));
    }

    @Test
    void sanitizeTrueFalseStripsCorrect() {
        JsonNode out = QuestionAnswers.sanitize(QuestionType.TRUE_FALSE, fullConfig());
        assertFalse(out.has("correct"));
        assertFalse(out.has("correctIndex"));
        assertTrue(out.has("options"));
    }

    @Test
    void sanitizeMultiSelectStripsCorrectIndices() {
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MULTIPLE_SELECT, fullConfig());
        assertFalse(out.has("correctIndices"));
        assertTrue(out.has("options"));
    }

    @Test
    void sanitizeNumericStripsAnswerButKeepsTolerance() {
        JsonNode out = QuestionAnswers.sanitize(QuestionType.NUMERIC, fullConfig());
        assertFalse(out.has("answer"));
        assertTrue(out.has("tolerance"));
    }

    @Test
    void sanitizeFillBlankStripsAnswer() {
        assertFalse(QuestionAnswers.sanitize(QuestionType.FILL_BLANK, fullConfig()).has("answer"));
    }

    @Test
    void sanitizeDragSortStripsCorrectOrder() {
        assertFalse(QuestionAnswers.sanitize(QuestionType.DRAG_SORT, fullConfig()).has("correctOrder"));
    }

    @Test
    void sanitizeClickBugStripsBugLine() {
        assertFalse(QuestionAnswers.sanitize(QuestionType.CLICK_BUG, fullConfig()).has("bugLine"));
    }

    @Test
    void sanitizeCodeCompletionStripsExpected() {
        assertFalse(QuestionAnswers.sanitize(QuestionType.CODE_COMPLETION, fullConfig()).has("expected"));
    }

    @Test
    void sanitizeDoesNotMutateInput() {
        ObjectNode raw = fullConfig();
        QuestionAnswers.sanitize(QuestionType.MCQ, raw);
        assertTrue(raw.has("correctIndex"));
        assertEquals(1, raw.get("correctIndex").asInt());
    }

    @Test
    void sanitizeCodingAllHiddenYieldsEmptyCases() {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases")
                .add(mapper.createObjectNode().put("input", "1").put("isHidden", true))
                .add(mapper.createObjectNode().put("input", "2").put("isHidden", true));
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_FULL, q);
        assertEquals(0, out.get("testCases").size());
    }

    @Test
    void sanitizeCodingKeepsCasesMissingHiddenFlag() {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases").add(mapper.createObjectNode().put("input", "1"));
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_PATCH, q);
        assertEquals(1, out.get("testCases").size());
    }

    @Test
    void sanitizeCodingKeepsMultipleVisible() {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases")
                .add(mapper.createObjectNode().put("input", "1").put("isHidden", false))
                .add(mapper.createObjectNode().put("input", "2").put("isHidden", false))
                .add(mapper.createObjectNode().put("input", "3").put("isHidden", true));
        assertEquals(2, QuestionAnswers.sanitize(QuestionType.OJ_FULL, q).get("testCases").size());
    }

    @Test
    void sanitizeCodingEmptyCasesStaysEmpty() {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases");
        assertEquals(0, QuestionAnswers.sanitize(QuestionType.OJ_FULL, q).get("testCases").size());
    }

    @Test
    void sanitizePreservesRenderFields() {
        ObjectNode q = mapper.createObjectNode();
        q.put("options", "a");
        q.put("lines", "l");
        q.put("starter", "s");
        q.put("code", "c");
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, q);
        assertEquals("a", out.get("options").asText());
        assertEquals("l", out.get("lines").asText());
        assertEquals("s", out.get("starter").asText());
        assertEquals("c", out.get("code").asText());
    }

    @Test
    void payloadMcqExposesOnlyCorrectIndex() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.MCQ, fullConfig());
        assertEquals(1, out.size());
        assertTrue(out.has("correctIndex"));
    }

    @Test
    void payloadTrueFalseExposesOnlyCorrect() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.TRUE_FALSE, fullConfig());
        assertEquals(1, out.size());
        assertTrue(out.has("correct"));
    }

    @Test
    void payloadNumericExposesAnswerAndTolerance() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.NUMERIC, fullConfig());
        assertEquals(2, out.size());
        assertTrue(out.has("answer"));
        assertTrue(out.has("tolerance"));
    }

    @Test
    void payloadNumericMissingToleranceExposesOnlyAnswer() {
        ObjectNode raw = mapper.createObjectNode();
        raw.put("answer", "7");
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.NUMERIC, raw);
        assertNotNull(out);
        assertTrue(out.has("answer"));
        assertFalse(out.has("tolerance"));
    }

    @Test
    void payloadDragSortExposesOnlyCorrectOrder() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.DRAG_SORT, fullConfig());
        assertEquals(1, out.size());
        assertTrue(out.has("correctOrder"));
    }

    @Test
    void payloadClickBugExposesOnlyBugLine() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.CLICK_BUG, fullConfig());
        assertEquals(5, out.get("bugLine").asInt());
    }

    @Test
    void payloadCodeCompletionExposesOnlyExpected() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.CODE_COMPLETION, fullConfig());
        assertEquals(1, out.size());
        assertTrue(out.has("expected"));
    }

    @Test
    void payloadNeverIncludesTestCases() {
        ObjectNode raw = fullConfig();
        raw.putArray("testCases").add(mapper.createObjectNode().put("input", "1"));
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.MCQ, raw);
        assertFalse(out.has("testCases"));
    }

    @Test
    void payloadNeverIncludesRenderFields() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.FILL_BLANK, fullConfig());
        assertFalse(out.has("options"));
        assertFalse(out.has("tolerance"));
    }

    @Test
    void payloadEmptyObjectYieldsEmptyReveal() {
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.MCQ, mapper.createObjectNode());
        assertNotNull(out);
        assertEquals(0, out.size());
    }
}
