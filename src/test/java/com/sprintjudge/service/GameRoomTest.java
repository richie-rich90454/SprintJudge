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

    @Test
    void addHostAtCapacityReturnsFalse() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 1);
        assertTrue(r.addPlayer(new Player("u1", "a", 0, "s1", true)));
        assertTrue(r.isFull());
        assertFalse(r.addHost(new Player("h", "host", 0, "sh", true)));
    }

    @Test
    void addHostOccupiesSeatWithoutBoardRow() {
        GameRoom r = new GameRoom("s", "q", "pin", "LOBBY", 10);
        assertTrue(r.addHost(new Player("h", "Host", 0, "sh", true)));
        assertEquals(0, r.leaderboard().size());
        assertEquals(1, r.players().size());
        assertEquals("h", r.players().get(0).uuid());
    }

    @Test
    void addHostUnderCapacitySucceeds() {
        GameRoom r = new GameRoom("s", "q", "pin", "LOBBY", 2);
        assertTrue(r.addHost(new Player("h", "H", 0, "sh", true)));
        assertFalse(r.isFull());
    }

    @Test
    void reclaimConnectedSeatReturnsNull() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "s1", true, "tok1"));
        assertNull(r.reclaim("tok1", "s2"));
    }

    @Test
    void reclaimUnknownTokenReturnsNullWhenAllDisconnected() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "s1", false, "tok1"));
        assertNull(r.reclaim("nope", "s2"));
    }

    @Test
    void reclaimEmptyRoomReturnsNull() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertNull(r.reclaim("tok", "s"));
    }

    @Test
    void refundDecrementsAttempt() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertTrue(r.tryBeginAttempt("q1", "u1", 5));
        assertTrue(r.tryBeginAttempt("q1", "u1", 5));
        assertEquals(2, r.attemptCount("q1", "u1"));
        r.refundAttempt("q1", "u1");
        assertEquals(1, r.attemptCount("q1", "u1"));
    }

    @Test
    void refundAtZeroStaysAtZero() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertTrue(r.tryBeginAttempt("q1", "u1", 5));
        r.refundAttempt("q1", "u1");
        assertEquals(0, r.attemptCount("q1", "u1"));
        r.refundAttempt("q1", "u1");
        assertEquals(0, r.attemptCount("q1", "u1"));
    }

    @Test
    void refundMissingKeyIsNoOp() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.refundAttempt("ghost-q", "ghost-u");
        assertEquals(0, r.attemptCount("ghost-q", "ghost-u"));
    }

    @Test
    void isFullExactlyAtCapacity() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 2);
        assertFalse(r.isFull());
        r.addPlayer(new Player("u1", "a", 0, "s1", true));
        assertFalse(r.isFull());
        r.addPlayer(new Player("u2", "b", 0, "s2", true));
        assertTrue(r.isFull());
    }

    @Test
    void getPlayerReflectsLiveBoardScore() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "s1", true));
        r.applyScore("u1", 250);
        assertEquals(250, r.getPlayer("u1").score());
    }

    @Test
    void applyScoreUnknownUuidReturnsMinusOne() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertEquals(-1, r.applyScore("ghost", 10));
    }

    @Test
    void playersIncludesHostSeatOutsideBoard() {
        GameRoom r = new GameRoom("s", "q", "pin", "LOBBY", 10);
        r.addHost(new Player("h", "Host", 0, "sh", true));
        r.addPlayer(new Player("u1", "a", 0, "s1", true));
        List<Player> all = r.players();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(p -> p.uuid().equals("h")));
        assertTrue(all.stream().anyMatch(p -> p.uuid().equals("u1")));
    }

    @Test
    void softRemoveUnknownIsNoOp() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        r.addPlayer(new Player("u1", "a", 0, "s1", true));
        r.softRemove("ghost");
        assertEquals(1, r.players().size());
        assertTrue(r.getPlayer("u1").connected());
    }

    @Test
    void attemptCountMissingKeyIsZero() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertEquals(0, r.attemptCount("no-q", "no-u"));
    }

    @Test
    void streakOfUnknownIsZero() {
        GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
        assertEquals(0, r.streakOf("ghost"));
    }
}
