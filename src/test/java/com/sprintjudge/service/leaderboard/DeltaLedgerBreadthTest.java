package com.sprintjudge.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeltaLedgerBreadthTest {

    @Test
    void latestRecordWinsForSameUuid() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 10, 2);
        l.record("u1", "A-renamed", 99, 1);
        assertEquals(1, l.pendingCount());
        DeltaLedger.Batch b = l.drain(false);
        assertEquals(1, b.upserts().size());
        assertEquals(99, b.upserts().get(0).score());
        assertEquals("A-renamed", b.upserts().get(0).name());
        assertEquals(1, b.upserts().get(0).rank());
    }

    @Test
    void reRecordKeepsOriginalInsertionPosition() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 10, 1);
        l.record("u2", "B", 20, 2);
        l.record("u1", "A", 30, 1);
        List<DeltaLedger.Delta> out = l.drain(false).upserts();
        assertEquals("u1", out.get(0).uuid());
        assertEquals("u2", out.get(1).uuid());
        assertEquals(30, out.get(0).score());
    }

    @Test
    void seqIsMonotonicAcrossDrains() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 1, 1);
        long first = l.drain(false).seq();
        l.record("u2", "B", 2, 2);
        long second = l.drain(false).seq();
        assertTrue(second > first);
    }

    @Test
    void drainTwiceSecondIsResync() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 1, 1);
        l.drain(false);
        DeltaLedger.Batch b = l.drain(false);
        assertTrue(b.resync());
        assertTrue(b.upserts().isEmpty());
    }

    @Test
    void forceResyncBatchCarriesCurrentSeq() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 1, 1);
        l.record("u2", "B", 2, 2);
        DeltaLedger.Batch b = l.drain(true);
        assertEquals(2, b.seq());
        assertEquals(2, l.pendingCount());
    }

    @Test
    void forceResyncOnEmptyLedgerIsSeqZero() {
        DeltaLedger.Batch b = new DeltaLedger().drain(true);
        assertEquals(0, b.seq());
        assertTrue(b.resync());
    }

    @Test
    void recordAfterDrainContinuesSequence() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 1, 1);
        l.drain(false);
        l.record("u2", "B", 2, 1);
        assertEquals(2, l.currentSeq());
        assertEquals(1, l.pendingCount());
    }

    @Test
    void hundredRecordsTrackExactly() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 100; i++) l.record("u" + i, "N" + i, i, i + 1);
        assertEquals(100, l.currentSeq());
        assertEquals(100, l.pendingCount());
        DeltaLedger.Batch b = l.drain(false);
        assertEquals(100, b.upserts().size());
        assertEquals(100, b.seq());
        assertEquals(0, l.pendingCount());
        assertEquals(100, l.currentSeq());
    }

    @Test
    void repeatedUpdatesToOnePlayerKeepSeqGrowingPendingAtOne() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 10; i++) l.record("solo", "S", i, 1);
        assertEquals(10, l.currentSeq());
        assertEquals(1, l.pendingCount());
        assertEquals(9, l.drain(false).upserts().get(0).score());
    }

    @Test
    void drainBatchSeqMatchesCurrentSeq() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 5, 1);
        DeltaLedger.Batch b = l.drain(false);
        assertEquals(l.currentSeq(), b.seq());
    }

    @Test
    void ranksAndNamesSurviveBatch() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "Ann", 50, 3);
        l.record("u2", "Bo", 70, 1);
        List<DeltaLedger.Delta> out = l.drain(false).upserts();
        assertEquals(3, out.get(0).rank());
        assertEquals("Bo", out.get(1).name());
    }

    @Test
    void emptyThenRecordThenForceResyncKeepsPending() {
        DeltaLedger l = new DeltaLedger();
        l.drain(false);
        l.record("u9", "Z", 1, 1);
        assertTrue(l.drain(true).resync());
        assertEquals(1, l.pendingCount());
        assertEquals(1, l.drain(false).upserts().size());
    }

    @Test
    void fiftyPlayerTournamentDrainsInJoinOrder() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 50; i++) l.record("u" + i, "N" + i, i * 10L, i + 1);
        assertEquals(50, l.pendingCount());
        assertEquals(50, l.currentSeq());
        var b = l.drain(false);
        org.junit.jupiter.api.Assertions.assertFalse(b.resync());
        assertEquals(50, b.upserts().size());
        assertEquals(50, b.seq());
        assertEquals("u0", b.upserts().get(0).uuid());
        assertEquals("u49", b.upserts().get(49).uuid());
        assertEquals(0, l.pendingCount());
    }

    @Test
    void hundredPlayerTournamentSeqTracksExactly() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 100; i++) l.record("p" + i, "P" + i, i, i + 1);
        assertEquals(100, l.currentSeq());
        var b = l.drain(false);
        assertEquals(100, b.seq());
        assertEquals(100, b.upserts().size());
        var empty = l.drain(false);
        assertTrue(empty.resync());
        assertTrue(empty.upserts().isEmpty());
    }

    @Test
    void twoHundredPlayerResyncReplacementCarriesFullSnapshot() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 200; i++) l.record("w" + i, "W" + i, i, i + 1);
        var first = l.drain(false);
        assertEquals(200, first.upserts().size());
        var gap = l.drain(false);
        assertTrue(gap.resync());
        l.record("w0", "W0", 9999, 1);
        assertEquals(1, l.pendingCount());
        var heal = l.drain(false);
        org.junit.jupiter.api.Assertions.assertFalse(heal.resync());
        assertEquals(9999, heal.upserts().get(0).score());
    }

    @Test
    void resyncAfterDrainThenNewRecordsContinueSequence() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 60; i++) l.record("a" + i, "A" + i, i, i + 1);
        long seqAfterFirst = l.drain(false).seq();
        assertEquals(60, seqAfterFirst);
        assertTrue(l.drain(false).resync());
        for (int i = 0; i < 40; i++) l.record("b" + i, "B" + i, i, i + 1);
        assertEquals(100, l.currentSeq());
        var b = l.drain(false);
        assertEquals(40, b.upserts().size());
        assertEquals(100, b.seq());
    }

    @Test
    void joinRemoveRejoinKeepsLatestRankAndName() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "Alice", 100, 1);
        l.record("u2", "Bob", 200, 2);
        l.record("u1", "Alice-Renamed", 150, 1);
        assertEquals(2, l.pendingCount());
        var b = l.drain(false);
        assertEquals(2, b.upserts().size());
        var first = b.upserts().get(0);
        assertEquals("u1", first.uuid());
        assertEquals("Alice-Renamed", first.name());
        assertEquals(150, first.score());
    }

    @Test
    void eightyPlayerCoalescedUpdatesKeepOneRowPerPlayer() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 80; i++) l.record("s" + i, "S" + i, 0, i + 1);
        for (int r = 0; r < 5; r++) for (int i = 0; i < 80; i++) l.record("s" + i, "S" + i, r * 10 + i, i + 1);
        assertEquals(80, l.pendingCount());
        assertEquals(80 * 6, l.currentSeq());
        var b = l.drain(false);
        assertEquals(80, b.upserts().size());
        for (var d : b.upserts()) assertEquals(40 + Integer.parseInt(d.uuid().substring(1)), d.score());
    }

    @Test
    void forceResyncMidTournamentPreservesAllPending() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 75; i++) l.record("m" + i, "M" + i, i * 5L, i + 1);
        var forced = l.drain(true);
        assertTrue(forced.resync());
        assertEquals(75, l.pendingCount());
        assertEquals(75, l.currentSeq());
        var real = l.drain(false);
        org.junit.jupiter.api.Assertions.assertFalse(real.resync());
        assertEquals(75, real.upserts().size());
        assertEquals(75, real.seq());
    }

    @Test
    void drainTwiceWithInterleavedRecordYieldsSingleRow() {
        DeltaLedger l = new DeltaLedger();
        l.record("solo", "S", 10, 1);
        l.drain(false);
        assertTrue(l.drain(false).resync());
        l.record("solo", "S", 20, 1);
        l.record("solo", "S", 30, 1);
        assertEquals(1, l.pendingCount());
        assertEquals(30, l.drain(false).upserts().get(0).score());
    }

    @Test
    void hundredFiftyPlayerBatchSeqMatchesCurrent() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 150; i++) l.record("e" + i, "E" + i, i * 2L, i + 1);
        var b = l.drain(false);
        assertEquals(l.currentSeq(), b.seq());
        assertEquals(150, b.upserts().size());
        assertEquals("e0", b.upserts().get(0).uuid());
        assertEquals(0, b.upserts().get(0).score());
        assertEquals(298, b.upserts().get(149).score());
    }

    @Test
    void rankStabilityAcrossDrainsForTopTen() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 50; i++) l.record("r" + i, "R" + i, 1000L - i * 10, i + 1);
        var b = l.drain(false);
        for (int i = 0; i < 10; i++) {
            assertEquals("r" + i, b.upserts().get(i).uuid());
            assertEquals(i + 1, b.upserts().get(i).rank());
        }
        l.record("r49", "R49", 2000, 1);
        var second = l.drain(false);
        assertEquals(1, second.upserts().size());
        assertEquals(1, second.upserts().get(0).rank());
    }

    @Test
    void nameChangeWithoutScoreChangeStillShips() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "Old", 100, 1);
        l.record("u1", "New", 100, 1);
        assertEquals(1, l.pendingCount());
        assertEquals(2, l.currentSeq());
        var b = l.drain(false);
        assertEquals("New", b.upserts().get(0).name());
        assertEquals(100, b.upserts().get(0).score());
    }

    @Test
    void zeroScorePlayersIncludedInBatch() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 30; i++) l.record("z" + i, "Z" + i, 0, i + 1);
        var b = l.drain(false);
        assertEquals(30, b.upserts().size());
        for (var d : b.upserts()) assertEquals(0, d.score());
    }

    @Test
    void negativeScoresSurviveBatchRoundTrip() {
        DeltaLedger l = new DeltaLedger();
        l.record("neg1", "N1", -100, 2);
        l.record("pos1", "P1", 100, 1);
        var b = l.drain(false);
        assertEquals(2, b.upserts().size());
        assertEquals(-100, b.upserts().get(0).score());
        assertEquals(100, b.upserts().get(1).score());
    }

    @Test
    void hundredPlayerDrainThenFiftyMoreContinueSeq() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 100; i++) l.record("a" + i, "A" + i, i, i + 1);
        l.drain(false);
        for (int i = 0; i < 50; i++) l.record("b" + i, "B" + i, i, i + 1);
        assertEquals(150, l.currentSeq());
        assertEquals(50, l.pendingCount());
        var b = l.drain(false);
        assertEquals(150, b.seq());
        assertEquals("b0", b.upserts().get(0).uuid());
    }

    @Test
    void singlePlayerTenUpdatesSeqTenPendingOne() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 10; i++) l.record("only", "O", i * 100L, 1);
        assertEquals(10, l.currentSeq());
        assertEquals(1, l.pendingCount());
        var b = l.drain(false);
        assertEquals(900, b.upserts().get(0).score());
        assertEquals(10, b.seq());
    }

    @Test
    void resyncReplacementAfterDrainCarriesNewSeq() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 10, 1);
        long firstSeq = l.drain(false).seq();
        var resync = l.drain(false);
        assertTrue(resync.resync());
        assertEquals(firstSeq, resync.seq());
        l.record("u2", "B", 20, 1);
        assertEquals(firstSeq + 1, l.currentSeq());
    }

    @Test
    void sixtyPlayerTournamentPreservesInsertionOrder() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 60; i++) l.record("o" + i, "O" + i, i * 3L, i + 1);
        var b = l.drain(false);
        for (int i = 0; i < 60; i++) assertEquals("o" + i, b.upserts().get(i).uuid());
    }

    @Test
    void ninetyPlayerDoubleUpdateKeepsLatestOnly() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 90; i++) l.record("d" + i, "D" + i, i, i + 1);
        for (int i = 0; i < 90; i++) l.record("d" + i, "D" + i, i + 1000, i + 1);
        assertEquals(90, l.pendingCount());
        assertEquals(180, l.currentSeq());
        var b = l.drain(false);
        for (var dlt : b.upserts()) assertTrue(dlt.score() >= 1000);
    }

    @Test
    void hundredTwentyPlayerForceResyncTwiceKeepsPending() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 120; i++) l.record("f" + i, "F" + i, i, i + 1);
        assertTrue(l.drain(true).resync());
        assertTrue(l.drain(true).resync());
        assertEquals(120, l.pendingCount());
        assertEquals(120, l.drain(false).upserts().size());
    }

    @Test
    void fiftyPlayerRemoveAndReaddKeepsNewScore() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 50; i++) l.record("r" + i, "R" + i, 100, i + 1);
        l.drain(false);
        l.record("r0", "R0", 999, 1);
        l.record("r1", "R1", 888, 2);
        var b = l.drain(false);
        assertEquals(2, b.upserts().size());
        assertEquals(999, b.upserts().get(0).score());
    }

    @Test
    void twoHundredPlayerSeqEndsAtTwoHundred() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 200; i++) l.record("big" + i, "B" + i, i, i + 1);
        assertEquals(200, l.currentSeq());
        assertEquals(200, l.pendingCount());
        var b = l.drain(false);
        assertEquals(200, b.seq());
        assertEquals(0, l.pendingCount());
    }

    @Test
    void interleavedDrainEveryTenKeepsSeqMonotonic() {
        DeltaLedger l = new DeltaLedger();
        long last = 0;
        for (int batch = 0; batch < 10; batch++) {
            for (int i = 0; i < 10; i++) l.record("u" + batch + "-" + i, "N", i, i + 1);
            long seq = l.drain(false).seq();
            org.junit.jupiter.api.Assertions.assertTrue(seq > last);
            last = seq;
        }
        assertEquals(100, last);
    }

    @Test
    void emptyLedgerForceResyncSeqZeroTwice() {
        DeltaLedger l = new DeltaLedger();
        assertEquals(0, l.drain(true).seq());
        assertEquals(0, l.drain(true).seq());
        assertEquals(0, l.pendingCount());
    }

    @Test
    void singleUuidFiftyUpdatesPendingOneSeqFifty() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 50; i++) l.record("solo2", "S", i, 1);
        assertEquals(50, l.currentSeq());
        assertEquals(1, l.pendingCount());
        assertEquals(49, l.drain(false).upserts().get(0).score());
    }

    @Test
    void batchUpsertsUnmodifiableSafeToIterateTwice() {
        DeltaLedger l = new DeltaLedger();
        for (int i = 0; i < 20; i++) l.record("it" + i, "I" + i, i, i + 1);
        var b = l.drain(false);
        assertEquals(20, b.upserts().size());
        int first = 0;
        for (var d : b.upserts()) first += d.score();
        int second = 0;
        for (var d : b.upserts()) second += d.score();
        assertEquals(first, second);
        assertEquals(190, first);
    }

    @Test
    void resyncBatchUpsertsAlwaysEmpty() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 5, 1);
        l.drain(false);
        for (int i = 0; i < 5; i++) {
            var r = l.drain(false);
            assertTrue(r.resync());
            assertTrue(r.upserts().isEmpty());
        }
    }
}
