package com.sprintjudge.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardSagaTest {

    private static void assertBoardConsistent(RankedSkipList sl) {
        List<RankedSkipList.Entry> snap = sl.snapshot();
        assertEquals(sl.size(), snap.size());
        Set<String> seen = new HashSet<>();
        for (int r = 1; r <= snap.size(); r++) {
            RankedSkipList.Entry e = sl.at(r);
            assertNotNull(e);
            assertTrue(seen.add(e.uuid()), "dup " + e.uuid());
            assertEquals(r, sl.rankOf(e.uuid()));
        }
        for (int i = 1; i < snap.size(); i++) {
            RankedSkipList.Entry prev = snap.get(i - 1);
            RankedSkipList.Entry cur = snap.get(i);
            assertTrue(prev.score() > cur.score()
                    || (prev.score() == cur.score() && prev.joinSeq() < cur.joinSeq()));
        }
    }

    @Test
    void scriptedEightPlayerTournamentEndsInScoreOrder() {
        RankedSkipList sl = new RankedSkipList();
        long[] scores = {50, 200, 150, 200, 0, 175, 150, 300};
        for (int i = 0; i < scores.length; i++) {
            sl.upsert("p" + i, "P" + i, scores[i], i + 1, Long.MIN_VALUE);
        }
        assertEquals(8, sl.size());
        assertEquals("p7", sl.at(1).uuid());
        assertEquals(300, sl.at(1).score());
        assertEquals("p4", sl.at(8).uuid());
        assertBoardConsistent(sl);
    }

    @Test
    void tiedPairsBreakByJoinOrderAcrossBoard() {
        RankedSkipList sl = new RankedSkipList();
        long[] scores = {50, 50, 30, 30, 10, 10};
        for (int i = 0; i < scores.length; i++) {
            sl.upsert("t" + i, "T" + i, scores[i], i + 1, Long.MIN_VALUE);
        }
        for (int r = 1; r <= 6; r++) {
            assertEquals("t" + (r - 1), sl.at(r).uuid());
            assertEquals(r, sl.rankOf("t" + (r - 1)));
        }
    }

    @Test
    void massRemovalInterleavedWithInsertsStaysConsistent() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 30; i++) sl.upsert("u" + i, "N" + i, i * 10L, i, Long.MIN_VALUE);
        for (int i = 2; i <= 30; i += 2) assertTrue(sl.remove("u" + i, i * 10L, i));
        assertEquals(15, sl.size());
        for (int i = 31; i <= 40; i++) sl.upsert("u" + i, "N" + i, i * 10L, i, Long.MIN_VALUE);
        assertEquals(25, sl.size());
        assertEquals("u40", sl.at(1).uuid());
        assertBoardConsistent(sl);
    }

    @Test
    void reinsertWithNewScoreMovesToHead() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 5; i++) sl.upsert("u" + i, "N", i * 10L, i, Long.MIN_VALUE);
        assertEquals(1, sl.upsert("u1", "N", 999L, 1, 10L));
        assertEquals("u1", sl.at(1).uuid());
        assertEquals(5, sl.size());
        assertBoardConsistent(sl);
    }

    @Test
    void reinsertMiddlePlayerWithLowerScoreSinks() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 5; i++) sl.upsert("u" + i, "N", i * 10L, i, Long.MIN_VALUE);
        sl.upsert("u5", "N", 5L, 5, 50L);
        assertEquals("u5", sl.at(5).uuid());
        assertEquals(5, sl.rankOf("u5"));
        assertBoardConsistent(sl);
    }

    @Test
    void alternatingBoostDuelKeepsExactRanks() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 0, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 0, 2, Long.MIN_VALUE);
        long sa = 0;
        long sb = 0;
        for (int round = 0; round < 10; round++) {
            long prevA = sa;
            sa = sb + 10;
            assertEquals(1, sl.upsert("a", "A", sa, 1, prevA));
            long prevB = sb;
            sb = sa + 10;
            assertEquals(1, sl.upsert("b", "B", sb, 2, prevB));
        }
        assertEquals("b", sl.at(1).uuid());
        assertEquals(1, sl.rankOf("b"));
        assertEquals(2, sl.rankOf("a"));
        assertBoardConsistent(sl);
    }

    @Test
    void removeHeadRepeatedlyPromotesInOrder() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 6; i++) sl.upsert("u" + i, "N", i * 10L, i, Long.MIN_VALUE);
        assertTrue(sl.remove("u6", 60, 6));
        assertEquals("u5", sl.at(1).uuid());
        assertTrue(sl.remove("u5", 50, 5));
        assertEquals("u4", sl.at(1).uuid());
        assertEquals(4, sl.size());
        assertBoardConsistent(sl);
    }

    @Test
    void removeMiddleKeepsNeighborsAdjacent() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 5; i++) sl.upsert("u" + i, "N", i * 10L, i, Long.MIN_VALUE);
        assertTrue(sl.remove("u3", 30, 3));
        assertEquals("u4", sl.at(2).uuid());
        assertEquals("u2", sl.at(3).uuid());
        assertBoardConsistent(sl);
    }

    @Test
    void atZeroAndNegativeRankIsNull() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 5, 1, Long.MIN_VALUE);
        assertNull(sl.at(0));
        assertNull(sl.at(-1));
        assertNull(sl.at(Integer.MIN_VALUE));
    }

    @Test
    void atBeyondSizeIsNull() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 5, 1, Long.MIN_VALUE);
        assertNull(sl.at(2));
        assertNull(sl.at(1000));
    }

    @Test
    void rankOfUnknownIsMinusOne() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 5, 1, Long.MIN_VALUE);
        assertEquals(-1, sl.rankOf("ghost"));
        assertEquals(-1, new RankedSkipList().rankOf("ghost"));
    }

    @Test
    void removeByIdentityUnknownKeepsBoard() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 5, 1, Long.MIN_VALUE);
        assertFalse(sl.removeByIdentity("ghost"));
        assertEquals(1, sl.size());
        assertBoardConsistent(sl);
    }

    @Test
    void negativeScoresRankBelowZero() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("pos", "P", 10, 1, Long.MIN_VALUE);
        sl.upsert("zero", "Z", 0, 2, Long.MIN_VALUE);
        sl.upsert("neg", "N", -10, 3, Long.MIN_VALUE);
        assertEquals("pos", sl.at(1).uuid());
        assertEquals("zero", sl.at(2).uuid());
        assertEquals("neg", sl.at(3).uuid());
        assertBoardConsistent(sl);
    }

    @Test
    void renameViaUpsertKeepsPositionAndScore() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "Old", 40, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 50, 2, Long.MIN_VALUE);
        sl.upsert("a", "New", 40, 1, 40L);
        assertEquals("New", sl.snapshot().stream()
                .filter(e -> e.uuid().equals("a")).findFirst().orElseThrow().name());
        assertEquals(2, sl.rankOf("a"));
        assertBoardConsistent(sl);
    }

    @Test
    void ledgerRapidRecordsPreserveInsertionOrder() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 50; i++) l.record("u" + i, "N" + i, i, i + 1);
        List<DeltaLedger.Delta> out = l.drain(false).upserts();
        assertEquals(50, out.size());
        for (int i = 0; i < 50; i++) assertEquals("u" + i, out.get(i).uuid());
        assertEquals(0, l.pendingCount());
    }

    @Test
    void ledgerInterleavedUpdatesCoalesceToLatestPerPlayer() {
        DeltaLedger l = new DeltaLedger();
        l.record("a", "A", 1, 1);
        l.record("b", "B", 1, 2);
        l.record("a", "A", 2, 1);
        l.record("c", "C", 1, 3);
        l.record("b", "B", 5, 1);
        List<DeltaLedger.Delta> out = l.drain(false).upserts();
        assertEquals(3, out.size());
        assertEquals("a", out.get(0).uuid());
        assertEquals(2, out.get(0).score());
        assertEquals("b", out.get(1).uuid());
        assertEquals(5, out.get(1).score());
        assertEquals(5, l.currentSeq());
    }

    @Test
    void ledgerSeqMonotonicAcrossManyDrains() {
        DeltaLedger l = new DeltaLedger();
        long prev = -1;
        for (int i = 0; i < 20; i++) {
            l.record("u" + i, "N", i, 1);
            long seq = l.drain(false).seq();
            assertTrue(seq > prev);
            prev = seq;
        }
        assertEquals(20, l.currentSeq());
        assertEquals(0, l.pendingCount());
    }

    @Test
    void ledgerForceResyncNeverConsumesPending() {
        DeltaLedger l = new DeltaLedger();
        l.record("a", "A", 1, 1);
        l.record("b", "B", 2, 2);
        assertTrue(l.drain(true).resync());
        assertEquals(2, l.pendingCount());
        assertEquals(2, l.drain(false).upserts().size());
        assertEquals(0, l.pendingCount());
    }

    @Test
    void ledgerEmptyDrainIsResyncWithZeroUpserts() {
        DeltaLedger l = new DeltaLedger();
        DeltaLedger.Batch b = l.drain(false);
        assertTrue(b.resync());
        assertTrue(b.upserts().isEmpty());
        assertEquals(0, b.seq());
    }

    @Test
    void liveTournamentSagaEndsWithCorrectPodium() {
        LiveLeaderboard lb = new LiveLeaderboard();
        String[] ids = {"a", "b", "c", "d", "e"};
        for (String id : ids) lb.join(id, id.toUpperCase());
        lb.applyScore("c", 300);
        lb.applyScore("a", 200);
        lb.applyScore("e", 250);
        lb.applyScore("b", 100);
        lb.applyScore("d", 50);
        assertEquals("c", lb.snapshot().get(0).uuid());
        assertEquals("e", lb.snapshot().get(1).uuid());
        assertEquals("a", lb.snapshot().get(2).uuid());
        assertEquals(1, lb.rankOf("c"));
        assertEquals(5, lb.rankOf("d"));
        assertEquals(5, lb.size());
    }

    @Test
    void liveTiesAcrossWholeBoardOrderByJoinSequence() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 6; i++) lb.join("p" + i, "P" + i);
        List<RankedSkipList.Entry> snap = lb.snapshot();
        for (int i = 0; i < 6; i++) assertEquals("p" + i, snap.get(i).uuid());
    }

    @Test
    void liveMassRemovalThenRejoinTournament() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 10; i++) lb.join("u" + i, "N" + i);
        for (int i = 0; i < 10; i += 2) lb.remove("u" + i);
        assertEquals(5, lb.size());
        for (int i = 0; i < 10; i += 2) lb.join("u" + i, "N" + i);
        assertEquals(10, lb.size());
        lb.applyScore("u0", 500);
        assertEquals(1, lb.rankOf("u0"));
        assertEquals(0, lb.scoreOf("u1"));
    }

    @Test
    void liveRejoinAfterRemovalResetsScoreAndKeepsSize() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("x", "X");
        lb.applyScore("x", 999);
        lb.remove("x");
        lb.join("x", "X2");
        assertEquals(1, lb.size());
        assertEquals(0, lb.scoreOf("x"));
        assertEquals("X2", lb.nameOf("x"));
    }

    @Test
    void liveDrainRefreshesStaleRanksAfterOvertake() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("slow", "S");
        lb.join("fast", "F");
        lb.applyScore("slow", 100);
        lb.applyScore("fast", 10);
        lb.applyScore("fast", 200);
        DeltaLedger.Batch b = lb.drainDeltas(false);
        assertEquals(2, b.upserts().size());
        for (DeltaLedger.Delta d : b.upserts()) {
            if (d.uuid().equals("fast")) {
                assertEquals(1, d.rank());
                assertEquals(210, d.score());
            } else {
                assertEquals(2, d.rank());
            }
        }
    }

    @Test
    void liveFullBatchReplacesWholesaleWithSequentialRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.join("c", "C");
        lb.applyScore("b", 30);
        DeltaLedger.Batch full = lb.fullBatch();
        assertTrue(full.resync());
        assertEquals(3, full.upserts().size());
        assertEquals("b", full.upserts().get(0).uuid());
        assertEquals(1, full.upserts().get(0).rank());
        assertEquals(2, full.upserts().get(1).rank());
        assertEquals(3, full.upserts().get(2).rank());
    }

    @Test
    void livePendingCountTracksDistinctPlayersOnly() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.applyScore("a", 1);
        lb.applyScore("a", 2);
        lb.applyScore("a", 3);
        assertEquals(1, lb.pendingDeltaCount());
        lb.join("b", "B");
        assertEquals(2, lb.pendingDeltaCount());
    }

    @Test
    void liveCurrentSeqGrowsWithEveryMutation() {
        LiveLeaderboard lb = new LiveLeaderboard();
        long s0 = lb.currentSeq();
        lb.join("a", "A");
        assertTrue(lb.currentSeq() > s0);
        long s1 = lb.currentSeq();
        lb.applyScore("a", 5);
        assertTrue(lb.currentSeq() > s1);
    }

    @Test
    void liveScoreOfUnknownIsZeroAndNameIsNull() {
        LiveLeaderboard lb = new LiveLeaderboard();
        assertEquals(0, lb.scoreOf("nobody"));
        assertNull(lb.nameOf("nobody"));
        assertEquals(-1, lb.rankOf("nobody"));
    }

    @Test
    void liveNegativeDeltasCanDriveScoreBelowZero() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("p", "P");
        lb.applyScore("p", 10);
        lb.applyScore("p", -25);
        assertEquals(-15, lb.scoreOf("p"));
    }

    @Test
    void liveZeroDeltaStillRecordsAndRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        assertEquals(1, lb.applyScore("a", 0));
        assertEquals(2, lb.rankOf("b"));
    }

    @Test
    void liveJoinReturnsStrictlyIncreasingSequences() {
        LiveLeaderboard lb = new LiveLeaderboard();
        long prev = 0;
        for (int i = 0; i < 25; i++) {
            long seq = lb.join("u" + i, "N");
            assertTrue(seq > prev);
            prev = seq;
        }
    }

    @Test
    void rankedSkipListHundredPlayersAllRanksExact() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 100; i++) sl.upsert("u" + i, "N", (i * 37L) % 500, i, Long.MIN_VALUE);
        assertEquals(100, sl.size());
        assertBoardConsistent(sl);
    }

    @Test
    void ledgerBatchUpsertsListIsImmutableSnapshot() {
        DeltaLedger l = new DeltaLedger();
        l.record("a", "A", 1, 1);
        DeltaLedger.Batch b = l.drain(false);
        List<DeltaLedger.Delta> copy = new ArrayList<>(b.upserts());
        copy.clear();
        assertEquals(1, b.upserts().size());
    }

    @Test
    void liveSnapshotReflectsRemovalsImmediately() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.join("c", "C");
        lb.remove("b");
        List<RankedSkipList.Entry> snap = lb.snapshot();
        assertEquals(2, snap.size());
        assertTrue(snap.stream().noneMatch(e -> e.uuid().equals("b")));
    }

    @Test
    void liveApplyScoreAfterRemoveReturnsMinusOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.remove("a");
        assertEquals(-1, lb.applyScore("a", 100));
        assertEquals(0, lb.size());
    }

    @Test
    void upsertReturnValueMatchesRankOf() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 10, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 20, 2, Long.MIN_VALUE);
        int rank = sl.upsert("c", "C", 15, 3, Long.MIN_VALUE);
        assertEquals(sl.rankOf("c"), rank);
        assertEquals(2, rank);
    }
}
