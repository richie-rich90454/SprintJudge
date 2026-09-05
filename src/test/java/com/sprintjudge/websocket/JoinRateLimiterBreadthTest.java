package com.sprintjudge.websocket;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinRateLimiterBreadthTest {

    private final AtomicLong now = new AtomicLong(5_000_000L);

    private JoinRateLimiter limiter() {
        return new JoinRateLimiter(now::get);
    }

    @Test
    void tryJoinAloneNeverBlocks() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 20; i++) assertTrue(limiter.tryJoin("lonely"));
    }

    @Test
    void singleFailureDoesNotBlock() {
        JoinRateLimiter limiter = limiter();
        limiter.tryJoin("ip");
        limiter.recordFailure("ip");
        assertTrue(limiter.tryJoin("ip"));
    }

    @Test
    void nineFailuresStillAllowEntry() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 9; i++) {
            limiter.tryJoin("nine");
            limiter.recordFailure("nine");
        }
        assertTrue(limiter.tryJoin("nine"));
    }

    @Test
    void windowExactBoundaryStillBlocks() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.tryJoin("edge");
            limiter.recordFailure("edge");
        }
        assertFalse(limiter.tryJoin("edge"));
        now.addAndGet(60_000L);
        assertFalse(limiter.tryJoin("edge"));
    }

    @Test
    void afterExpiryCounterRestartsFromZero() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.tryJoin("re");
            limiter.recordFailure("re");
        }
        assertFalse(limiter.tryJoin("re"));
        now.addAndGet(60_001L);
        assertTrue(limiter.tryJoin("re"));
        limiter.recordFailure("re");
        assertTrue(limiter.tryJoin("re"));
    }

    @Test
    void successOnUnknownAddressIsNoop() {
        JoinRateLimiter limiter = limiter();
        limiter.recordSuccess("ghost");
        assertTrue(limiter.tryJoin("ghost"));
    }

    @Test
    void failureAfterExpiryStartsFreshWindow() {
        JoinRateLimiter limiter = limiter();
        limiter.tryJoin("fresh");
        limiter.recordFailure("fresh");
        now.addAndGet(120_000L);
        assertTrue(limiter.tryJoin("fresh"));
        limiter.recordFailure("fresh");
        assertTrue(limiter.tryJoin("fresh"));
    }

    @Test
    void productionClockAllowsFirstJoin() {
        assertTrue(new JoinRateLimiter().tryJoin("brand-new-ip"));
    }
}
