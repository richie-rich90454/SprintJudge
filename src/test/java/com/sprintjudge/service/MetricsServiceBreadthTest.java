package com.sprintjudge.service;

import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.executor.CompileArtifactCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsServiceBreadthTest {

    @TempDir Path tempDir;

    private MetricsService service(Semaphore permits, SubmissionWriteBuffer buffer,
                                   CompileArtifactCache cache, GameRoomManager rooms,
                                   BroadcastScheduler sched) {
        return new MetricsService(permits, buffer, cache, rooms, sched,
                mock(io.micrometer.core.instrument.MeterRegistry.class));
    }

    private MetricsService plain() throws Exception {
        return service(new Semaphore(5),
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                new CompileArtifactCache(tempDir.toString(), 16, 16),
                mock(GameRoomManager.class), mock(BroadcastScheduler.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> snap, String key) {
        return (Map<String, Object>) snap.get(key);
    }

    @Test
    void snapshotContainsEveryTopLevelSection() throws Exception {
        Map<String, Object> snap = plain().snapshot();
        for (String key : new String[]{"uptime_sec", "memory_gc", "threads", "judge",
                "persistence_buffer", "compile_cache", "runtime"}) {
            assertTrue(snap.containsKey(key), "missing " + key);
        }
    }

    @Test
    void singleSampleSetsAllPercentilesEqual() throws Exception {
        MetricsService svc = plain();
        svc.recordJudge(10_000_000L, false, false);
        Map<String, Object> judge = section(svc.snapshot(), "judge");
        assertEquals(10.0, (Double) judge.get("latency_ms_p50"), 1e-9);
        assertEquals(10.0, (Double) judge.get("latency_ms_p95"), 1e-9);
        assertEquals(10.0, (Double) judge.get("latency_ms_p99"), 1e-9);
        assertEquals(1L, judge.get("runs_total"));
    }

    @Test
    void stdoutCapOnlyIncrementsCaps() throws Exception {
        MetricsService svc = plain();
        svc.recordJudge(5_000_000L, false, true);
        Map<String, Object> judge = section(svc.snapshot(), "judge");
        assertEquals(1L, judge.get("stdout_caps_total"));
        assertEquals(0L, judge.get("timeouts_total"));
    }

    @Test
    void timeoutOnlyIncrementsTimeouts() throws Exception {
        MetricsService svc = plain();
        svc.recordJudge(5_000_000L, true, false);
        Map<String, Object> judge = section(svc.snapshot(), "judge");
        assertEquals(1L, judge.get("timeouts_total"));
        assertEquals(0L, judge.get("stdout_caps_total"));
    }

    @Test
    void permitsReflectLiveSemaphore() throws Exception {
        Semaphore permits = new Semaphore(3);
        permits.acquire(1);
        MetricsService svc = service(permits,
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                new CompileArtifactCache(tempDir.resolve("p").toString(), 16, 16),
                mock(GameRoomManager.class), mock(BroadcastScheduler.class));
        assertEquals(2, section(svc.snapshot(), "judge").get("permits_available"));
    }

    @Test
    void bufferDepthFlowsThroughSnapshot() throws Exception {
        SubmissionWriteBuffer buffer = new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000);
        buffer.offer(mock(com.sprintjudge.domain.models.Submission.class));
        buffer.offer(mock(com.sprintjudge.domain.models.Submission.class));
        MetricsService svc = service(new Semaphore(1), buffer,
                new CompileArtifactCache(tempDir.resolve("d").toString(), 16, 16),
                mock(GameRoomManager.class), mock(BroadcastScheduler.class));
        Map<String, Object> writes = section(svc.snapshot(), "persistence_buffer");
        assertEquals(2, writes.get("depth"));
        assertEquals(2L, writes.get("offered_total"));
        assertEquals(0L, writes.get("flushed_total"));
    }

    @Test
    void flushedTotalFlowsThroughAfterFlush() throws Exception {
        SubmissionWriteBuffer buffer = new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000);
        buffer.offer(mock(com.sprintjudge.domain.models.Submission.class));
        buffer.flush();
        MetricsService svc = service(new Semaphore(1), buffer,
                new CompileArtifactCache(tempDir.resolve("f").toString(), 16, 16),
                mock(GameRoomManager.class), mock(BroadcastScheduler.class));
        assertEquals(1L, section(svc.snapshot(), "persistence_buffer").get("flushed_total"));
    }

    @Test
    void judgeQueueLengthIsNonNegative() throws Exception {
        Object qlen = section(plain().snapshot(), "judge").get("queue_length");
        assertTrue(((Number) qlen).intValue() >= 0);
    }

    @Test
    void runtimeMaxPlayersDefaultsToZeroWithoutInjection() throws Exception {
        assertEquals(0, section(plain().snapshot(), "runtime").get("max_players_per_room"));
    }

    @Test
    void percentileOrderingAcrossSpread() throws Exception {
        MetricsService svc = plain();
        for (int i = 1; i <= 100; i++) svc.recordJudge((long) i * 1_000_000L, false, false);
        Map<String, Object> judge = section(svc.snapshot(), "judge");
        double p50 = (Double) judge.get("latency_ms_p50");
        double p95 = (Double) judge.get("latency_ms_p95");
        double p99 = (Double) judge.get("latency_ms_p99");
        assertTrue(p50 > 0 && p50 < p95 && p95 <= p99);
        GameRoomManager rooms = mock(GameRoomManager.class);
        BroadcastScheduler sched = mock(BroadcastScheduler.class);
        when(rooms.activeRooms()).thenReturn(2);
        when(sched.pendingRooms()).thenReturn(1);
        MetricsService wired = service(new Semaphore(1),
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                new CompileArtifactCache(tempDir.resolve("w").toString(), 16, 16), rooms, sched);
        assertEquals(2, section(wired.snapshot(), "runtime").get("rooms_active"));
    }

    @Test
    void gaugeScrapeExecutesEveryGaugeFunction() throws Exception {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        MetricsService svc = new MetricsService(new Semaphore(5),
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                new CompileArtifactCache(tempDir.resolve("g").toString(), 16, 16),
                mock(GameRoomManager.class), mock(BroadcastScheduler.class), registry);
        svc.recordJudge(5_000_000L, true, false);
        java.util.concurrent.atomic.AtomicInteger scraped = new java.util.concurrent.atomic.AtomicInteger();
        registry.forEachMeter(m -> {
            for (var ms : m.measure()) {
                assertTrue(Double.isFinite(ms.getValue()));
                scraped.incrementAndGet();
            }
        });
        assertTrue(scraped.get() >= 6);
    }
}
