package com.sprintjudge.service;

import com.sprintjudge.domain.dto.LeaderboardEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameRoomTest {

    @Test
    void addPlayerUnderCapacity() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 2);
        assertTrue(r.addPlayer(new Player("u1", "a", 0, "ss", true)));
        assertTrue(r.addPlayer(new Player("u2", "b", 0, "ss", true)));
        assertFalse(r.addPlayer(new Player("u3", "c", 0, "ss", true)));
        assertTrue(r.isFull());
    }

    @Test
    void legacyCtorDefaultsCap() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE");
        assertFalse(r.isFull());
        assertTrue(r.addPlayer(new Player("u", "n", 0, "ss", true)));
        assertEquals(500, r.capacity());
    }

    @Test
    void softRemoveKeepsBoardRow() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "ss", true));
        r.softRemove("u1");
        Player gp = r.getPlayer("u1");
        assertNotNull(gp);
        assertFalse(gp.connected());
        r.softRemove("missing");
    }

    @Test
    void hardRemoveDropsBoardRow() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "ss", true));
        r.hardRemove("u1");
        assertNull(r.getPlayer("u1"));
        r.hardRemove("missing");
    }

    @Test
    void reclaimFlow() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertNull(r.reclaim(null, "s"));
        // a connected player forces the reclaim loop's !connected() short-circuit branch
        r.addPlayer(new Player("u0", "z", 0, "ss", true, "othertok"));
        // a disconnected player with a non-matching token exercises the && false branch
        r.addPlayer(new Player("ux", "x", 0, "ss", false, "wrongtok"));
        r.addPlayer(new Player("u1", "a", 0, "old", false, "tok"));
        Player reclaimed = r.reclaim("tok", "news");
        assertNotNull(reclaimed);
        assertTrue(reclaimed.connected());
        assertEquals("news", reclaimed.sessionId());
        // player is in the players map but not the board -> exercises players() second loop
        List<Player> list = r.players();
        assertEquals(3, list.size());
        assertNull(r.reclaim("wrong", "x"));
    }

    @Test
    void playersWithOrphanBoardRow() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "ss", true));
        // board row with no matching players-map entry -> exercises players() null check
        r.board().join("ghost", "ghost");
        r.board().remove("u1");
        List<Player> list = r.players();
        assertEquals(1, list.size());
        assertEquals("u1", list.get(0).uuid());
    }

    @Test
    void getPlayerUnknown() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertNull(r.getPlayer("nope"));
    }

    @Test
    void applyScoreAndLeaderboard() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "ss", true));
        r.applyScore("u1", 100);
        List<LeaderboardEntry> lb = r.leaderboard();
        assertEquals(1, lb.size());
        assertEquals(100, lb.get(0).score());
    }

    @Test
    void attemptsAndStreaks() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertTrue(r.tryBeginAttempt("q1", "u1", 3));
        assertTrue(r.tryBeginAttempt("q1", "u1", 3));
        assertTrue(r.tryBeginAttempt("q1", "u1", 3));
        assertFalse(r.tryBeginAttempt("q1", "u1", 3));
        assertEquals(3, r.attemptCount("q1", "u1"));
        r.bumpStreak("u1");
        r.bumpStreak("u1");
        assertEquals(2, r.streakOf("u1"));
        r.resetStreak("u1");
        assertEquals(0, r.streakOf("u1"));
    }

    @Test
    void rounds() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.recordRound("u1", 50, 5);
        assertArrayEquals(new int[]{50, 5}, r.roundOf("u1"));
        assertArrayEquals(new int[]{0, 0}, r.roundOf("nope"));
        r.clearRounds();
        assertArrayEquals(new int[]{0, 0}, r.roundOf("u1"));
    }

    @Test
    void connectedCountAndLifecycle() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "ss", true));
        r.addPlayer(new Player("u2", "b", 0, "ss", false));
        assertEquals(1, r.connectedCount());
        r.touch();
        assertTrue(r.idleMs(System.currentTimeMillis() + 1000) > 0);
        assertEquals(10, r.capacity());
        r.setStatus("ENDED");
        assertEquals("ENDED", r.status());
        r.setHostUuid("h");
        assertEquals("h", r.hostUuid());
        r.setCurrentQuestionIndex(2);
        assertEquals(2, r.currentQuestionIndex());
        r.setCurrentQuestionId("q");
        assertEquals("q", r.currentQuestionId());
        r.setCurrentQuestionEndEpochMs(123L);
        assertEquals(123L, r.currentQuestionEndEpochMs());
    }
}
