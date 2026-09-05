package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BroadcastSchedulerTest {

    private void drain(BroadcastScheduler s) throws Exception {
        Method m = BroadcastScheduler.class.getDeclaredMethod("drain");
        m.setAccessible(true);
        m.invoke(s);
    }

    @Test
    void markDirtyRunsTask() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        AtomicBoolean ran = new AtomicBoolean(false);
        s.markDirty(1, () -> ran.set(true));
        drain(s);
        assertTrue(ran.get());
        assertEquals(0, s.pendingRooms());
    }

    @Test
    void throwingTaskIsCaught() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        s.markDirty(2, () -> {
            throw new RuntimeException("boom");
        });
        drain(s); // must swallow the RuntimeException
    }

    @Test
    void emptyDrainIsNoop() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        drain(s); // empty map -> returns immediately
        assertEquals(0, s.pendingRooms());
    }

    @Test
    void startSchedulesAndStopShutsDown() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        Method start = BroadcastScheduler.class.getDeclaredMethod("start");
        start.setAccessible(true);
        start.invoke(s); // covers the @PostConstruct scheduling path
        s.stop();        // covers executor shutdown
    }

    @Test
    void latestTaskWinsForSameRoom() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        java.util.List<String> ran = new java.util.ArrayList<>();
        s.markDirty(7, () -> ran.add("first"));
        s.markDirty(7, () -> ran.add("second"));
        assertEquals(1, s.pendingRooms());
        drain(s);
        assertEquals(java.util.List.of("second"), ran);
    }

    @Test
    void multipleRoomsAllRun() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        java.util.Set<Integer> ran = java.util.concurrent.ConcurrentHashMap.newKeySet();
        s.markDirty(1, () -> ran.add(1));
        s.markDirty(2, () -> ran.add(2));
        s.markDirty(3, () -> ran.add(3));
        assertEquals(3, s.pendingRooms());
        drain(s);
        assertEquals(java.util.Set.of(1, 2, 3), ran);
        assertEquals(0, s.pendingRooms());
    }

    @Test
    void failingTaskDoesNotBlockSiblings() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        AtomicBoolean survivor = new AtomicBoolean(false);
        s.markDirty(1, () -> { throw new RuntimeException("one fails"); });
        s.markDirty(2, () -> survivor.set(true));
        drain(s);
        assertTrue(survivor.get());
        assertEquals(0, s.pendingRooms());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullTaskFromRaceIsSkipped() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        java.lang.reflect.Field f = BroadcastScheduler.class.getDeclaredField("due");
        f.setAccessible(true);
        java.util.Map<Integer, Runnable> due = (java.util.Map<Integer, Runnable>) f.get(s);
        java.util.Map<Integer, Runnable> racing = org.mockito.Mockito.mock(java.util.Map.class);
        org.mockito.Mockito.when(racing.keySet()).thenReturn(java.util.Set.of(99));
        org.mockito.Mockito.when(racing.remove(99)).thenReturn(null);
        org.mockito.Mockito.when(racing.size()).thenReturn(1);
        f.set(s, racing);
        drain(s);
        org.mockito.Mockito.verify(racing).remove(99);
        f.set(s, due);
        s.stop();
    }

    @Test
    void startHonorsConfiguredCoalescePeriod() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        java.lang.reflect.Field f = BroadcastScheduler.class.getDeclaredField("coalesceMs");
        f.setAccessible(true);
        f.setLong(s, 16L);
        Method start = BroadcastScheduler.class.getDeclaredMethod("start");
        start.setAccessible(true);
        start.invoke(s);
        s.markDirty(5, () -> {});
        assertEquals(1, s.pendingRooms());
        s.stop();
    }

    @Test
    void startClampsNonPositiveCoalesceToOneMs() throws Exception {
        BroadcastScheduler s = new BroadcastScheduler();
        java.lang.reflect.Field f = BroadcastScheduler.class.getDeclaredField("coalesceMs");
        f.setAccessible(true);
        f.setLong(s, 0L);
        Method start = BroadcastScheduler.class.getDeclaredMethod("start");
        start.setAccessible(true);
        start.invoke(s);
        s.stop();
    }

    @Test
    void pendingRoomsCountsDistinctKeysOnce() {
        BroadcastScheduler s = new BroadcastScheduler();
        s.markDirty(1, () -> {});
        s.markDirty(1, () -> {});
        s.markDirty(2, () -> {});
        assertEquals(2, s.pendingRooms());
        s.stop();
    }
}
