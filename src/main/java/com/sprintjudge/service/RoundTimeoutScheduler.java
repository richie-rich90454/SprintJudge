package com.sprintjudge.service;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Per-room round-deadline watchdog. One shared daemon thread arms a
 * {@link ScheduledFuture} at each round start (and re-arms on +time extensions);
 * on fire it asks the manager to transition the room to REVIEW. The manager
 * cancels the task on force-submit / game end so no stray transition lands.
 */
@Component
public class RoundTimeoutScheduler {

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "oq-round-timer");
                t.setDaemon(true);
                return t;
            });
    private final Map<Integer, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    /** Arms the timer for {@code pinKey}; any previous task for that room is replaced. */
    public synchronized void schedule(int pinKey, long endEpochMs, Runnable fire) {
        cancelLocked(pinKey);
        long delay = Math.max(0, endEpochMs - System.currentTimeMillis());
        // Small grace so a client auto-submit landing right at zero still scores.
        tasks.put(pinKey, executor.schedule(fire::run, delay + 500, TimeUnit.MILLISECONDS));
    }

    public synchronized void cancel(int pinKey) {
        cancelLocked(pinKey);
    }

    private void cancelLocked(int pinKey) {
        ScheduledFuture<?> f = tasks.remove(pinKey);
        if (f != null) f.cancel(false);
    }

    @PreDestroy
    void stop() {
        tasks.values().forEach(f -> f.cancel(false));
        executor.shutdownNow();
    }
}
