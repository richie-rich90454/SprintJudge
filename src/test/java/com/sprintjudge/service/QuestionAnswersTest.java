package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprintjudge.domain.enums.QuestionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestionAnswersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sanitizeNullReturnsNull() {
        assertNull(QuestionAnswers.sanitize(QuestionType.MCQ, null));
    }

    @Test
    void sanitizeNullNodeReturnsNode() {
        JsonNode n = mapper.nullNode();
        assertSame(n, QuestionAnswers.sanitize(QuestionType.MCQ, n));
    }

    @Test
    void sanitizeStripsAnswerKeys() throws Exception {
        ObjectNode q = mapper.createObjectNode();
        q.put("correctIndex", 2);
        q.put("correct", true);
        q.put("correctIndices", "1,2");
        q.put("answer", "42");
        q.put("correctOrder", "3,1,2");
        q.put("bugLine", 4);
        q.put("expected", "x");
        q.put("options", "a,b,c");
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, q);
        assertFalse(out.has("correctIndex"));
        assertFalse(out.has("correct"));
        assertFalse(out.has("correctIndices"));
        assertFalse(out.has("answer"));
        assertFalse(out.has("correctOrder"));
        assertFalse(out.has("bugLine"));
        assertFalse(out.has("expected"));
        assertTrue(out.has("options"));
    }

    @Test
    void sanitizeCodingKeepsVisibleDropsHidden() throws Exception {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases")
                .add(mapper.createObjectNode().put("input", "1").put("expectedOutput", "2").put("isHidden", false))
                .add(mapper.createObjectNode().put("input", "3").put("expectedOutput", "4").put("isHidden", true));
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_FULL, q);
        JsonNode tcs = out.get("testCases");
        assertEquals(1, tcs.size());
        assertFalse(tcs.get(0).path("isHidden").asBoolean(false));
    }

    @Test
    void sanitizeCodingWithoutTestCasesKey() throws Exception {
        ObjectNode q = mapper.createObjectNode();
        q.put("options", "x");
        JsonNode out = QuestionAnswers.sanitize(QuestionType.OJ_PATCH, q);
        assertFalse(out.has("testCases"));
        assertTrue(out.has("options"));
    }

    @Test
    void sanitizeNonCodingLeavesTestCasesUntouched() throws Exception {
        ObjectNode q = mapper.createObjectNode();
        q.putArray("testCases").add(mapper.createObjectNode().put("isHidden", true));
        JsonNode out = QuestionAnswers.sanitize(QuestionType.MCQ, q);
        assertEquals(1, out.get("testCases").size());
    }

    @Test
    void answerPayloadNullReturnsNull() {
        assertNull(QuestionAnswers.answerPayload(QuestionType.MCQ, null));
    }

    @Test
    void answerPayloadNullNodeReturnsNull() {
        assertNull(QuestionAnswers.answerPayload(QuestionType.MCQ, mapper.nullNode()));
    }

    @Test
    void answerPayloadCodingReturnsNull() {
        assertNull(QuestionAnswers.answerPayload(QuestionType.OJ_FULL, mapper.createObjectNode()));
        assertNull(QuestionAnswers.answerPayload(QuestionType.OJ_PATCH, mapper.createObjectNode()));
    }

    @Test
    void answerPayloadPerTypeWithAllKeys() throws Exception {
        for (QuestionType t : new QuestionType[]{QuestionType.MCQ, QuestionType.OUTPUT_PRED, QuestionType.COMPLEXITY,
                QuestionType.TRUE_FALSE, QuestionType.MULTIPLE_SELECT, QuestionType.NUMERIC,
                QuestionType.FILL_BLANK, QuestionType.DRAG_SORT, QuestionType.CLICK_BUG, QuestionType.CODE_COMPLETION}) {
            ObjectNode raw = mapper.createObjectNode();
            raw.put("correctIndex", 1);
            raw.put("correct", true);
            raw.put("correctIndices", "1,2");
            raw.put("answer", "42");
            raw.put("tolerance", 0.1);
            raw.put("correctOrder", "3,2,1");
            raw.put("bugLine", 5);
            raw.put("expected", "e");
            JsonNode out = QuestionAnswers.answerPayload(t, raw);
            assertNotNull(out, () -> "null payload for " + t);
        }
    }

    @Test
    void answerPayloadMissingKeysSkipsCopy() throws Exception {
        ObjectNode raw = mapper.createObjectNode(); // no answer/tolerance for NUMERIC
        JsonNode out = QuestionAnswers.answerPayload(QuestionType.NUMERIC, raw);
        assertNotNull(out);
        assertFalse(out.has("answer"));
    }
}
