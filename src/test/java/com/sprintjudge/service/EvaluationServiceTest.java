package com.sprintjudge.service;

import com.sprintjudge.domain.models.Question;
import com.sprintjudge.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationServiceTest {

    private final EvaluationService service = new EvaluationService();

    private Question q(String type, String config) {
        return new Question("q1", "qz", "t", "d", type, null, 30, 100, config, 0, null);
    }

    private Double eval(String type, String config, String responseJson) {
        return service.evaluateCorrectness(q(type, config), Json.readTree(responseJson));
    }

    // ---------- MCQ family ----------

    @ParameterizedTest
    @CsvSource({"0,0,true", "1,1,true", "2,2,true", "3,3,true", "0,1,false", "3,0,false"})
    void mcqSelections(int chosen, int correct, boolean expectCorrect) {
        double score = eval("MCQ",
                Json.write(Map.of("options", List.of("A", "B", "C", "D"), "correctIndex", correct)),
                "{\"selectedIndex\":" + chosen + "}");
        assertEquals(expectCorrect ? 1.0 : 0.0, score, 0.0001);
    }

    @Test
    void mcqMissingResponseScoresZero() {
        assertEquals(0.0, service.evaluateCorrectness(
                q("MCQ", Json.write(Map.of("correctIndex", 0))), null), 0.0001);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"selectedIndex\":-5}", "{\"selectedIndex\":99}", "{}"})
    void mcqOutOfRangeOrEmptyIsZero(String response) {
        assertEquals(0.0, eval("MCQ", Json.write(Map.of("correctIndex", 1)), response), 0.0001);
    }

    @ParameterizedTest
    @CsvSource({"true,true,1.0", "false,false,1.0", "true,false,0.0", "false,true,0.0"})
    void trueFalseBothDirections(String answer, String given, String expected) {
        double score = eval("TRUE_FALSE", Json.write(Map.of("correct", Boolean.parseBoolean(answer))),
                "{\"value\":" + given + "}");
        assertEquals(Double.parseDouble(expected), score, 0.0001);
    }

    @Test
    void outputPredUsesSelectedIndex() {
        assertEquals(1.0, eval("OUTPUT_PRED", Json.write(Map.of("correctIndex", 2)), "{\"selectedIndex\":2}"), 0.0001);
        assertEquals(0.0, eval("OUTPUT_PRED", Json.write(Map.of("correctIndex", 2)), "{\"selectedIndex\":1}"), 0.0001);
    }

    @Test
    void complexityUsesSelectedIndex() {
        assertEquals(1.0, eval("COMPLEXITY", Json.write(Map.of("correctIndex", 3)), "{\"selectedIndex\":3}"), 0.0001);
    }

    // ---------- MULTIPLE_SELECT partial credit ----------

    @Test
    void multiSelectPerfect() {
        assertEquals(1.0, eval("MULTIPLE_SELECT", Json.write(Map.of("correctIndices", List.of(0, 1))),
                "{\"selectedIndices\":[0,1]}"), 0.0001);
    }

    @Test
    void multiSelectOneOfTwoMissesHalfPenalty() {
        assertEquals(0.75, eval("MULTIPLE_SELECT", Json.write(Map.of("correctIndices", List.of(0, 1))),
                "{\"selectedIndices\":[0]}"), 0.0001);
    }

    @Test
    void multiSelectExtraWrongChoiceIsPenalized() {
        // correct={0}, chosen={0,2}: intersect 1, extra 1 -> 1 - (0.5)/1 = 0.5
        assertEquals(0.5, eval("MULTIPLE_SELECT", Json.write(Map.of("correctIndices", List.of(0))),
                "{\"selectedIndices\":[0,2]}"), 0.0001);
    }

    @Test
    void multiSelectAllWrongIsZero() {
        assertEquals(0.0, eval("MULTIPLE_SELECT", Json.write(Map.of("correctIndices", List.of(0, 1))),
                "{\"selectedIndices\":[2,3]}"), 0.0001);
    }

    @Test
    void multiSelectNothingChosenIsZero() {
        assertEquals(0.0, eval("MULTIPLE_SELECT", Json.write(Map.of("correctIndices", List.of(0, 1))),
                "{\"selectedIndices\":[]}"), 0.0001);
    }

    @Test
    void multiSelectNoAnswerKeyConfiguredIsZero() {
        assertEquals(0.0, eval("MULTIPLE_SELECT", "{}", "{\"selectedIndices\":[0]}"), 0.0001);
    }

    // ---------- NUMERIC ----------

    @ParameterizedTest
    @CsvSource({"3.14,3.14,0,1.0", "42,42.001,0.01,1.0", "100,90,5,0.0", "7,7,0,1.0"})
    void numericTolerance(double answer, double value, double tol, String expected) {
        double score = eval("NUMERIC", Json.write(Map.of("answer", answer, "tolerance", tol)),
                "{\"value\":" + value + "}");
        assertEquals(Double.parseDouble(expected), score, 0.0001);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":\"abc\"}"})
    void numericGarbageInputIsZero(String response) {
        assertEquals(0.0, eval("NUMERIC", Json.write(Map.of("answer", 1)), response), 0.0001);
    }

    // ---------- FILL_BLANK ----------

    @Test
    void fillBlankExactMatch() {
        assertEquals(1.0, eval("FILL_BLANK", Json.write(Map.of("snippet", "x=___", "answer", "42")),
                "{\"text\":\"42\"}"), 0.0001);
    }

    @Test
    void fillBlankCollapsesWhitespace() {
        assertEquals(1.0, eval("FILL_BLANK", Json.write(Map.of("answer", "hello world")),
                "{\"text\":\"  hello   world  \"}"), 0.0001);
    }

    @Test
    void fillBlankCaseSensitiveByDesign() {
        assertEquals(0.0, eval("FILL_BLANK", Json.write(Map.of("answer", "True")),
                "{\"text\":\"true\"}"), 0.0001);
    }

    // ---------- CLICK_BUG ----------

    @ParameterizedTest
    @CsvSource({"0,0,1.0", "11,11,1.0", "5,6,0.0", "-1,3,0.0"})
    void clickBugLineMatching(int clicked, int bugLine, String expected) {
        double score = eval("CLICK_BUG", Json.write(Map.of("bugLine", bugLine)),
                "{\"line\":" + clicked + "}");
        assertEquals(Double.parseDouble(expected), score, 0.0001);
    }

    // ---------- DRAG_SORT ----------

    private static final String DRAG_CFG =
            Json.write(Map.of("lines", List.of(Map.of("id", "a", "text", "1"),
                    Map.of("id", "b", "text", "2"), Map.of("id", "c", "text", "3")),
                    "correctOrder", List.of("a", "b", "c")));

    @Test
    void dragSortPerfectOrder() {
        assertEquals(1.0, eval("DRAG_SORT", DRAG_CFG, "{\"order\":[\"a\",\"b\",\"c\"]}"), 0.0001);
    }

    @Test
    void dragSortOneFixedPointOfThree() {
        // [a,c,b]: only position 0 matches -> 1/3 per-position credit.
        assertEquals(1.0 / 3.0, eval("DRAG_SORT", DRAG_CFG, "{\"order\":[\"a\",\"c\",\"b\"]}"), 0.0001);
    }

    @Test
    void dragSortReversedKeepsSingleMiddleHit() {
        // [c,b,a]: only the middle element lands on its own index.
        assertEquals(1.0 / 3.0, eval("DRAG_SORT", DRAG_CFG, "{\"order\":[\"c\",\"b\",\"a\"]}"), 0.0001);
    }

    @Test
    void dragSortWrongLengthIsZero() {
        assertEquals(0.0, eval("DRAG_SORT", DRAG_CFG, "{\"order\":[\"a\",\"b\"]}"), 0.0001);
    }

    // ---------- CODE_COMPLETION / OJ passthrough ----------

    @Test
    void codeCompletionMatchesNormalized() {
        assertEquals(1.0, eval("CODE_COMPLETION", Json.write(Map.of("expected", "return x+1;")),
                "{\"code\":\" return x+1; \"}"), 0.0001);
        assertEquals(0.0, eval("CODE_COMPLETION", Json.write(Map.of("expected", "return x+1;")),
                "{\"code\":\"return x-1;\"}"), 0.0001);
    }

    @NullAndEmptySource
    @ParameterizedTest
    void nullOrEmptyResponseAlwaysZero(String ignored) {
        Question question = q("MCQ", Json.write(Map.of("correctIndex", 0)));
        assertEquals(0.0, service.evaluateCorrectness(question, Json.readTree("null")), 0.0001);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OJ_FULL", "OJ_PATCH"})
    void codingTypesPassThroughForJudgeScoring(String type) {
        assertEquals(1.0, service.evaluateCorrectness(q(type, "{}"), Json.readTree("{}")), 0.0001);
    }

    // ---------- supplementary tables ----------

    @ParameterizedTest
    @CsvSource({"0,true", "1,false", "2,false", "3,false"})
    void outputPredOnlyExactIndexScores(int chosen, String expected) {
        double score = eval("OUTPUT_PRED", Json.write(Map.of("correctIndex", 0)),
                "{\"selectedIndex\":" + chosen + "}");
        assertEquals(Boolean.parseBoolean(expected) ? 1.0 : 0.0, score, 0.0001);
    }

    @ParameterizedTest
    @CsvSource({
        "-5,-5,0,1.0",
        "-0.001,-0.001,0.01,1.0",
        "0,0,0,1.0"
    })
    void numericNegativeAndZeroAnswers(double answer, double value, double tol, String expected) {
        double score = eval("NUMERIC", Json.write(Map.of("answer", answer, "tolerance", tol)),
                "{\"value\":" + value + "}");
        assertEquals(Double.parseDouble(expected), score, 0.0001);
    }

    @Test
    void fillBlankUnicodeExactMatch() {
        assertEquals(1.0, eval("FILL_BLANK", Json.write(Map.of("answer", "José")),
                "{\"text\":\"José\"}"), 0.0001);
    }

    @Test
    void dragSortSingleElementAlwaysPerfect() {
        String cfg = Json.write(Map.of("lines", List.of(Map.of("id", "a", "text", "1")),
                "correctOrder", List.of("a")));
        assertEquals(1.0, eval("DRAG_SORT", cfg, "{\"order\":[\"a\"]}"), 0.0001);
    }

    @Test
    void clickBugNullResponseIsZero() {
        assertEquals(0.0, service.evaluateCorrectness(
                q("CLICK_BUG", Json.write(Map.of("bugLine", 2))), Json.readTree("null")), 0.0001);
    }
}
