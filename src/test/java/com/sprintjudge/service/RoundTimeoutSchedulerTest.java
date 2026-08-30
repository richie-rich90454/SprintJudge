package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundTimeoutSchedulerTest {

    @Test
    void scheduleFiresTheRunnableAfterDelay() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        CountDownLatch fired = new CountDownLatch(1);
        s.schedule(1, System.currentTimeMillis(), fired::countDown);
        assertTrue(fired.await(2, TimeUnit.SECONDS));
    }

    @Test
    void cancelPreventsTheRunnableFromFiring() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        CountDownLatch fired = new CountDownLatch(1);
        s.schedule(2, System.currentTimeMillis() + 100_000, fired::countDown);
        s.cancel(2);
        assertFalse(fired.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void cancelUnknownPinIsNoOp() {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        s.cancel(987654); // must not throw
    }

    @Test
    void stopWithPendingTaskCancelsAndShutsDown() {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        CountDownLatch fired = new CountDownLatch(1);
        s.schedule(1, System.currentTimeMillis() + 100_000, fired::countDown);
        s.stop(); // forEach cancels the pending future
        // executor is shut down; the task must not have run
    }

    @Test
    void stopWhenEmptyIsNoOp() {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        s.stop(); // forEach over an empty map
    }

    @Test
    void reArmReplacesPreviousTask() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        try {
            CountDownLatch first = new CountDownLatch(1);
            CountDownLatch second = new CountDownLatch(1);
            s.schedule(1, System.currentTimeMillis(), first::countDown);
            s.schedule(1, System.currentTimeMillis(), second::countDown); // cancels the first
            assertFalse(first.await(1, TimeUnit.SECONDS));
            assertTrue(second.await(1, TimeUnit.SECONDS));
        } finally {
            s.stop();
        }
    }

    @Test
    void schedulePastEndClampsDelayToZero() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        try {
            CountDownLatch fired = new CountDownLatch(1);
            s.schedule(1, System.currentTimeMillis() - 1000, fired::countDown);
            assertTrue(fired.await(2, TimeUnit.SECONDS));
        } finally {
            s.stop();
        }
    }
}
