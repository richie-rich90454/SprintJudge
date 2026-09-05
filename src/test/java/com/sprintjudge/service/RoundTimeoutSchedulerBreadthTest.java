package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundTimeoutSchedulerBreadthTest {

    @Test
    void twoPinsAreIndependentCancelOneOtherFires() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        try {
            CountDownLatch far = new CountDownLatch(1);
            CountDownLatch near = new CountDownLatch(1);
            s.schedule(11, System.currentTimeMillis() + 60_000, far::countDown);
            s.schedule(12, System.currentTimeMillis(), near::countDown);
            s.cancel(11);
            assertTrue(near.await(2, TimeUnit.SECONDS));
            assertFalse(far.await(200, TimeUnit.MILLISECONDS));
        } finally {
            s.stop();
        }
    }

    @Test
    void tripleScheduleOnlyLastFires() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        try {
            CountDownLatch first = new CountDownLatch(1);
            CountDownLatch second = new CountDownLatch(1);
            CountDownLatch third = new CountDownLatch(1);
            s.schedule(21, System.currentTimeMillis(), first::countDown);
            s.schedule(21, System.currentTimeMillis(), second::countDown);
            s.schedule(21, System.currentTimeMillis(), third::countDown);
            assertTrue(third.await(2, TimeUnit.SECONDS));
            assertFalse(first.await(200, TimeUnit.MILLISECONDS));
            assertFalse(second.await(200, TimeUnit.MILLISECONDS));
        } finally {
            s.stop();
        }
    }

    @Test
    void cancelAfterFireIsHarmless() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        try {
            CountDownLatch fired = new CountDownLatch(1);
            s.schedule(31, System.currentTimeMillis(), fired::countDown);
            assertTrue(fired.await(2, TimeUnit.SECONDS));
            s.cancel(31);
        } finally {
            s.stop();
        }
    }

    @Test
    void farFutureTaskDoesNotFireEarly() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        try {
            CountDownLatch fired = new CountDownLatch(1);
            s.schedule(41, System.currentTimeMillis() + 60_000, fired::countDown);
            assertFalse(fired.await(150, TimeUnit.MILLISECONDS));
            s.cancel(41);
            assertFalse(fired.await(150, TimeUnit.MILLISECONDS));
        } finally {
            s.stop();
        }
    }

    @Test
    void stopIsIdempotent() {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        s.stop();
        s.stop();
    }

    @Test
    void rescheduleAfterCancelFires() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        try {
            CountDownLatch first = new CountDownLatch(1);
            s.schedule(51, System.currentTimeMillis() + 60_000, first::countDown);
            s.cancel(51);
            CountDownLatch second = new CountDownLatch(1);
            s.schedule(51, System.currentTimeMillis(), second::countDown);
            assertTrue(second.await(2, TimeUnit.SECONDS));
            assertFalse(first.await(150, TimeUnit.MILLISECONDS));
        } finally {
            s.stop();
        }
    }

    @Test
    void fireRunsExactlyOnce() throws Exception {
        RoundTimeoutScheduler s = new RoundTimeoutScheduler();
        try {
            AtomicInteger count = new AtomicInteger();
            CountDownLatch fired = new CountDownLatch(1);
            s.schedule(61, System.currentTimeMillis(), () -> {
                count.incrementAndGet();
                fired.countDown();
            });
            assertTrue(fired.await(2, TimeUnit.SECONDS));
            Thread.sleep(300);
            assertEquals(1, count.get());
        } finally {
            s.stop();
        }
    }
}
