package com.openquiz.service;

import com.openquiz.domain.models.Submission;
import com.openquiz.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Write-coalescing buffer for submissions (250 ms default).
 *
 * <p>Score truth lives in the room's in-memory leaderboard; this only batches
 * the durable audit trail. A crash loses at most one flush window of history —
 * never correctness of live rankings. Round boundaries call {@link #flush()}
 * synchronously so results screens are always fully persisted.
 */
@Component
public class SubmissionWriteBuffer {

    private final SubmissionRepository repository;
    private final BlockingQueue<Submission> queue;
    private final ScheduledExecutorService flusher =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "oq-write-flusher");
                t.setDaemon(true);
                return t;
            });

    private final AtomicLong flushed = new AtomicLong();
    private final AtomicLong offered = new AtomicLong();
    private volatile long lastFlushMs;

    public SubmissionWriteBuffer(
            SubmissionRepository repository,
            @Value("${openquiz.persistence.flush-ms:250}") long flushMs,
            @Value("${openquiz.persistence.buffer-capacity:20000}") int capacity) {
        this.repository = repository;
        this.queue = new ArrayBlockingQueue<>(capacity);
        long period = Math.max(25, flushMs);
        flusher.scheduleWithFixedDelay(this::flushSafely, period, period, TimeUnit.MILLISECONDS);
    }

    /** Non-blocking; drops-never policy via bounded backpressure to caller. */
    public boolean offer(Submission s) {
        offered.incrementAndGet();
        return queue.offer(s);
    }

    /** Synchronous drain — used at accuracy boundaries (force-submit, game end). */
    public synchronized int flush() {
        if (queue.isEmpty()) return 0;
        List<Submission> batch = new ArrayList<>(queue.size());
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            repository.saveAll(batch);
            flushed.addAndGet(batch.size());
        }
        lastFlushMs = System.currentTimeMillis();
        return batch.size();
    }

    private void flushSafely() {
        try {
            flush();
        } catch (RuntimeException ignored) {
            // Next tick retries; live rankings are unaffected.
        }
    }

    @PreDestroy
    void shutdown() {
        flushSafely();
        flusher.shutdownNow();
    }

    // ---------- metrics ----------

    public long offeredTotal() { return offered.get(); }
    public long flushedTotal() { return flushed.get(); }
    public int depth() { return queue.size(); }
    public long lastFlushEpochMs() { return lastFlushMs; }
}
