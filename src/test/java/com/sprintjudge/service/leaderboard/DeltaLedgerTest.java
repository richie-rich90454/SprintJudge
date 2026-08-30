package com.sprintjudge.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeltaLedgerTest {

    @Test
    void recordBumpsSeqAndPending() {
        DeltaLedger l = new DeltaLedger();
        assertEquals(0, l.currentSeq());
        assertEquals(0, l.pendingCount());
        l.record("u1", "A", 10, 1);
        assertEquals(1, l.currentSeq());
        assertEquals(1, l.pendingCount());
        l.record("u2", "B", 20, 2);
        assertEquals(2, l.currentSeq());
        assertEquals(2, l.pendingCount());
    }

    @Test
    void drainWithPendingReturnsDeltaBatch() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 10, 1);
        l.record("u2", "B", 20, 2);
        DeltaLedger.Batch b = l.drain(false);
        assertFalse(b.resync());
        assertEquals(2, b.seq());
        assertEquals(2, b.upserts().size());
        assertEquals(0, l.pendingCount());
    }

    @Test
    void drainForceResyncKeepsPending() {
        DeltaLedger l = new DeltaLedger();
        l.record("u1", "A", 10, 1);
        DeltaLedger.Batch b = l.drain(true);
        assertTrue(b.resync());
        assertTrue(b.upserts().isEmpty());
        assertEquals(1, l.pendingCount());
    }

    @Test
    void drainEmptyReturnsResyncBatch() {
        DeltaLedger l = new DeltaLedger();
        DeltaLedger.Batch b = l.drain(false);
        assertTrue(b.resync());
        assertTrue(b.upserts().isEmpty());
    }
}
