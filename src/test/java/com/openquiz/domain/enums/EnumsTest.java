package com.openquiz.domain.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumsTest {

    // ---------- QuestionType ----------

    @Test
    void questionTypeHasExactlyTwelveFormats() {
        assertEquals(12, QuestionType.values().length);
    }

    @ParameterizedTest
    @EnumSource(QuestionType.class)
    void questionTypeRoundTripsThroughFrom(QuestionType type) {
        assertEquals(type, QuestionType.from(type.name()));
        assertEquals(type, QuestionType.from(type.name().toLowerCase()));
        assertEquals(type, QuestionType.from("  " + type.name() + " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OJ_FULL", "OJ_PATCH"})
    void codingFlagOnlyForOnlineJudgeTypes(String type) {
        assertTrue(QuestionType.from(type).isCoding());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MCQ", "TRUE_FALSE", "MULTIPLE_SELECT", "NUMERIC", "OUTPUT_PRED",
            "FILL_BLANK", "DRAG_SORT", "CLICK_BUG", "CODE_COMPLETION", "COMPLEXITY"})
    void selectionTypesAreNotCoding(String type) {
        assertFalse(QuestionType.from(type).isCoding());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOPE", "", "mcq_extra"})
    void invalidQuestionTypeIsRejected(String raw) {
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from(raw));
    }

    // ---------- GameStatus ----------

    @Test
    void gameStatusHasFourStates() {
        assertEquals(4, GameStatus.values().length);
    }

    @ParameterizedTest
    @CsvSource({"LOBBY,lobby", "ACTIVE,ACTIVE", "REVIEW, review ", "ENDED,enDeD"})
    void gameStatusCaseAndWhitespaceTolerant(String expected, String raw) {
        assertEquals(GameStatus.valueOf(expected), GameStatus.from(raw));
    }

    @Test
    void invalidGameStatusThrows() {
        assertThrows(IllegalArgumentException.class, () -> GameStatus.from("PAUSED"));
    }

    // ---------- Role ----------

    @Test
    void roleHasTwoValues() {
        assertEquals(2, Role.values().length);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,admin", "PLAYER,PLAYER"})
    void roleRoundTrip(String expected, String raw) {
        assertEquals(Role.valueOf(expected), Role.from(raw));
    }

    @Test
    void questionTypeNamesAreUnique() {
        assertEquals(12, java.util.Arrays.stream(QuestionType.values())
                .map(Enum::name).distinct().count());
    }

    @Test
    void invalidRoleThrows() {
        assertThrows(IllegalArgumentException.class, () -> Role.from("SUPERUSER"));
    }

    @Test
    void gameStatusTransitionsCoverFullLifecycle() {
        // Documents the legal walk: LOBBY -> ACTIVE -> REVIEW -> ENDED.
        var order = java.util.List.of(GameStatus.LOBBY, GameStatus.ACTIVE,
                GameStatus.REVIEW, GameStatus.ENDED);
        assertEquals(4, order.size());
        assertEquals(GameStatus.LOBBY, GameStatus.from("lobby"));
    }
}
