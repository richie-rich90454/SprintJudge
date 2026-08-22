package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In-memory room aggregate: scoring accumulation, ranking, host slot. */
class GameRoomTest {

    private GameRoom room() {
        return new GameRoom("s1", "qz", "123456", "LOBBY");
    }

    @Test
    void newRoomStartsEmptyInLobby() {
        GameRoom r = room();
        assertEquals("LOBBY", r.status());
        assertEquals(0, r.players().size());
        assertNull(r.hostUuid());
        assertEquals(0, r.currentQuestionIndex());
    }

    @Test
    void applyScoreAccumulatesAcrossSubmissions() {
        GameRoom r = room();
        var p = new Player("u1", "A", 0, "sa", true);
        r.addPlayer(p);
        r.applyScore("u1", 100);
        r.applyScore("u1", 250);
        assertEquals(350, r.getPlayer("u1").score());
    }

    @Test
    void applyScoreForUnknownPlayerIsHarmless() {
        room().applyScore("ghost", 100);
    }

    @Test
    void leaderboardSortsDescendingWithSequentialRanks() {
        GameRoom r = room();
        r.addPlayer(new Player("a", "Ann", 0, "sa", true));
        r.addPlayer(new Player("b", "Bob", 0, "sb", true));
        r.addPlayer(new Player("c", "Cid", 0, "sc", true));
        r.applyScore("b", 300);
        r.applyScore("c", 500);
        var board = r.leaderboard();
        assertEquals("Cid", board.get(0).name());
        assertEquals(1, board.get(0).rank());
        assertEquals("Bob", board.get(1).name());
        assertEquals(2, board.get(1).rank());
        assertEquals("Ann", board.get(2).name());
        assertEquals(3, board.get(2).rank());
        assertEquals(0, board.get(2).score());
    }

    @Test
    void leaderboardTiesKeepStableInsertionOrder() {
        GameRoom r = room();
        r.addPlayer(new Player("x", "X", 50, "sx", true));
        r.addPlayer(new Player("y", "Y", 50, "sy", true));
        var board = r.leaderboard();
        assertEquals("X", board.get(0).name());
        assertEquals(1, board.get(0).rank());
        assertEquals("Y", board.get(1).name());
        assertEquals(2, board.get(1).rank());
    }

    @Test
    void hostSlotOccupancyEnforcedByHolder() {
        GameRoom r = room();
        r.setHostUuid("host-uuid");
        assertEquals("host-uuid", r.hostUuid());
    }

    @Test
    void removePlayerDropsFromStateAndLeaderboard() {
        GameRoom r = room();
        r.addPlayer(new Player("z", "Zed", 10, "sz", true));
        r.removePlayer("z");
        assertEquals(0, r.players().size());
        assertEquals(0, r.leaderboard().size());
    }

    @Test
    void statusTransitionsAreRecorded() {
        GameRoom r = room();
        r.setStatus("ACTIVE");
        assertEquals("ACTIVE", r.status());
        r.setStatus("REVIEW");
        assertEquals("REVIEW", r.status());
    }

    @Test
    void timerDeadlineIsMutable() {
        GameRoom r = room();
        r.setCurrentQuestionEndEpochMs(123456789L);
        assertEquals(123456789L, r.currentQuestionEndEpochMs());
    }

    @Test
    void duplicateJoinReplacesSessionForSameUuid() {
        GameRoom r = room();
        r.addPlayer(new Player("u", "U", 5, "old-sess", false));
        r.addPlayer(new Player("u", "U", 5, "new-sess", true));
        assertEquals("new-sess", r.getPlayer("u").sessionId());
        assertTrue(r.getPlayer("u").connected());
        assertEquals(1, r.players().size());
    }

    @Test
    void playersSnapshotIsDefensiveCopy() {
        GameRoom r = room();
        r.addPlayer(new Player("a", "A", 0, "s", true));
        var snapshot = r.players();
        snapshot.clear();
        assertEquals(1, r.players().size());
    }

    @Test
    void playerRecordTransformersPreserveIdentity() {
        Player p = new Player("id", "N", 10, "s1", true);
        assertEquals("id", p.withScore(20).uuid());
        assertEquals(20, p.withScore(20).score());
        assertEquals("s2", p.withSession("s2").sessionId());
        assertTrue(p.disconnected().connected() == false);
        assertEquals(10, p.score());   // original untouched
    }
}
