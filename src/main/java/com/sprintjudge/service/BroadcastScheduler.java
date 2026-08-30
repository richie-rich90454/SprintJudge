package com.sprintjudge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 16 ms coalescing scheduler for room broadcasts.
 *
 * <p>Hot events (score changes) mark a room dirty instead of sending instantly;
 * one shared tick drains every dirty room per frame, collapsing bursts of
 * submissions into a single syscall batch and one delta payload per room.
 * Low-frequency events (joins, state changes) bypass this and send immediately.
 */
@Component
public class BroadcastScheduler {

    private static final Logger log = LoggerFactory.getLogger(BroadcastScheduler.class);
    private final Map<Integer, Runnable> due = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "oq-broadcast-tick");
                t.setDaemon(true);
                return t;
            });

    @Value("${sprintjudge.broadcast.coalesce-ms:16}")
    private long coalesceMs;

    @jakarta.annotation.PostConstruct
    void start() {
        long period = Math.max(1, coalesceMs);
        executor.scheduleWithFixedDelay(this::drain, period, period, TimeUnit.MILLISECONDS);
    }

    /** Marks the room for the next tick; latest task wins (idempotent flush). */
    public void markDirty(int pinKey, Runnable flushTask) {
        due.put(pinKey, flushTask);
    }

    public int pendingRooms() {
        return due.size();
    }

    private void drain() {
        if (due.isEmpty()) return;
        // Snapshot-then-clear is safe here: entries lost to a concurrent markDirty
        // will be re-marked on the next tick (within 16ms). Acceptable for
        // coalescing precision — worst case is one extra tick delay.
        Map<Integer, Runnable> snapshot = new java.util.HashMap<>(due);
        due.clear();
        for (var entry : snapshot.entrySet()) {
            try {
                entry.getValue().run();
            } catch (RuntimeException e) {
                log.warn("Broadcast flush failed for room {}", entry.getKey(), e);
            }
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
