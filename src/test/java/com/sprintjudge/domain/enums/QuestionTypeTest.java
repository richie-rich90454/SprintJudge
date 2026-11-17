package com.sprintjudge.domain.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class QuestionTypeTest {

	private static final List<String> ALL = Arrays.asList(QuestionType.MCQ, QuestionType.TRUE_FALSE,
			QuestionType.MULTIPLE_SELECT, QuestionType.NUMERIC, QuestionType.OUTPUT_PRED, QuestionType.FILL_BLANK,
			QuestionType.DRAG_SORT, QuestionType.CLICK_BUG, QuestionType.CODE_COMPLETION, QuestionType.COMPLEXITY,
			QuestionType.OJ_FULL, QuestionType.OJ_PATCH);

	@Test
	void allConstantsDefined() {
		assertEquals(12, ALL.size());
		assertEquals(QuestionType.MCQ, QuestionType.from("MCQ"));
		assertEquals(QuestionType.OJ_FULL, QuestionType.from("OJ_FULL"));
		assertEquals(QuestionType.OJ_PATCH, QuestionType.from("OJ_PATCH"));
	}

	@Test
	void fromNormalizesCaseAndWhitespace() {
		assertEquals(QuestionType.MCQ, QuestionType.from("mcq"));
		assertEquals(QuestionType.OJ_FULL, QuestionType.from(" oj_full "));
		assertEquals(QuestionType.TRUE_FALSE, QuestionType.from("True_False"));
	}

	@Test
	void fromNullIsRequired() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				QuestionType.from(null);
			}
		});
	}

	@Test
	void fromBlankIsRequired() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				QuestionType.from("");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				QuestionType.from("   ");
			}
		});
	}

	@Test
	void fromUnknownThrows() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				QuestionType.from("ESSAY");
			}
		});
	}

	@Test
	void codingTypesAreCoding() {
		assertTrue(QuestionType.isCoding(QuestionType.OJ_FULL));
		assertTrue(QuestionType.isCoding(QuestionType.OJ_PATCH));
	}

	@Test
	void nonCodingTypesAreNotCoding() {
		for (String type : ALL) {
			if (!QuestionType.OJ_FULL.equals(type) && !QuestionType.OJ_PATCH.equals(type)) {
				assertFalse(QuestionType.isCoding(type), type);
			}
		}
	}

	@Test
	void everyFromValueRoundTrips() {
		for (String type : ALL) {
			assertEquals(type, QuestionType.from(type));
			assertEquals(type, QuestionType.from(type.toLowerCase()));
		}
	}
}
