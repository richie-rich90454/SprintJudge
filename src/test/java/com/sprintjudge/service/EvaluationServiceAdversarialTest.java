package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.util.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationServiceAdversarialTest {

    private final EvaluationService svc = new EvaluationService();

    private Question q(String type, String config) {
        return new Question("id", "qz", "T", "D", type, null, 30, 100, config, 0, Instant.now());
    }

    private JsonNode r(String json) {
        return Json.readTree(json);
    }

    @Test
    void mcqOutOfRangeIndexIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":1}"), r("{\"selectedIndex\":99}")));
    }

    @Test
    void mcqNegativeIndexIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":1}"), r("{\"selectedIndex\":-5}")));
    }

    @Test
    void mcqMissingSelectedIndexIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":0}"), r("{}")));
    }

    @Test
    void mcqNullSelectedIndexIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":0}"), r("{\"selectedIndex\":null}")));
    }

    @Test
    void mcqNegativeOneSentinelCollidesWithMissingResponse() {
        assertEquals(1.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":-1}"), r("{}")));
    }

    @Test
    void mcqExactMatchAtHighIndexIsOne() {
        assertEquals(1.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":7}"), r("{\"selectedIndex\":7}")));
    }

    @Test
    void mcqNullResponseIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{\"correctIndex\":0}"), null));
    }

    @Test
    void mcqEmptyConfigMatchesOnlyMinusTwo() {
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{}"), r("{\"selectedIndex\":0}")));
        assertEquals(0.0, svc.evaluateCorrectness(q("MCQ", "{}"), r("{\"selectedIndex\":-1}")));
    }

    @Test
    void outputPredExactMatchIsOne() {
        assertEquals(1.0, svc.evaluateCorrectness(q("OUTPUT_PRED", "{\"correctIndex\":2}"), r("{\"selectedIndex\":2}")));
    }

    @Test
    void outputPredWrongIndexIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("OUTPUT_PRED", "{\"correctIndex\":2}"), r("{\"selectedIndex\":0}")));
    }

    @Test
    void outputPredOutOfRangeIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("OUTPUT_PRED", "{\"correctIndex\":0}"), r("{\"selectedIndex\":42}")));
    }

    @Test
    void outputPredStringIndexCoerces() {
        assertEquals(1.0, svc.evaluateCorrectness(q("OUTPUT_PRED", "{\"correctIndex\":1}"), r("{\"selectedIndex\":\"1\"}")));
    }

    @Test
    void complexityWrongIndexIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("COMPLEXITY", "{\"correctIndex\":3}"), r("{\"selectedIndex\":1}")));
    }

    @Test
    void complexityMissingIndexIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("COMPLEXITY", "{\"correctIndex\":0}"), r("{}")));
    }

    @Test
    void trueFalseStringTrueCountsAsTrue() {
        assertEquals(1.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":true}"), r("{\"value\":\"true\"}")));
    }

    @Test
    void trueFalseUppercaseTrueFallsBackToFalse() {
        assertEquals(1.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":false}"), r("{\"value\":\"TRUE\"}")));
    }

    @Test
    void trueFalseZeroCountsAsFalse() {
        assertEquals(1.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":false}"), r("{\"value\":0}")));
    }

    @Test
    void trueFalseTwoCountsAsTrue() {
        assertEquals(1.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":true}"), r("{\"value\":2}")));
    }

    @Test
    void trueFalseYesStringCountsAsFalse() {
        assertEquals(1.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":false}"), r("{\"value\":\"yes\"}")));
    }

    @Test
    void trueFalseMissingValueKeyIsZeroEvenWhenCorrectIsFalse() {
        assertEquals(0.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":false}"), r("{\"other\":1}")));
        assertEquals(0.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":false}"), r("{}")));
    }

    @Test
    void trueFalseWrongAnswerIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("TRUE_FALSE", "{\"correct\":true}"), r("{\"value\":false}")));
    }

    @Test
    void multiEmptySelectionIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1]}"), r("{\"selectedIndices\":[]}")));
    }

    @Test
    void multiEmptyCorrectSetIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[]}"), r("{\"selectedIndices\":[0]}")));
    }

    @Test
    void multiBothEmptyIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[]}"), r("{\"selectedIndices\":[]}")));
    }

    @Test
    void multiSelectEverythingScoresZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1]}"), r("{\"selectedIndices\":[0,1,2,3]}")));
    }

    @Test
    void multiAllWrongScoresZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1]}"), r("{\"selectedIndices\":[5,6]}")));
    }

    @Test
    void multiSingleWrongAgainstSingleCorrectIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0]}"), r("{\"selectedIndices\":[9]}")));
    }

    @Test
    void multiOneMissOneExtraOnTripleScoresTwoThirds() {
        assertEquals(2.0 / 3, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1,2]}"), r("{\"selectedIndices\":[0,1,3]}")), 1e-9);
    }

    @Test
    void multiHalfOfFourScoresThreeQuarters() {
        assertEquals(0.75, svc.evaluateCorrectness(
                q("MULTIPLE_SELECT", "{\"correctIndices\":[0,1,2,3]}"), r("{\"selectedIndices\":[0,1]}")), 1e-9);
    }

    @Test
    void numericClearlyInsideTolerancePasses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":10,\"tolerance\":0.5}"), r("{\"value\":10.2}")));
    }

    @Test
    void numericClearlyOutsideToleranceFails() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":10,\"tolerance\":0.5}"), r("{\"value\":11}")));
    }

    @Test
    void numericMissingValueKeyIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("NUMERIC", "{\"answer\":5}"), r("{}")));
    }

    @Test
    void numericNonNumericStringIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":5}"), r("{\"value\":\"abc\"}")));
    }

    @Test
    void numericMissingAnswerKeyIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("NUMERIC", "{}"), r("{\"value\":5}")));
    }

    @Test
    void numericNegativeAnswerExactPasses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":-5,\"tolerance\":0}"), r("{\"value\":-5}")));
    }

    @Test
    void numericZeroAnswerZeroValuePasses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":0,\"tolerance\":0}"), r("{\"value\":0}")));
    }

    @Test
    void numericHugeMismatchFails() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":1}"), r("{\"value\":1e18}")));
    }

    @Test
    void numericFractionalToleranceInsidePasses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("NUMERIC", "{\"answer\":3.14,\"tolerance\":0.01}"), r("{\"value\":3.149}")));
    }

    @Test
    void numericNullResponseIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("NUMERIC", "{\"answer\":3}"), null));
    }

    @Test
    void fillBlankAllCapsMatchPasses() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("FILL_BLANK", "{\"answer\":\"PARIS\"}"), r("{\"text\":\"PARIS\"}")));
    }

    @Test
    void fillBlankTrailingSpacesPass() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("FILL_BLANK", "{\"answer\":\"rome\"}"), r("{\"text\":\"rome   \"}")));
    }

    @Test
    void fillBlankTabsCollapseToSpaces() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("FILL_BLANK", "{\"answer\":\"a b\"}"), r("{\"text\":\"a\\tb\"}")));
    }

    @Test
    void fillBlankWhitespaceOnlyMatchesEmptyAnswer() {
        assertEquals(1.0, svc.evaluateCorrectness(q("FILL_BLANK", "{\"answer\":\"\"}"), r("{\"text\":\"   \"}")));
    }

    @Test
    void fillBlankMissingTextKeyIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("FILL_BLANK", "{\"answer\":\"x\"}"), r("{}")));
    }

    @Test
    void fillBlankExtraInternalSpaceFails() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("FILL_BLANK", "{\"answer\":\"a b\"}"), r("{\"text\":\"a  b c\"}")));
    }

    @Test
    void dragSortSingleElementExactIsOne() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"only\"]}"), r("{\"order\":[\"only\"]}")));
    }

    @Test
    void dragSortSingleElementWrongIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"a\"]}"), r("{\"order\":[\"b\"]}")));
    }

    @Test
    void dragSortLongerResponseIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"a\",\"b\"]}"), r("{\"order\":[\"a\",\"b\",\"c\"]}")));
    }

    @Test
    void dragSortShorterResponseIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"a\",\"b\",\"c\"]}"), r("{\"order\":[\"a\",\"b\"]}")));
    }

    @Test
    void dragSortNonArrayOrderIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"a\"]}"), r("{\"order\":\"a\"}")));
    }

    @Test
    void dragSortNonArrayConfigIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":\"abc\"}"), r("{\"order\":[\"a\"]}")));
    }

    @Test
    void dragSortFullyReversedScoresZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"a\",\"b\",\"c\",\"d\"]}"),
                r("{\"order\":[\"d\",\"c\",\"b\",\"a\"]}")), 1e-9);
    }

    @Test
    void dragSortThreeOfFourScoresThreeQuarters() {
        assertEquals(0.75, svc.evaluateCorrectness(
                q("DRAG_SORT", "{\"correctOrder\":[\"a\",\"b\",\"c\",\"d\"]}"),
                r("{\"order\":[\"a\",\"b\",\"c\",\"x\"]}")), 1e-9);
    }

    @Test
    void clickBugExactMatchIsOne() {
        assertEquals(1.0, svc.evaluateCorrectness(q("CLICK_BUG", "{\"bugLine\":7}"), r("{\"line\":7}")));
    }

    @Test
    void clickBugWrongLineIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("CLICK_BUG", "{\"bugLine\":7}"), r("{\"line\":8}")));
    }

    @Test
    void clickBugNegativeLineIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(q("CLICK_BUG", "{\"bugLine\":5}"), r("{\"line\":-1}")));
    }

    @Test
    void clickBugMissingConfigIsZeroForEmptyResponse() {
        assertEquals(0.0, svc.evaluateCorrectness(q("CLICK_BUG", "{}"), r("{}")));
    }

    @Test
    void clickBugFirstLineMatchIsOne() {
        assertEquals(1.0, svc.evaluateCorrectness(q("CLICK_BUG", "{\"bugLine\":1}"), r("{\"line\":1}")));
    }

    @Test
    void codeCompletionEmptyMatchesEmpty() {
        assertEquals(1.0, svc.evaluateCorrectness(q("CODE_COMPLETION", "{\"expected\":\"\"}"), r("{\"code\":\"\"}")));
    }

    @Test
    void codeCompletionWhitespaceOnlyMatchesEmpty() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("CODE_COMPLETION", "{\"expected\":\"\"}"), r("{\"code\":\"   \\n\\t \"}")));
    }

    @Test
    void codeCompletionMissingCodeKeyIsZero() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("CODE_COMPLETION", "{\"expected\":\"x\"}"), r("{}")));
    }

    @Test
    void codeCompletionNewlinesCollapse() {
        assertEquals(1.0, svc.evaluateCorrectness(
                q("CODE_COMPLETION", "{\"expected\":\"a b\"}"), r("{\"code\":\"a\\nb\"}")));
    }

    @Test
    void codeCompletionDifferentTokensFail() {
        assertEquals(0.0, svc.evaluateCorrectness(
                q("CODE_COMPLETION", "{\"expected\":\"return 1;\"}"), r("{\"code\":\"return 2;\"}")));
    }

    @Test
    void ojFullRoutesToOneForAnyResponse() {
        assertEquals(1.0, svc.evaluateCorrectness(q("OJ_FULL", "{}"), r("{}")));
        assertEquals(1.0, svc.evaluateCorrectness(q("OJ_FULL", "{}"), r("{\"code\":\"anything\"}")));
    }

    @Test
    void ojPatchRoutesToOneForAnyResponse() {
        assertEquals(1.0, svc.evaluateCorrectness(q("OJ_PATCH", "{}"), r("{\"patch\":\"x\"}")));
    }

    @Test
    void nullConfigThrows() {
        assertThrows(Exception.class,
                () -> svc.evaluateCorrectness(q("MCQ", null), r("{}")));
    }
}
