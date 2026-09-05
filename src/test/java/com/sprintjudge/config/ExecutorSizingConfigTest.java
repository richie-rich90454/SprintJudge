package com.sprintjudge.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorSizingConfigTest {

    private final ExecutorSizingConfig config = new ExecutorSizingConfig();

    @Test
    void numericValueUsedDirectly() {
        assertEquals(4, ExecutorSizingConfig.resolvePermits("4", 8));
    }

    @Test
    void numericWithWhitespace() {
        assertEquals(42, ExecutorSizingConfig.resolvePermits("  42  ", 8));
    }

    @Test
    void zeroClampedToOne() {
        assertEquals(1, ExecutorSizingConfig.resolvePermits("0", 8));
    }

    @Test
    void negativeClampedToOne() {
        assertEquals(1, ExecutorSizingConfig.resolvePermits("-5", 8));
    }

    @Test
    void nullMeansAuto() {
        int cores = Runtime.getRuntime().availableProcessors();
        int expected = Math.max(8, Math.min(512, cores * 8));
        assertEquals(expected, ExecutorSizingConfig.resolvePermits(null, 8));
    }

    @Test
    void blankMeansAuto() {
        assertEquals(ExecutorSizingConfig.resolvePermits("auto", 8),
                ExecutorSizingConfig.resolvePermits("   ", 8));
    }

    @Test
    void autoCaseInsensitiveWithSpaces() {
        assertEquals(ExecutorSizingConfig.resolvePermits("auto", 8),
                ExecutorSizingConfig.resolvePermits(" AUTO ", 8));
        assertEquals(ExecutorSizingConfig.resolvePermits("auto", 8),
                ExecutorSizingConfig.resolvePermits("Auto", 8));
    }

    @Test
    void garbageFallsBackToAuto() {
        assertEquals(ExecutorSizingConfig.resolvePermits("auto", 8),
                ExecutorSizingConfig.resolvePermits("lots", 8));
        assertEquals(ExecutorSizingConfig.resolvePermits("auto", 8),
                ExecutorSizingConfig.resolvePermits("4x", 8));
    }

    @Test
    void autoFloorIsEight() {
        assertTrue(ExecutorSizingConfig.resolvePermits("auto", 0) >= 8);
        assertTrue(ExecutorSizingConfig.resolvePermits("auto", -3) >= 8);
    }

    @Test
    void autoCapIs512() {
        assertTrue(ExecutorSizingConfig.resolvePermits("auto", 10_000) <= 512);
    }

    @Test
    void beanReturnsSemaphoreWithPermits() {
        Semaphore s = config.executionSlots("3", 8);
        assertNotNull(s);
        assertEquals(3, s.availablePermits());
    }

    @Test
    void beanAutoResolves() {
        Semaphore s = config.executionSlots("auto", 8);
        assertTrue(s.availablePermits() >= 8);
    }
}
