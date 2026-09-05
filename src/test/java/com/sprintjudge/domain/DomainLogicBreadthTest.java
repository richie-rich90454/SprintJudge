package com.sprintjudge.domain;

import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.service.GameRoom;
import com.sprintjudge.service.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainLogicBreadthTest {

    @Test
    void questionTypeFromAcceptsEveryNameAnyCase() {
        for (QuestionType t : QuestionType.values()) {
            assertEquals(t, QuestionType.from(t.name().toLowerCase()));
            assertEquals(t, QuestionType.from(" " + t.name() + " "));
        }
    }

    @Test
    void questionTypeFromRejectsBlankAndNull() {
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from(null));
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from(""));
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from("   "));
    }

    @Test
    void questionTypeFromRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> QuestionType.from("ESSAY"));
    }

    @Test
    void onlyOjTypesAreCoding() {
        for (QuestionType t : QuestionType.values()) {
            assertEquals(t == QuestionType.OJ_FULL || t == QuestionType.OJ_PATCH, t.isCoding());
        }
    }

    @Test
    void twelveQuestionTypesExist() {
        assertEquals(12, QuestionType.values().length);
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
        assertEquals("u-1", p.uuid());
        assertEquals("Ann", p.name());
        assertEquals(5, p.score());
        assertEquals("s", p.sessionId());
        assertFalse(p.connected());
    }

    @Test
    void playerChainedTransitions() {
        Player p = new Player("u", "Ann", 0, "s1", true, "tok");
        Player moved = p.disconnected().withSession("s2").withScore(42);
        assertTrue(moved.connected());
        assertEquals("s2", moved.sessionId());
        assertEquals(42, moved.score());
        assertEquals("tok", moved.token());
        assertTrue(p.connected());
    }

    @Test
    void teamWithScoreKeepsMembers() {
        GameRoom.Team t = new GameRoom.Team("t1", "Alpha", Set.of("u1", "u2"), 10);
        GameRoom.Team next = t.withScore(99);
        assertEquals(Set.of("u1", "u2"), next.memberUuids());
        assertEquals(99, next.score());
        assertEquals(10, t.score());
    }

    @Test
    void teamAddMemberIsImmutableAndDedupes() {
        GameRoom.Team t = new GameRoom.Team("t1", "Alpha", Set.of("u1"), 0);
        GameRoom.Team next = t.addMember("u1");
        assertEquals(1, next.memberUuids().size());
        assertTrue(t.memberUuids().contains("u1"));
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
        GameRoom.BattleMatch m = new GameRoom.BattleMatch(
                "m1", "p1", "p2", "q1", "a1", "a2", true, false, "p1");
        assertEquals("m1", m.id());
        assertEquals("p1", m.p1Uuid());
        assertEquals("p2", m.p2Uuid());
        assertEquals("q1", m.questionId());
        assertEquals("a1", m.p1Answer());
        assertEquals("a2", m.p2Answer());
        assertTrue(m.p1Correct());
        assertFalse(m.p2Correct());
        assertEquals("p1", m.winnerUuid());
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
        ExportBundle.QuestionExport qx = new ExportBundle.QuestionExport(
                "q1", "MCQ", "T", "D", 30, 100, Map.of("k", 1), List.of("java"));
        ExportBundle.QuizExport qz = new ExportBundle.QuizExport("z", "T", "D", true, List.of(qx));
        ExportBundle a = new ExportBundle("1.0", 5L, List.of(qz), Map.of("s", "v"));
        ExportBundle b = new ExportBundle("1.0", 5L, List.of(qz), Map.of("s", "v"));
        assertEquals(a, b);
        assertEquals("q1", a.quizzes().get(0).questions().get(0).id());
        assertTrue(a.quizzes().get(0).template());
    }

    @Test
    void exportBundleDistinguishesVersions() {
        ExportBundle a = new ExportBundle("1.0", 1L, List.of(), Map.of());
        ExportBundle b = new ExportBundle("2.0", 1L, List.of(), Map.of());
        assertNotEquals(a, b);
    }

    @Test
    void gameModeValues() {
        assertEquals(6, GameRoom.GameMode.values().length);
        assertEquals(GameRoom.GameMode.BATTLE, GameRoom.GameMode.valueOf("BATTLE"));
    }
}
