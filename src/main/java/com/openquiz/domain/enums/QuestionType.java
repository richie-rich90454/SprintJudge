package com.openquiz.domain.enums;

public enum QuestionType {
    MCQ,
    TRUE_FALSE,
    MULTIPLE_SELECT,
    NUMERIC,
    OUTPUT_PRED,
    FILL_BLANK,
    DRAG_SORT,
    CLICK_BUG,
    CODE_COMPLETION,
    COMPLEXITY,
    OJ_FULL,
    OJ_PATCH;

    public static QuestionType from(String value) {
        return QuestionType.valueOf(value.trim().toUpperCase());
    }

    public boolean isCoding() {
        return this == OJ_FULL || this == OJ_PATCH;
    }
}
