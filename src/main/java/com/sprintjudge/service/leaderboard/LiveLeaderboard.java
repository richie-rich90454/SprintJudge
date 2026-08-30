package com.sprintjudge.service.leaderboard;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Authoritative live ranking for one room at 10k-player scale.
 *
 * <p>Identity lives in a lock-free {@link ConcurrentHashMap}; ordering lives in
 * the exact {@link RankedSkipList}; broadcast truth flows through
 * {@link DeltaLedger}. Full snapshots are built on demand (joiners, resyncs,
 * metrics) from the skip list in O(n) with zero sorting.
 */
public final class LiveLeaderboard {

    private static final class Slot {
        final String name;
        final long joinSeq;
        volatile long score;
        volatile boolean present = true;

        Slot(String name, long joinSeq, long score) {
            this.name = name;
            this.joinSeq = joinSeq;
            this.score = score;
        }
    }

    private final Map<String, Slot> players = new ConcurrentHashMap<>();
    private final RankedSkipList ordered = new RankedSkipList();
    private final DeltaLedger ledger = new DeltaLedger();
    private final AtomicLong joinCounter = new AtomicLong();

    /** Adds a player at score 0. Returns their unique join sequence. */
    public long join(String uuid, String name) {
        long seq = joinCounter.incrementAndGet();
        players.put(uuid, new Slot(name, seq, 0L));
        ordered.upsert(uuid, name, 0L, seq, Long.MIN_VALUE);
        ledger.record(uuid, name, 0L, ordered.rankOf(uuid));
        return seq;
    }

    /** Applies a score delta (positive or negative); returns the new exact rank. */
    public synchronized int applyScore(String uuid, long delta) {
        Slot s = players.get(uuid);
        if (s == null || !s.present) return -1;
        long prevScore = s.score;
        long newScore = prevScore + delta;
        s.score = newScore;
        int rank = ordered.upsert(uuid, s.name, newScore, s.joinSeq, prevScore);
        ledger.record(uuid, s.name, newScore, rank);
        return rank;
    }

    public void remove(String uuid) {
        Slot s = players.remove(uuid);
        if (s == null) return;
        s.present = false;
        ordered.remove(uuid, s.score, s.joinSeq);
    }

    public int rankOf(String uuid) {
        return ordered.rankOf(uuid);
    }

    public long scoreOf(String uuid) {
        Slot s = players.get(uuid);
        return s == null ? 0L : s.score;
    }

    public String nameOf(String uuid) {
        Slot s = players.get(uuid);
        return s == null ? null : s.name;
    }

    public int size() {
        return players.size();
    }

    /** Exact full snapshot [best..worst]; also stamps it as current-seq baseline. */
    public List<RankedSkipList.Entry> snapshot() {
        return ordered.snapshot();
    }

    // ---------- broadcast plumbing ----------

    public DeltaLedger.Batch drainDeltas(boolean forceResync) {
        return ledger.drain(forceResync);
    }

    /**
     * Builds a fresh full batch (joiner/resync path) stamped with current seq.
     * Does NOT touch the ledger: inflating seq here would make every other
     * client see a phantom gap and cascade-resync.
     */
    public DeltaLedger.Batch fullBatch() {
        List<DeltaLedger.Delta> all = new java.util.ArrayList<>(players.size());
        int rank = 1;
        for (RankedSkipList.Entry e : ordered.snapshot()) {
            all.add(new DeltaLedger.Delta(e.uuid(), e.name(), e.score(), rank++));
        }
        // resync=true tells clients to REPLACE their local ranking wholesale.
        return new DeltaLedger.Batch(ledger.currentSeq(), true, all);
    }

    public long currentSeq() {
        return ledger.currentSeq();
    }

    public int pendingDeltaCount() {
        return ledger.pendingCount();
    }
}
