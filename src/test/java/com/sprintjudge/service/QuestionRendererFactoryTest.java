package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionRendererFactoryTest {

    @Test
    void validTypePassesThrough() {
        assertTrue(QuestionRendererFactory.isValidType("MCQ"));
        assertTrue(QuestionRendererFactory.isValidType("OJ_FULL"));
        assertTrue(QuestionRendererFactory.isValidType("drag_sort"));
    }

    @Test
    void invalidTypeReturnsFalse() {
        assertFalse(QuestionRendererFactory.isValidType("FLYING_MONKEY"));
        assertFalse(QuestionRendererFactory.isValidType(""));
        assertFalse(QuestionRendererFactory.isValidType(null));
    }
}
