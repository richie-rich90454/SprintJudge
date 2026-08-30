package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.util.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationServiceTest {

    private final EvaluationService svc = new EvaluationService();

    private Question q(String type, String config) {
        return new Question("id", "qz", "T", "D", type, null, 30, 100, config, 0, Instant.now());
    }

    private JsonNode r(String json) {
        return Json.readTree(json);
    }

    @Test
    void nullResponseIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":0}"), null));
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":0}"), r("null")));
    }

    @Test
    void mcqMatchesAndMisses() {
        Question q = q("MCQ", "{\"correctIndex\":2}");
        assertEquals(1.0, svc.evaluateCorrectness(q, r("{\"selectedIndex\":2}")));
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"selectedIndex\":0}")));
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{}")));   // missing selectedIndex -> -1
    }

    @Test
    void outputPredAndComplexityShareMcqLogic() {
        assertEquals(1.0, svc.evaluateCorrectness(q("OUTPUT_PRED", "{\"correctIndex\":1}"), r("{\"selectedIndex\":1}")));
        assertEquals(0.0, svc.evaluateCorrectness(q("COMPLEXITY", "{\"correctIndex\":0}"), r("{\"selectedIndex\":3}")));
    }

    @Test
    void trueFalseRequiresPresentValue() {
        Question q = q("TRUE_FALSE", "{\"correct\":true}");
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{}")));                 // no value field
        assertEquals(1.0, svc.evaluateCorrectness(q, r("{\"value\":true}")));
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"value\":false}")));
        Question qf = q("TRUE_FALSE", "{\"correct\":false}");
        assertEquals(1.0, svc.evaluateCorrectness(qf, r("{\"value\":false}")));
    }

    @Test
    void multiSelectVariants() {
        Question q = q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1]}");
        assertEquals(0.0, svc.evaluateCorrectness(q("MULTIPLE_SELECT", "{}"), r("{}"))); // empty correct
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"selectedIndices\":[]}")));    // empty chosen
        assertEquals(1.0, svc.evaluateCorrectness(q, r("{\"selectedIndices\":[0,1]}"))); // all correct
        assertEquals(0.75, svc.evaluateCorrectness(q, r("{\"selectedIndices\":[0]}")), 1e-9); // 1 missed
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"selectedIndices\":[2,3]}")));       // all wrong (extra>intersect)
        assertEquals(0.5, svc.evaluateCorrectness(q("MULTIPLE_SELECT", "{\"correctIndices\":[0]}"),
                r("{\"selectedIndices\":[0,1]}")), 1e-9); // 1 right + 1 wrong -> 0.5
    }

    @Test
    void numericToleranceAndMissing() {
        Question q = q("NUMERIC", "{\"answer\":10,\"tolerance\":0.5}");
        assertEquals(0.0, svc.evaluateCorrectness(q("NUMERIC", "{}"), r("{\"value\":1}"))); // no answer
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{}")));                            // no value
        assertEquals(1.0, svc.evaluateCorrectness(q, r("{\"value\":10.2}")));
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"value\":9.0}")));
        assertEquals(1.0, svc.evaluateCorrectness(q("NUMERIC", "{\"answer\":5}"), r("{\"value\":5}")));
    }

    @Test
    void fillBlankNormalizesWhitespace() {
        Question q = q("FILL_BLANK", "{\"answer\":\"hello world\"}");
        assertEquals(1.0, svc.evaluateCorrectness(q, r("{\"text\":\"hello   world\"}")));
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"text\":\"nope\"}")));
    }

    @Test
    void clickBugMatchesLine() {
        Question q = q("CLICK_BUG", "{\"bugLine\":7}");
        assertEquals(1.0, svc.evaluateCorrectness(q, r("{\"line\":7}")));
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"line\":3}")));
    }

    @Test
    void dragSortScoring() {
        Question q = q("DRAG_SORT", "{\"correctOrder\":[\"a\",\"b\",\"c\"]}");
        assertEquals(0.0, svc.evaluateCorrectness(q("DRAG_SORT", "{\"correctOrder\":\"x\"}"),
                r("{\"order\":[\"a\"]}")));                                              // not arrays
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"order\":[\"a\",\"b\"]}")));    // size mismatch
        assertEquals(1.0, svc.evaluateCorrectness(q, r("{\"order\":[\"a\",\"b\",\"c\"]}")));
        assertEquals(1.0 / 3, svc.evaluateCorrectness(q, r("{\"order\":[\"c\",\"b\",\"a\"]}")), 1e-9);
    }

    @Test
    void codeCompletionMatches() {
        Question q = q("CODE_COMPLETION", "{\"expected\":\"int x = 1;\"}");
        assertEquals(1.0, svc.evaluateCorrectness(q, r("{\"code\":\"int x = 1;\"}")));
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"code\":\"int y = 2;\"}")));
    }

    @Test
    void ojTypesAlwaysPassToExecutor() {
        assertEquals(1.0, svc.evaluateCorrectness(q("OJ_FULL", "{}"), r("{\"anything\":true}")));
        assertEquals(1.0, svc.evaluateCorrectness(q("OJ_PATCH", "{}"), r("{\"anything\":true}")));
    }

    @Test
    void dragSortCorrectArrayButChosenNotArrayReturnsZero() {
        Question q = q("DRAG_SORT", "{\"correctOrder\":[\"a\",\"b\"]}");
        assertEquals(0.0, svc.evaluateCorrectness(q, r("{\"order\":\"notarray\"}")));
    }
}
