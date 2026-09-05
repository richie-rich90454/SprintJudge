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
}
