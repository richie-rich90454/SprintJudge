package com.sprintjudge.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LiveLeaderboardTest {

    @Test
    void joinProducesRankAndTracking() {
        LiveLeaderboard lb = new LiveLeaderboard();
        long seq = lb.join("u1", "Alice");
        assertTrue(seq > 0);
        assertEquals(1, lb.size());
        assertEquals("Alice", lb.nameOf("u1"));
        assertEquals(0L, lb.scoreOf("u1"));
        assertEquals(1, lb.rankOf("u1"));
    }

    @Test
    void applyScoreUpdatesRankAndDelta() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.join("u2", "Bob");
        int rank = lb.applyScore("u1", 50);
        assertEquals(1, rank);
        assertEquals(50L, lb.scoreOf("u1"));
        assertEquals(1, lb.rankOf("u1"));
        assertEquals(2, lb.rankOf("u2"));
        lb.applyScore("u1", -20);
        assertEquals(30L, lb.scoreOf("u1"));
    }

    @Test
    void applyScoreUnknownReturnsMinusOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        assertEquals(-1, lb.applyScore("ghost", 10));
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyScoreWhenSlotMarkedAbsentReturnsMinusOne() throws Exception {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        Field playersF = LiveLeaderboard.class.getDeclaredField("players");
        playersF.setAccessible(true);
        Map<String, Object> players = (Map<String, Object>) playersF.get(lb);
        Object slot = players.get("u1");
        Field presentF = slot.getClass().getDeclaredField("present");
        presentF.setAccessible(true);
        presentF.set(slot, false);
        assertEquals(-1, lb.applyScore("u1", 5));
    }

    @Test
    void removeHandlesPresentAndAbsent() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.remove("u1");
        assertEquals(-1, lb.rankOf("u1"));
        assertEquals(0, lb.size());
        lb.remove("nope");
    }

    @Test
    void scoreAndNameOfAbsent() {
        LiveLeaderboard lb = new LiveLeaderboard();
        assertEquals(0L, lb.scoreOf("x"));
        assertNull(lb.nameOf("x"));
    }

    @Test
    void snapshotAndFullBatch() {
        LiveLeaderboard lb = new LiveLeaderboard();
        assertEquals(0, lb.snapshot().size());
        var empty = lb.fullBatch();
        assertTrue(empty.resync());
        assertTrue(empty.upserts().isEmpty());

        lb.join("u1", "Alice");
        lb.join("u2", "Bob");
        lb.applyScore("u1", 100);
        var full = lb.fullBatch();
        assertTrue(full.resync());
        assertEquals(2, full.upserts().size());
    }

    @Test
    void drainDeltasAndSeqAndPending() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.applyScore("u1", 10);
        assertEquals(1, lb.pendingDeltaCount());
        var b = lb.drainDeltas(false);
        assertFalse(b.resync());
        assertEquals(0, lb.pendingDeltaCount());
        var r = lb.drainDeltas(true);
        assertTrue(r.resync());
    }

    @Test
    void currentSeqAdvances() {
        LiveLeaderboard lb = new LiveLeaderboard();
        long before = lb.currentSeq();
        lb.join("u1", "Alice");
        assertTrue(lb.currentSeq() > before);
    }
}
