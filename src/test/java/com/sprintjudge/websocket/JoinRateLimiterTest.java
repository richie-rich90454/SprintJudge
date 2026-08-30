package com.sprintjudge.websocket;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private JoinRateLimiter limiter() {
        return new JoinRateLimiter(now::get);
    }

    @Test
    void firstTenFailuresAreAllowedThenBlocked() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryJoin("ip-a"), "join " + i + " should pass");
            limiter.recordFailure("ip-a");
        }
        assertFalse(limiter.tryJoin("ip-a"), "11th attempt must be blocked");
    }

    @Test
    void successResetsTheCounter() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 9; i++) {
            limiter.tryJoin("ip-b");
            limiter.recordFailure("ip-b");
        }
        assertTrue(limiter.tryJoin("ip-b"));
        limiter.recordSuccess("ip-b");
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryJoin("ip-b"));
            limiter.recordFailure("ip-b");
        }
        assertFalse(limiter.tryJoin("ip-b"));
    }

    @Test
    void windowExpiryRestoresAccess() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.tryJoin("ip-c");
            limiter.recordFailure("ip-c");
        }
        assertFalse(limiter.tryJoin("ip-c"));
        now.addAndGet(60_001L);          // past the window
        assertTrue(limiter.tryJoin("ip-c"));
    }

    @Test
    void addressesAreIndependent() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.tryJoin("ip-x");
            limiter.recordFailure("ip-x");
        }
        assertFalse(limiter.tryJoin("ip-x"));
        assertTrue(limiter.tryJoin("ip-y"));
    }

    @Test
    void unknownAddressFailureIsHarmless() {
        limiter().recordFailure("never-seen");
    }

    @Test
    void expiredWindowsArePerAddress() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.tryJoin("ip-1");
            limiter.recordFailure("ip-1");
        }
        now.addAndGet(60_001L);
        assertTrue(limiter.tryJoin("ip-1"));
        assertTrue(limiter.tryJoin("ip-2"));   // untouched address unaffected by expiry sweep
    }

    @Test
    void exceedingMaxTrackedTriggersCleanupSweep() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 10_001; i++) {
            limiter.tryJoin("swept-" + i);
        }
        // 10_001 tracked > MAX_TRACKED(10_000): the next join exercises the sweep branch.
        assertTrue(limiter.tryJoin("swept-new"));
    }

    @Test
    void productionConstructorBlocksAfterTenFailures() {
        JoinRateLimiter limiter = new JoinRateLimiter(); // wall-clock ctor (line 25-27)
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryJoin("ip-prod"));
            limiter.recordFailure("ip-prod");
        }
        assertFalse(limiter.tryJoin("ip-prod"));
    }

    @Test
    void sweepRemovesExpiredEntries() {
        JoinRateLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.tryJoin("old-ip");
            limiter.recordFailure("old-ip");
        }
        assertFalse(limiter.tryJoin("old-ip"));
        now.addAndGet(120_000L);          // old-ip window now expired
        for (int i = 0; i < 10_001; i++) {
            limiter.tryJoin("sweep2-" + i);
        }
        // expired entry was swept out, so a fresh window is created -> allowed again
        assertTrue(limiter.tryJoin("old-ip"));
    }
}
