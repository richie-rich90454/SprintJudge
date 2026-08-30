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
}
