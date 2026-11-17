package com.sprintjudge.domain;

import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.service.GameRoom;
import com.sprintjudge.service.Player;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class DomainLogicBreadthTest {

	private static final List<String> QUESTION_TYPES = Arrays.asList(QuestionType.MCQ, QuestionType.TRUE_FALSE,
			QuestionType.MULTIPLE_SELECT, QuestionType.NUMERIC, QuestionType.OUTPUT_PRED, QuestionType.FILL_BLANK,
			QuestionType.DRAG_SORT, QuestionType.CLICK_BUG, QuestionType.CODE_COMPLETION, QuestionType.COMPLEXITY,
			QuestionType.OJ_FULL, QuestionType.OJ_PATCH);

	@Test
	void questionTypeFromAcceptsEveryNameAnyCase() {
		for (String type : QUESTION_TYPES) {
			assertEquals(type, QuestionType.from(type.toLowerCase()));
			assertEquals(type, QuestionType.from(" " + type + " "));
		}
	}

	@Test
	void questionTypeFromRejectsBlankAndNull() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				QuestionType.from(null);
			}
		});
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
	void questionTypeFromRejectsUnknown() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				QuestionType.from("ESSAY");
			}
		});
	}

	@Test
	void onlyOjTypesAreCoding() {
		for (String type : QUESTION_TYPES) {
			assertEquals(QuestionType.OJ_FULL.equals(type) || QuestionType.OJ_PATCH.equals(type),
					QuestionType.isCoding(type));
		}
	}

	@Test
	void twelveQuestionTypesExist() {
		assertEquals(12, QUESTION_TYPES.size());
	}

	@Test
	void playerCanonicalEqualsAndHashCode() {
		Player a = new Player("u", "Ann", 10, "s", true, "tok");
		Player b = new Player("u", "Ann", 10, "s", true, "tok");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, b.withScore(11));
		assertNotEquals(a, new Player("other", "Ann", 10, "s", true, "tok"));
	}

	@Test
	void playerToStringCarriesIdentity() {
		Player p = new Player("u-1", "Ann", 5, "s", false, null);
		assertTrue(p.toString().contains("u-1"));
		assertEquals("u-1", p.getUuid());
		assertEquals("Ann", p.getName());
		assertEquals(5, p.getScore());
		assertEquals("s", p.getSessionId());
		assertFalse(p.isConnected());
	}

	@Test
	void playerChainedTransitions() {
		Player p = new Player("u", "Ann", 0, "s1", true, "tok");
		Player moved = p.disconnected().withSession("s2").withScore(42);
		assertTrue(moved.isConnected());
		assertEquals("s2", moved.getSessionId());
		assertEquals(42, moved.getScore());
		assertEquals("tok", moved.getToken());
		assertTrue(p.isConnected());
	}

	@Test
	void teamWithScoreKeepsMembers() {
		GameRoom.Team t = new GameRoom.Team("t1", "Alpha", Set.of("u1", "u2"), 10);
		GameRoom.Team next = t.withScore(99);
		assertEquals(Set.of("u1", "u2"), next.getMemberUuids());
		assertEquals(99, next.getScore());
		assertEquals(10, t.getScore());
	}

	@Test
	void teamAddMemberIsImmutableAndDedupes() {
		GameRoom.Team t = new GameRoom.Team("t1", "Alpha", Set.of("u1"), 0);
		GameRoom.Team next = t.addMember("u1");
		assertEquals(1, next.getMemberUuids().size());
		assertTrue(t.getMemberUuids().contains("u1"));
		assertEquals(t, new GameRoom.Team("t1", "Alpha", Set.of("u1"), 0));
	}

	@Test
	void teamEqualsDistinguishesScore() {
		GameRoom.Team a = new GameRoom.Team("t1", "A", Set.of(), 1);
		GameRoom.Team b = new GameRoom.Team("t1", "A", Set.of(), 2);
		assertNotEquals(a, b);
		assertTrue(a.toString().contains("t1"));
	}

	@Test
	void battleMatchAccessors() {
		GameRoom.BattleMatch m = new GameRoom.BattleMatch("m1", "p1", "p2", "q1", "a1", "a2", true, false, "p1");
		assertEquals("m1", m.getId());
		assertEquals("p1", m.getP1Uuid());
		assertEquals("p2", m.getP2Uuid());
		assertEquals("q1", m.getQuestionId());
		assertEquals("a1", m.getP1Answer());
		assertEquals("a2", m.getP2Answer());
		assertTrue(m.isP1Correct());
		assertFalse(m.isP2Correct());
		assertEquals("p1", m.getWinnerUuid());
	}

	@Test
	void battleMatchEquals() {
		GameRoom.BattleMatch a = new GameRoom.BattleMatch("m", "1", "2", "q", null, null, false, false, null);
		GameRoom.BattleMatch b = new GameRoom.BattleMatch("m", "1", "2", "q", null, null, false, false, null);
		assertEquals(a, b);
		assertNotEquals(a, new GameRoom.BattleMatch("other", "1", "2", "q", null, null, false, false, null));
	}

	@Test
	void exportBundleNestedRecordsEqual() {
		ExportBundle.QuestionExport qx = new ExportBundle.QuestionExport("q1", "MCQ", "T", "D", 30, 100, Map.of("k", 1),
				List.of("java"));
		ExportBundle.QuizExport qz = new ExportBundle.QuizExport("z", "T", "D", true, List.of(qx));
		ExportBundle a = new ExportBundle("1.0", 5L, List.of(qz), Map.of("s", "v"));
		ExportBundle b = new ExportBundle("1.0", 5L, List.of(qz), Map.of("s", "v"));
		assertEquals(a, b);
		assertEquals("q1", a.getQuizzes().get(0).getQuestions().get(0).getId());
		assertTrue(a.getQuizzes().get(0).isTemplate());
	}

	@Test
	void exportBundleDistinguishesVersions() {
		ExportBundle a = new ExportBundle("1.0", 1L, List.of(), Map.of());
		ExportBundle b = new ExportBundle("2.0", 1L, List.of(), Map.of());
		assertNotEquals(a, b);
	}

	@Test
	void gameModeValues() {
		assertEquals("BATTLE", GameRoom.GameMode.BATTLE);
		assertTrue(GameRoom.GameMode.isValid("BATTLE"));
		assertFalse(GameRoom.GameMode.isValid("NOPE"));
	}
}
