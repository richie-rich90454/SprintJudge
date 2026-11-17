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

    /** A single player's pending leaderboard change. */
    public static final class Delta {
        private String uuid;
        private String name;
        private long score;
        private int rank;

        public Delta() {}

        public Delta(String uuid, String name, long score, int rank) {
            this.uuid = uuid;
            this.name = name;
            this.score = score;
            this.rank = rank;
        }

        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public long getScore() { return score; }
        public void setScore(long score) { this.score = score; }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Delta)) return false;
            Delta other = (Delta) o;
            return score == other.score && rank == other.rank
                    && java.util.Objects.equals(uuid, other.uuid)
                    && java.util.Objects.equals(name, other.name);
        }

        @Override public int hashCode() { return java.util.Objects.hash(uuid, name, score, rank); }

        @Override public String toString() {
            return "Delta[uuid=" + uuid + ", name=" + name + ", score=" + score + ", rank=" + rank + "]";
        }
    }

    /** One broadcast unit: apply upserts; empty list means "full resync". */
    public static final class Batch {
        private long seq;
        private boolean resync;
        private List<Delta> upserts;

        public Batch() {}

        public Batch(long seq, boolean resync, List<Delta> upserts) {
            this.seq = seq;
            this.resync = resync;
            this.upserts = upserts;
        }

        public long getSeq() { return seq; }
        public void setSeq(long seq) { this.seq = seq; }

        public boolean isResync() { return resync; }
        public void setResync(boolean resync) { this.resync = resync; }

        public List<Delta> getUpserts() { return upserts; }
        public void setUpserts(List<Delta> upserts) { this.upserts = upserts; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Batch)) return false;
            Batch other = (Batch) o;
            return seq == other.seq && resync == other.resync
                    && java.util.Objects.equals(upserts, other.upserts);
        }

        @Override public int hashCode() { return java.util.Objects.hash(seq, resync, upserts); }

        @Override public String toString() {
            return "Batch[seq=" + seq + ", resync=" + resync + ", upserts=" + upserts + "]";
        }
    }

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
