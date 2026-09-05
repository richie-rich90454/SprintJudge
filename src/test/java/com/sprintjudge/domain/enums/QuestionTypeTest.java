package com.sprintjudge.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTypeTest {

    @Test
    void allConstantsDefined() {
        assertEquals(12, QuestionType.values().length);
        assertSame(QuestionType.MCQ, QuestionType.valueOf("MCQ"));
        assertSame(QuestionType.OJ_FULL, QuestionType.valueOf("OJ_FULL"));
        assertSame(QuestionType.OJ_PATCH, QuestionType.valueOf("OJ_PATCH"));
    }

    @Test
    void fromNormalizesCaseAndWhitespace() {
        assertSame(QuestionType.MCQ, QuestionType.from("mcq"));
        assertSame(QuestionType.OJ_FULL, QuestionType.from(" oj_full "));
        assertSame(QuestionType.TRUE_FALSE, QuestionType.from("True_False"));
    }

    @Test
    void fromNullIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from(null));
    }

    @Test
    void fromBlankIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from(""));
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from("   "));
    }

    @Test
    void fromUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from("ESSAY"));
    }

    @Test
    void codingTypesAreCoding() {
        assertTrue(QuestionType.OJ_FULL.isCoding());
        assertTrue(QuestionType.OJ_PATCH.isCoding());
    }

    @Test
    void nonCodingTypesAreNotCoding() {
        assertFalse(QuestionType.MCQ.isCoding());
        assertFalse(QuestionType.TRUE_FALSE.isCoding());
        assertFalse(QuestionType.MULTIPLE_SELECT.isCoding());
        assertFalse(QuestionType.NUMERIC.isCoding());
        assertFalse(QuestionType.OUTPUT_PRED.isCoding());
        assertFalse(QuestionType.FILL_BLANK.isCoding());
        assertFalse(QuestionType.DRAG_SORT.isCoding());
        assertFalse(QuestionType.CLICK_BUG.isCoding());
        assertFalse(QuestionType.CODE_COMPLETION.isCoding());
        assertFalse(QuestionType.COMPLEXITY.isCoding());
    }

    @Test
    void everyFromValueRoundTrips() {
        for (QuestionType t : QuestionType.values()) {
            assertSame(t, QuestionType.from(t.name()));
            assertSame(t, QuestionType.from(t.name().toLowerCase()));
        }
    }
}
