package com.sprintjudge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionConfigValidatorTest {

    private static String cfg(String body) {
        return body;
    }

    private static void invalid(String type, String config, String fragment) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> QuestionConfigValidator.requireValid(type, config));
        assertTrue(e.getMessage().contains(fragment), e.getMessage());
    }

    @Test
    void unknownTypeRejected() {
        invalid("BOGUS", "{}", "Unknown question type");
    }

    @Test
    void blankConfigFailsShapeChecks() {
        invalid("MCQ", null, "options");
        invalid("MCQ", "  ", "options");
    }

    @Test
    void malformedConfigIsBadRequest() {
        invalid("MCQ", "{oops", "not valid JSON");
    }

    @Test
    void nonObjectConfigRejected() {
        invalid("MCQ", "[1,2]", "JSON object");
    }

    @Test
    void mcqValid() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("MCQ",
                cfg("{\"options\":[\"a\",\"b\"],\"correctIndex\":1}")));
    }

    @Test
    void mcqNeedsTwoOptions() {
        invalid("MCQ", cfg("{\"options\":[\"a\"],\"correctIndex\":0}"), "at least 2");
    }

    @Test
    void mcqIndexBounds() {
        invalid("MCQ", cfg("{\"options\":[\"a\",\"b\"],\"correctIndex\":9}"), "out of bounds");
        invalid("MCQ", cfg("{\"options\":[\"a\",\"b\"]}"), "non-negative integer");
        invalid("MCQ", cfg("{\"options\":[\"a\",\"b\"],\"correctIndex\":-1}"), "non-negative integer");
    }

    @Test
    void outputPredValidAndBounded() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("OUTPUT_PRED",
                cfg("{\"options\":[\"1\",\"2\"],\"correctIndex\":0}")));
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("OUTPUT_PRED",
                cfg("{\"correctIndex\":0}")));
        invalid("OUTPUT_PRED", cfg("{\"options\":[\"1\"],\"correctIndex\":5}"), "out of bounds");
    }

    @Test
    void complexityMirrorsMcq() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("COMPLEXITY",
                cfg("{\"options\":[\"O(1)\",\"O(n)\"],\"correctIndex\":0}")));
        invalid("COMPLEXITY", cfg("{\"options\":[\"O(1)\"],\"correctIndex\":0}"), "at least 2");
    }

    @Test
    void trueFalseNeedsBoolean() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("TRUE_FALSE",
                cfg("{\"correct\":true}")));
        invalid("TRUE_FALSE", cfg("{}"), "true or false");
        invalid("TRUE_FALSE", cfg("{\"correct\":\"yes\"}"), "true or false");
    }

    @Test
    void multiSelectValidAndBounded() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("MULTIPLE_SELECT",
                cfg("{\"options\":[\"a\",\"b\",\"c\"],\"correctIndices\":[0,2]}")));
        invalid("MULTIPLE_SELECT", cfg("{\"options\":[\"a\",\"b\"],\"correctIndices\":[]}"), "non-empty");
        invalid("MULTIPLE_SELECT", cfg("{\"options\":[\"a\",\"b\"],\"correctIndices\":[7]}"), "out of bounds");
        invalid("MULTIPLE_SELECT", cfg("{\"options\":[\"a\",\"b\"],\"correctIndices\":[-1]}"), "out of bounds");
        invalid("MULTIPLE_SELECT", cfg("{\"options\":[\"a\",\"b\"],\"correctIndices\":[\"x\"]}"), "out of bounds");
        invalid("MULTIPLE_SELECT", cfg("{\"options\":[\"a\",\"b\"]}"), "non-empty");
    }

    @Test
    void numericAnswerAndTolerance() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("NUMERIC",
                cfg("{\"answer\":42,\"tolerance\":0.5}")));
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("NUMERIC",
                cfg("{\"answer\":42}")));
        invalid("NUMERIC", cfg("{\"answer\":\"x\"}"), "must be a number");
        invalid("NUMERIC", cfg("{\"answer\":1,\"tolerance\":-1}"), "tolerance");
        invalid("NUMERIC", cfg("{\"answer\":1,\"tolerance\":\"x\"}"), "tolerance");
    }

    @Test
    void fillBlankNeedsAnswer() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("FILL_BLANK",
                cfg("{\"answer\":\"Paris\"}")));
        invalid("FILL_BLANK", cfg("{}"), "must not be blank");
        invalid("FILL_BLANK", cfg("{\"answer\":\"  \"}"), "must not be blank");
    }

    @Test
    void dragSortNeedsTwoLines() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("DRAG_SORT",
                cfg("{\"correctOrder\":[\"a\",\"b\"]}")));
        invalid("DRAG_SORT", cfg("{\"correctOrder\":[\"a\"]}"), "at least 2");
        invalid("DRAG_SORT", cfg("{}"), "at least 2");
    }

    @Test
    void clickBugLineInBounds() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("CLICK_BUG",
                cfg("{\"codeLines\":[\"a\",\"b\"],\"bugLine\":1}")));
        invalid("CLICK_BUG", cfg("{\"codeLines\":[],\"bugLine\":0}"), "non-empty array");
        invalid("CLICK_BUG", cfg("{\"codeLines\":[\"a\"],\"bugLine\":3}"), "out of bounds");
    }

    @Test
    void codeCompletionNeedsExpected() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid("CODE_COMPLETION",
                cfg("{\"expected\":\"x = 1\"}")));
        invalid("CODE_COMPLETION", cfg("{}"), "must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OJ_FULL", "OJ_PATCH"})
    void ojNeedsCases(String type) {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireValid(type,
                cfg("{\"testCases\":[{\"input\":\"1\",\"expectedOutput\":\"1\"}],\"memoryLimitMb\":256}")));
        invalid(type, cfg("{}"), "non-empty array");
        invalid(type, cfg("{\"testCases\":[{\"input\":\"1\"}]}"), "expectedOutput");
        invalid(type, cfg("{\"testCases\":[\"x\"]}"), "expectedOutput");
        invalid(type, cfg("{\"testCases\":[{\"input\":\"1\",\"expectedOutput\":\"1\"}],\"memoryLimitMb\":0}"),
                "memoryLimitMb");
        invalid(type, cfg("{\"testCases\":[{\"input\":\"1\",\"expectedOutput\":\"1\"}],\"memoryLimitMb\":\"x\"}"),
                "memoryLimitMb");
    }

    @Test
    void scriptInConfigRejected() {
        invalid("MCQ",
                cfg("{\"options\":[\"a<script>alert(1)</script>\",\"b\"],\"correctIndex\":0}"),
                "disallowed markup");
        invalid("FILL_BLANK", cfg("{\"answer\":\"javascript:alert(1)\"}"), "disallowed markup");
    }

    @Test
    void scriptScanReachesNestedArrays() {
        invalid("DRAG_SORT",
                cfg("{\"correctOrder\":[\"ok\",\"<ScRiPt>x</sCrIpT>\"]}"),
                "disallowed markup");
    }

    @Test
    void cleanTextAllowsCodePunctuation() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireCleanText("title",
                "#include <stdio.h> — what prints? (a < b)"));
    }

    @Test
    void cleanTextNullPasses() {
        assertDoesNotThrow(() -> QuestionConfigValidator.requireCleanText("title", null));
    }

    @Test
    void cleanTextRejectsVectors() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> QuestionConfigValidator.requireCleanText("description", "see JAVASCRIPT:void(0)"));
        assertTrue(e.getMessage().contains("description"));
    }
}
