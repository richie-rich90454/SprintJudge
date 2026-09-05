package com.sprintjudge.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveLeaderboardBreadthTest {

    @Test
    void joinAssignsIncreasingSequences() {
        LiveLeaderboard lb = new LiveLeaderboard();
        long first = lb.join("u1", "A");
        long second = lb.join("u2", "B");
        assertTrue(second > first);
        assertEquals(2, lb.size());
    }

    @Test
    void rejoinResetsScoreToZero() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "A");
        lb.applyScore("u1", 100);
        lb.join("u1", "A");
        assertEquals(1, lb.size());
        assertEquals(0, lb.scoreOf("u1"));
    }

    @Test
    void applyScoreUnknownAndRemovedReturnMinusOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        assertEquals(-1, lb.applyScore("ghost", 10));
        lb.join("u1", "A");
        lb.remove("u1");
        assertEquals(-1, lb.applyScore("u1", 10));
    }

    @Test
    void negativeDeltaLowersScore() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "A");
        lb.applyScore("u1", 100);
        lb.applyScore("u1", -30);
        assertEquals(70, lb.scoreOf("u1"));
    }

    @Test
    void overtakeUpdatesRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.applyScore("a", 100);
        lb.applyScore("b", 50);
        assertEquals(1, lb.rankOf("a"));
        lb.applyScore("b", 100);
        assertEquals(1, lb.rankOf("b"));
        assertEquals(2, lb.rankOf("a"));
    }

    @Test
    void removeUnknownIsNoop() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "A");
        lb.remove("ghost");
        assertEquals(1, lb.size());
        assertEquals(0, lb.scoreOf("ghost"));
        assertNull(lb.nameOf("ghost"));
        assertEquals("A", lb.nameOf("u1"));
    }

    @Test
    void drainMergesJoinsAndScoresWithFreshRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.applyScore("a", 100);
        lb.applyScore("b", 200);
        DeltaLedger.Batch batch = lb.drainDeltas(false);
        assertEquals(2, batch.upserts().size());
        for (DeltaLedger.Delta d : batch.upserts()) {
            if (d.uuid().equals("b")) {
                assertEquals(1, d.rank());
                assertEquals(200, d.score());
            } else {
                assertEquals(2, d.rank());
            }
        }
        assertEquals(0, lb.pendingDeltaCount());
    }

    @Test
    void fullBatchDoesNotConsumeLedger() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        long seq = lb.currentSeq();
        DeltaLedger.Batch full = lb.fullBatch();
        assertTrue(full.resync());
        assertEquals(1, full.upserts().size());
        assertEquals(seq, full.seq());
        assertEquals(seq, lb.currentSeq());
        assertEquals(1, lb.pendingDeltaCount());
        assertEquals(1, lb.drainDeltas(false).upserts().size());
    }

    @Test
    void snapshotOrderMatchesRankOrder() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.join("c", "C");
        lb.applyScore("c", 300);
        lb.applyScore("a", 100);
        List<RankedSkipList.Entry> snap = lb.snapshot();
        assertEquals("c", snap.get(0).uuid());
        assertEquals("a", snap.get(1).uuid());
        assertEquals("b", snap.get(2).uuid());
    }

    @Test
    void forceResyncDrainReturnsResyncBatch() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        DeltaLedger.Batch b = lb.drainDeltas(true);
        assertTrue(b.resync());
        assertEquals(1, lb.pendingDeltaCount());
    }

    @Test
    void removedPlayerRankIsMinusOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.remove("a");
        assertEquals(-1, lb.rankOf("a"));
        assertEquals(1, lb.rankOf("b"));
        assertEquals(1, lb.size());
    }

    @Test
    void applyScoreReturnsNewRank() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        assertEquals(1, lb.applyScore("b", 50));
        assertEquals(2, lb.applyScore("a", 10));
    }

    @Test
    void drainAfterRemoveKeepsRecordedRank() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "A");
        lb.remove("u1");
        var batch = lb.drainDeltas(false);
        assertEquals(1, batch.upserts().size());
        assertEquals(1, batch.upserts().get(0).rank());
    }
}
