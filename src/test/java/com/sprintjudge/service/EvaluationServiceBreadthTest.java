package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.util.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationServiceBreadthTest {

    private final EvaluationService svc = new EvaluationService();

    private Question q(String type, String config) {
        return new Question("id", "qz", "T", "D", type, null, 30, 100, config, 0, Instant.now());
    }

    private JsonNode r(String json) {
        return Json.readTree(json);
    }

    @Test
    void mcqTypeNameIsCaseInsensitive() {
        assertEquals(1.0, svc.evaluateCorrectness(q("mcq", "{\"correctIndex\":0}"), r("{\"selectedIndex\":0}")));
    }

    @Test
    void mcqTypeNameIgnoresSurroundingWhitespace() {
        assertEquals(1.0, svc.evaluateCorrectness(q(" MCQ ", "{\"correctIndex\":0}"), r("{\"selectedIndex\":0}")));
    }

    @Test
    void mcqMissingConfigSentinelOnlyMatchesMinusTwo() {
        Question noConfig = q("MCQ", "{}");
        assertEquals(0.0, svc.evaluateCorrectness(noConfig, r("{}")));
        assertEquals(1.0, svc.evaluateCorrectness(noConfig, r("{\"selectedIndex\":-2}")));
    }

    @Test
    void mcqStringSelectedIndexParses() {
        assertEquals(1.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":2}"), r("{\"selectedIndex\":\"2\"}")));
    }

    @Test
    void outputPredMissingAnswerIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("OUTPUT_PRED", "{\"correctIndex\":1}"), r("{}")));
    }

    @Test
    void complexityCorrectMatchIsOne() {
        assertEquals(1.0, svc.evaluateCorrectness(q("COMPLEXITY", "{\"correctIndex\":3}"), r("{\"selectedIndex\":3}")));
    }

    @Test
    void trueFalseMissingCorrectDefaultsToFalse() {
        assertEquals(1.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{}"), r("{\"value\":false}")));
        assertEquals(0.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{}"), r("{\"value\":true}")));
    }

    @Test
    void trueFalseNullValueCountsAsFalse() {
        assertEquals(1.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":false}"), r("{\"value\":null}")));
        assertEquals(0.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":true}"), r("{\"value\":null}")));
    }

    @Test
    void trueFalseNumericOneCountsAsTrue() {
        assertEquals(1.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":true}"), r("{\"value\":1}")));
    }

    @Test
    void multiSingleCorrectExactIsOne() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[2]}"), r("{\"selectedIndices\":[2]}")));
    }

    @Test
    void multiOneOfThreeScoresTwoThirds() {
        assertEquals(2.0 / 3, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1,2]}"), r("{\"selectedIndices\":[0]}")), 1e-9);
    }

    @Test
    void multiTwoOfThreeScoresFiveSixths() {
        assertEquals(5.0 / 6, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1,2]}"), r("{\"selectedIndices\":[0,1]}")), 1e-9);
    }

    @Test
    void multiSingleExtraBelowIntersectKeepsPartialCredit() {
        assertEquals(0.75, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1]}"), r("{\"selectedIndices\":[0,1,2]}")), 1e-9);
    }

    @Test
    void multiSingleExtraOnTripleKeepsPartialCredit() {
        assertEquals(1.0 - 0.5 / 3, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1,2]}"), r("{\"selectedIndices\":[0,1,2,3]}")), 1e-9);
    }

    @Test
    void multiDuplicateSelectionsAreDeduped() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1]}"), r("{\"selectedIndices\":[0,0,1,1]}")));
    }

    @Test
    void multiDuplicateConfigEntriesAreDeduped() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,0,1]}"), r("{\"selectedIndices\":[0,1]}")));
    }

    @Test
    void multiMissingSelectedIndicesIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1]}"), r("{\"other\":1}")));
    }

    @Test
    void numericUpperToleranceEdgePasses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":10,\"tolerance\":0.5}"), r("{\"value\":10.5}")));
    }

    @Test
    void numericJustOutsideToleranceFails() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":10,\"tolerance\":0.5}"), r("{\"value\":10.500001}")));
    }

    @Test
    void numericLowerToleranceEdgePasses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":10,\"tolerance\":0.5}"), r("{\"value\":9.5}")));
    }

    @Test
    void numericNegativeToleranceRejectsEvenExact() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":10,\"tolerance\":-0.5}"), r("{\"value\":10}")));
    }

    @Test
    void numericStringValueParses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":10,\"tolerance\":0.5}"), r("{\"value\":\"10\"}")));
    }

    @Test
    void numericStringAnswerParses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":\"10\",\"tolerance\":0}"), r("{\"value\":10}")));
    }

    @Test
    void numericNullValueIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":10}"), r("{\"value\":null}")));
    }

    @Test
    void numericMissingToleranceRequiresExact() {
        assertEquals(1.0, svc.evaluateCorrectness(q("NUMERIC", "{\"answer\":5}"), r("{\"value\":5}")));
        assertEquals(0.0, svc.evaluateCorrectness(q("NUMERIC", "{\"answer\":5}"), r("{\"value\":5.0001}")));
    }

    @Test
    void fillBlankIsCaseSensitive() {
        assertEquals(0.0, svc.evaluateCorrectness(q("FILL_BLANK", "{\"answer\":\"Hello\"}"), r("{\"text\":\"hello\"}")));
    }

    @Test
    void fillBlankTrimsAndCollapses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("FILL_BLANK", "{\"answer\":\"hello world\"}"), r("{\"text\":\"  hello   world  \"}")));
    }

    @Test
    void fillBlankNewlinesCollapseToSpaces() {
        assertEquals(1.0, svc.evaluateCorrectness(q("FILL_BLANK", "{\"answer\":\"a b\"}"), r("{\"text\":\"a\\nb\"}")));
    }

    @Test
    void fillBlankBothEmptyCountsAsMatch() {
        assertEquals(1.0, svc.evaluateCorrectness(q("FILL_BLANK", "{\"answer\":\"\"}"), r("{\"text\":\"\"}")));
    }

    @Test
    void clickBugMissingLineIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("CLICK_BUG", "{\"bugLine\":7}"), r("{}")));
    }

    @Test
    void clickBugStringLineParses() {
        assertEquals(1.0, svc.evaluateCorrectness(q("CLICK_BUG", "{\"bugLine\":7}"), r("{\"line\":\"7\"}")));
    }

    @Test
    void dragSortNumericOrdersCompareByText() {
        Question quiz = q("DRAG_SORT", "{\"correctOrder\":[1,2,3]}");
        assertEquals(1.0, svc.evaluateCorrectness(quiz, r("{\"order\":[1,2,3]}")));
        assertEquals(1.0, svc.evaluateCorrectness(quiz, r("{\"order\":[\"1\",\"2\",\"3\"]}")));
    }

    @Test
    void dragSortHalfPositionsScoresHalf() {
        assertEquals(0.5, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"a\",\"b\",\"c\",\"d\"]}"),
                r("{\"order\":[\"a\",\"b\",\"d\",\"c\"]}")), 1e-9);
    }

    @Test
    void dragSortBothEmptyIsNaN() {
        assertTrue(Double.isNaN(svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[]}"), r("{\"order\":[]}"))));
    }

    @Test
    void dragSortMissingOrderKeyIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"a\"]}"), r("{}")));
    }

    @Test
    void codeCompletionCollapsesWhitespace() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("CODE_COMPLETION", "{\"expected\":\"int x = 1;\"}"), r("{\"code\":\"int   x = 1;\"}")));
    }

    @Test
    void codeCompletionSpacingDifferenceFails() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("CODE_COMPLETION", "{\"expected\":\"int x=1;\"}"), r("{\"code\":\"int x = 1;\"}")));
    }

    @Test
    void ojNullResponseIsZeroBeforeExecutorBranch() {
        assertEquals(0.0, svc.evaluateCorrectness(q("OJ_FULL", "{}"), null));
        assertEquals(0.0, svc.evaluateCorrectness(q("OJ_PATCH", "{}"), r("null")));
    }

    @Test
    void unknownQuestionTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> svc.evaluateCorrectness(q("BOGUS", "{}"), r("{}")));
    }

    @Test
    void blankQuestionTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> svc.evaluateCorrectness(q("  ", "{}"), r("{}")));
    }
}
