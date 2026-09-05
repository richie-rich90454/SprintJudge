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

    @Test
    void drainRefreshesStaleRanksAfterOvertake() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.join("u2", "Bob");
        lb.drainDeltas(false);
        lb.applyScore("u1", 10);
        lb.applyScore("u2", 50);
        var batch = lb.drainDeltas(false);
        assertFalse(batch.resync());
        assertEquals(2, batch.upserts().size());
        var byUuid = new java.util.HashMap<String, Integer>();
        for (var d : batch.upserts()) byUuid.put(d.uuid(), d.rank());
        assertEquals(2, (int) byUuid.get("u1"));
        assertEquals(1, (int) byUuid.get("u2"));
        assertEquals(1, lb.rankOf("u2"));
        assertEquals(2, lb.rankOf("u1"));
    }

    @Test
    void drainFallsBackToRecordedRankWhenPlayerRemoved() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.join("u2", "Bob");
        lb.applyScore("u1", 30);
        lb.remove("u1");
        var batch = lb.drainDeltas(false);
        assertFalse(batch.resync());
        var fallen = batch.upserts().stream().filter(d -> d.uuid().equals("u1")).findFirst().orElseThrow();
        assertTrue(fallen.rank() >= 1);
        assertEquals(-1, lb.rankOf("u1"));
    }

    @Test
    void drainResyncPassthroughSkipsRankRefresh() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        var batch = lb.drainDeltas(true);
        assertTrue(batch.resync());
        assertTrue(batch.upserts().isEmpty());
    }

    @Test
    void drainEmptyLedgerReturnsResyncBatch() {
        LiveLeaderboard lb = new LiveLeaderboard();
        var batch = lb.drainDeltas(false);
        assertTrue(batch.resync());
        assertTrue(batch.upserts().isEmpty());
    }

    @Test
    void drainCoalescesMultipleScoresIntoLatestRank() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.join("u2", "Bob");
        lb.join("u3", "Cara");
        lb.drainDeltas(false);
        lb.applyScore("u3", 5);
        lb.applyScore("u3", 100);
        lb.applyScore("u1", 50);
        var batch = lb.drainDeltas(false);
        var ranks = new java.util.HashMap<String, Integer>();
        for (var d : batch.upserts()) ranks.put(d.uuid(), d.rank());
        assertEquals(2, batch.upserts().size());
        assertEquals(1, (int) ranks.get("u3"));
        assertEquals(2, (int) ranks.get("u1"));
        assertEquals(1, lb.rankOf("u3"));
        assertEquals(2, lb.rankOf("u1"));
        assertEquals(3, lb.rankOf("u2"));
    }

    @Test
    void fullBatchRanksFollowScoreOrder() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.join("u2", "Bob");
        lb.applyScore("u2", 70);
        lb.applyScore("u1", 20);
        var full = lb.fullBatch();
        assertTrue(full.resync());
        assertEquals(2, full.upserts().size());
        assertEquals("u2", full.upserts().get(0).uuid());
        assertEquals(1, full.upserts().get(0).rank());
        assertEquals("u1", full.upserts().get(1).uuid());
        assertEquals(2, full.upserts().get(1).rank());
    }

    @Test
    void fullBatchDoesNotConsumePendingDeltas() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        assertEquals(1, lb.pendingDeltaCount());
        lb.fullBatch();
        assertEquals(1, lb.pendingDeltaCount());
        var drained = lb.drainDeltas(false);
        assertFalse(drained.resync());
        assertEquals(0, lb.pendingDeltaCount());
    }

    @Test
    void negativeDeltaDropsRankAtDrainTime() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.join("u2", "Bob");
        lb.drainDeltas(false);
        lb.applyScore("u1", 100);
        lb.applyScore("u1", -90);
        var batch = lb.drainDeltas(false);
        var ranks = new java.util.HashMap<String, Integer>();
        for (var d : batch.upserts()) ranks.put(d.uuid(), d.rank());
        assertEquals(1, (int) ranks.get("u1"));
        assertEquals(10L, lb.scoreOf("u1"));
    }

    @Test
    void joinAfterDrainGetsFreshSequenceRank() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "Alice");
        lb.drainDeltas(false);
        lb.join("u2", "Bob");
        var batch = lb.drainDeltas(false);
        assertEquals(1, batch.upserts().size());
        assertEquals("u2", batch.upserts().get(0).uuid());
        assertEquals(2, batch.upserts().get(0).rank());
    }
}
