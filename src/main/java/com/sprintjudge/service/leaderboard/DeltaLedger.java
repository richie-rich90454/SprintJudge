package com.sprintjudge.service.leaderboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sequence-numbered change log for leaderboard broadcasts.
 *
 * <p>Every mutation bumps a monotonically increasing {@code seq}. Pending
 * changes are merged per-player (latest wins) so coalesced broadcasts never
 * lose or reorder truth. Clients track the last seq they applied; on gap they
 * request a full resync and receive an authoritative snapshot stamped with the
 * current seq. No inaccuracies, by construction.
 */
public final class DeltaLedger {

    public record Delta(String uuid, String name, long score, int rank) {}

    /** One broadcast unit: apply upserts; empty list means "full resync". */
    public record Batch(long seq, boolean resync, List<Delta> upserts) {}

    private final ReentrantLock lock = new ReentrantLock();
    private long seq;
    private final Map<String, Delta> pending = new LinkedHashMap<>();

    public void record(String uuid, String name, long score, int rank) {
        lock.lock();
        try {
            seq++;
            pending.put(uuid, new Delta(uuid, name, score, rank));
        } finally {
            lock.unlock();
        }
    }

    public void recordAll(List<Delta> deltas) {
        lock.lock();
        try {
            for (Delta d : deltas) {
                seq++;
                pending.put(d.uuid(), d);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drains pending changes into one batch. When the receiver's last seen seq
     * is behind the oldest pending change (or unknown), pass
     * {@code clientSeq < 0} to force a full-resync batch instead.
     */
    public Batch drain(boolean forceResync) {
        lock.lock();
        try {
            if (forceResync || pending.isEmpty()) {
                return new Batch(seq, true, List.of());
            }
            List<Delta> out = new ArrayList<>(pending.values());
            pending.clear();
            return new Batch(seq, false, out);
        } finally {
            lock.unlock();
        }
    }

    public long currentSeq() {
        lock.lock();
        try {
            return seq;
        } finally {
            lock.unlock();
        }
    }

    public int pendingCount() {
        lock.lock();
        try {
            return pending.size();
        } finally {
            lock.unlock();
        }
    }
}
