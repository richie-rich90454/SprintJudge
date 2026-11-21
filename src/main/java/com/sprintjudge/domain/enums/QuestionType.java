package com.sprintjudge.domain.enums;

import org.eclipse.jdt.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public final class QuestionType {

	public static final String MCQ = "MCQ";

	public static final String TRUE_FALSE = "TRUE_FALSE";

	public static final String MULTIPLE_SELECT = "MULTIPLE_SELECT";

	public static final String NUMERIC = "NUMERIC";

	public static final String OUTPUT_PRED = "OUTPUT_PRED";

	public static final String FILL_BLANK = "FILL_BLANK";

	public static final String DRAG_SORT = "DRAG_SORT";

	public static final String CLICK_BUG = "CLICK_BUG";

	public static final String CODE_COMPLETION = "CODE_COMPLETION";

	public static final String COMPLEXITY = "COMPLEXITY";

	public static final String OJ_FULL = "OJ_FULL";

	public static final String OJ_PATCH = "OJ_PATCH";

	private static final List<String> VALUES = Arrays.asList(MCQ, TRUE_FALSE, MULTIPLE_SELECT, NUMERIC, OUTPUT_PRED,
			FILL_BLANK, DRAG_SORT, CLICK_BUG, CODE_COMPLETION, COMPLEXITY, OJ_FULL, OJ_PATCH);

	private QuestionType() {
	}

	public static String from(@Nullable String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Question type is required");
		}
		String normalized = value.trim().toUpperCase();
		if (!VALUES.contains(normalized)) {
			throw new IllegalArgumentException("No enum constant " + normalized);
		}
		return normalized;
	}

	public static boolean isCoding(String type) {
		return OJ_FULL.equals(type) || OJ_PATCH.equals(type);
	}
}
