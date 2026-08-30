package com.sprintjudge.service;

import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.executor.CompileArtifactCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsServiceTest {

    @TempDir Path tempDir;

    private MetricsService service() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        SubmissionWriteBuffer buffer = new SubmissionWriteBuffer(repo, 100000, 1000);
        CompileArtifactCache cache = new CompileArtifactCache(tempDir.toString(), 16, 16);
        return new MetricsService(new Semaphore(5), buffer, cache,
                mock(GameRoomManager.class), mock(BroadcastScheduler.class));
    }

    @Test
    void snapshotWithNoSamplesReportsZeroPercentiles() throws Exception {
        Map<String, Object> snap = service().snapshot();
        assertNotNull(snap);
        @SuppressWarnings("unchecked")
        Map<String, Object> judge = (Map<String, Object>) snap.get("judge");
        org.junit.jupiter.api.Assertions.assertEquals(0.0, (Double) judge.get("latency_ms_p50"));
        org.junit.jupiter.api.Assertions.assertEquals(0.0, (Double) judge.get("latency_ms_p99"));
    }

    @Test
    void recordAndSnapshotExercisesBothBranches() throws Exception {
        MetricsService svc = service();
        svc.recordJudge(10_000_000L, true, true);   // timedOut + stdoutCapped
        svc.recordJudge(20_000_000L, false, false); // neither
        svc.recordJudge(30_000_000L, true, false);  // timedOut only

        Map<String, Object> snap = svc.snapshot();
        @SuppressWarnings("unchecked")
        Map<String, Object> judge = (Map<String, Object>) snap.get("judge");
        org.junit.jupiter.api.Assertions.assertEquals(3L, judge.get("runs_total"));
        org.junit.jupiter.api.Assertions.assertEquals(2L, judge.get("timeouts_total"));
        org.junit.jupiter.api.Assertions.assertEquals(1L, judge.get("stdout_caps_total"));
        assertTrue((Double) judge.get("latency_ms_p99") > 0.0);
        assertTrue(snap.containsKey("memory_gc"));
        assertTrue(snap.containsKey("compile_cache"));
    }

    @Test
    void compileCacheMetricsFlowThroughSnapshot() throws Exception {
        CompileArtifactCache cache = new CompileArtifactCache(tempDir.resolve("c2").toString(), 16, 16);
        MetricsService svc = new MetricsService(new Semaphore(1),
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                cache, mock(GameRoomManager.class), mock(BroadcastScheduler.class));
        cache.misses();
        assertTrue(svc.snapshot().containsKey("compile_cache"));
    }

    @Test
    void snapshotReflectsRuntimeAndPersistenceValues() throws Exception {
        GameRoomManager rooms = mock(GameRoomManager.class);
        BroadcastScheduler sched = mock(BroadcastScheduler.class);
        when(rooms.activeRooms()).thenReturn(7);
        when(sched.pendingRooms()).thenReturn(3);
        MetricsService svc = new MetricsService(new Semaphore(2),
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                new CompileArtifactCache(tempDir.resolve("rt").toString(), 16, 16),
                rooms, sched);
        @SuppressWarnings("unchecked")
        Map<String, Object> rt = (Map<String, Object>) svc.snapshot().get("runtime");
        int roomsActive = (Integer) rt.get("rooms_active");
        int pending = (Integer) rt.get("broadcast_pending_rooms");
        assertEquals(7, roomsActive);
        assertEquals(3, pending);
    }

    @Test
    void snapshotReflectsCompileCacheHit() throws Exception {
        CompileArtifactCache cache = new CompileArtifactCache(tempDir.resolve("hit").toString(), 16, 16);
        Path bin = Files.createTempFile(tempDir, "b", ".o");
        Files.write(bin, new byte[]{1});
        cache.put("k", bin);
        cache.get("k"); // a hit
        MetricsService svc = new MetricsService(new Semaphore(1),
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                cache, mock(GameRoomManager.class), mock(BroadcastScheduler.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> cc = (Map<String, Object>) svc.snapshot().get("compile_cache");
        long hits = (Long) cc.get("hits");
        double hr = (Double) cc.get("hit_ratio");
        assertEquals(1L, hits);
        assertEquals(1.0, hr, 0.0001);
    }

    @Test
    void recordJudgeCountersAreIndependent() throws Exception {
        MetricsService svc = new MetricsService(new Semaphore(1),
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                new CompileArtifactCache(tempDir.resolve("cnt").toString(), 16, 16),
                mock(GameRoomManager.class), mock(BroadcastScheduler.class));
        svc.recordJudge(5_000_000L, false, false);
        svc.recordJudge(15_000_000L, true, false);
        svc.recordJudge(25_000_000L, false, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> judge = (Map<String, Object>) svc.snapshot().get("judge");
        long runs = (Long) judge.get("runs_total");
        long timeouts = (Long) judge.get("timeouts_total");
        long caps = (Long) judge.get("stdout_caps_total");
        assertEquals(3L, runs);
        assertEquals(1L, timeouts);
        assertEquals(1L, caps);
    }

    @Test
    void manyRecordsComputeOrderedPercentiles() throws Exception {
        MetricsService svc = new MetricsService(new Semaphore(1),
                new SubmissionWriteBuffer(mock(SubmissionRepository.class), 100000, 1000),
                new CompileArtifactCache(tempDir.resolve("pct").toString(), 16, 16),
                mock(GameRoomManager.class), mock(BroadcastScheduler.class));
        for (int i = 1; i <= 1500; i++) {
            svc.recordJudge((long) i * 1_000_000L, false, false);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> judge = (Map<String, Object>) svc.snapshot().get("judge");
        double p50 = (Double) judge.get("latency_ms_p50");
        double p99 = (Double) judge.get("latency_ms_p99");
        assertTrue(p99 > p50);
        assertTrue(p50 > 0.0);
    }

    @Test
    void snapshotIncludesThreadCounts() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> th = (Map<String, Object>) service().snapshot().get("threads");
        assertNotNull(th);
        assertTrue(th.containsKey("platform_active"));
        assertTrue(th.containsKey("peak"));
    }
}
