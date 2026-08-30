package com.sprintjudge.service;

import com.sprintjudge.service.executor.CompileArtifactCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dependency-free runtime metrics with rolling windows for the hot paths
 * (judge latency, broadcast batch sizes). Exposed via /api/admin/metrics.
 */
@Service
public class MetricsService {

    private final long startedAt = System.currentTimeMillis();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();

    private final Semaphore judgeSlots;
    private final SubmissionWriteBuffer writeBuffer;
    private final CompileArtifactCache compileCache;
    private final GameRoomManager roomManager;
    private final BroadcastScheduler scheduler;

    @Value("${sprintjudge.room.max-players:10000}")
    private int maxPlayers;

    // Rolling judge-latency window (last 1024 samples, ns).
    private static final int WINDOW = 1024;
    private final long[] judgeNanos = new long[WINDOW];
    private final AtomicLong judgeCount = new AtomicLong();
    private final AtomicLong judgeTimeouts = new AtomicLong();
    private final AtomicLong judgeStdoutCaps = new AtomicLong();

    public MetricsService(Semaphore judgeSlots,
                          SubmissionWriteBuffer writeBuffer,
                          CompileArtifactCache compileCache,
                          GameRoomManager roomManager,
                          BroadcastScheduler scheduler) {
        this.judgeSlots = judgeSlots;
        this.writeBuffer = writeBuffer;
        this.compileCache = compileCache;
        this.roomManager = roomManager;
        this.scheduler = scheduler;
    }

    public void recordJudge(long nanos, boolean timedOut, boolean stdoutCapped) {
        long idx = judgeCount.incrementAndGet();
        judgeNanos[(int) ((idx - 1) % WINDOW)] = nanos;
        if (timedOut) judgeTimeouts.incrementAndGet();
        if (stdoutCapped) judgeStdoutCaps.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uptime_sec", (System.currentTimeMillis() - startedAt) / 1000);

        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("heap_used_mb", memory.getHeapMemoryUsage().getUsed() >> 20);
        mem.put("heap_max_mb", memory.getHeapMemoryUsage().getMax() >> 20);
        mem.put("non_heap_used_mb", memory.getNonHeapMemoryUsage().getUsed() >> 20);
        for (GarbageCollectorMXBean gc : gcs) {
            mem.put("gc_" + gc.getName().replace(' ', '_') + "_count", gc.getCollectionCount());
            mem.put("gc_" + gc.getName().replace(' ', '_') + "_ms", gc.getCollectionTime());
        }
        m.put("memory_gc", mem);

        Map<String, Object> th = new LinkedHashMap<>();
        th.put("platform_active", threads.getThreadCount());
        th.put("peak", threads.getPeakThreadCount());
        m.put("threads", th);

        Map<String, Object> judge = new LinkedHashMap<>();
        judge.put("permits_available", judgeSlots.availablePermits());
        judge.put("queue_length", judgeSlots.getQueueLength());
        judge.put("runs_total", judgeCount.get());
        judge.put("timeouts_total", judgeTimeouts.get());
        judge.put("stdout_caps_total", judgeStdoutCaps.get());
        putPercentiles(judge, judgeNanos, judgeCount.get());
        m.put("judge", judge);

        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put("offered_total", writeBuffer.offeredTotal());
        writes.put("flushed_total", writeBuffer.flushedTotal());
        writes.put("depth", writeBuffer.depth());
        writes.put("last_flush_epoch_ms", writeBuffer.lastFlushEpochMs());
        m.put("persistence_buffer", writes);

        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("entries", compileCache.entries());
        cache.put("bytes", compileCache.bytes());
        cache.put("hits", compileCache.hits());
        cache.put("misses", compileCache.misses());
        cache.put("hit_ratio", Math.round(compileCache.hitRatio() * 10000) / 10000.0);
        m.put("compile_cache", cache);

        Map<String, Object> rt = new LinkedHashMap<>();
        rt.put("rooms_active", roomManager.activeRooms());
        rt.put("broadcast_pending_rooms", scheduler.pendingRooms());
        rt.put("max_players_per_room", maxPlayers);
        m.put("runtime", rt);

        return m;
    }

    /** p50/p95/p99 in milliseconds over the rolling window. */
    private void putPercentiles(Map<String, Object> into, long[] ring, long totalWritten) {
        int n = (int) Math.min(totalWritten, WINDOW);
        if (n == 0) {
            into.put("latency_ms_p50", 0.0);
            into.put("latency_ms_p95", 0.0);
            into.put("latency_ms_p99", 0.0);
            return;
        }
        long[] copy = new long[n];
        System.arraycopy(ring, 0, copy, 0, n);
        java.util.Arrays.sort(copy);
        into.put("latency_ms_p50", copy[(int) (n * 0.50)] / 1_000_000.0);
        into.put("latency_ms_p95", copy[Math.min(n - 1, (int) (n * 0.95))] / 1_000_000.0);
        into.put("latency_ms_p99", copy[Math.min(n - 1, (int) (n * 0.99))] / 1_000_000.0);
    }
}
