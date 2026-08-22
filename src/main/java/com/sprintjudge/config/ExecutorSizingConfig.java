package com.openquiz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Semaphore;

@Configuration
public class ExecutorSizingConfig {

    /**
     * Judge concurrency budget: explicit count, or "auto" to derive from CPU
     * cores × factor (floor 8, cap 512). Q28: auto-derived sizing.
     */
    @Bean
    public Semaphore executionSlots(
            @Value("${openquiz.executor.max-concurrent:auto}") String maxConcurrent,
            @Value("${openquiz.executor.concurrency-factor:8}") int factor) {
        int permits = resolvePermits(maxConcurrent, factor);
        return new Semaphore(permits);
    }

    static int resolvePermits(String maxConcurrent, int factor) {
        if (maxConcurrent == null || maxConcurrent.isBlank()
                || maxConcurrent.trim().equalsIgnoreCase("auto")) {
            int cores = Runtime.getRuntime().availableProcessors();
            return Math.max(8, Math.min(512, cores * Math.max(1, factor)));
        }
        try {
            return Math.max(1, Integer.parseInt(maxConcurrent.trim()));
        } catch (NumberFormatException e) {
            int cores = Runtime.getRuntime().availableProcessors();
            return Math.max(8, Math.min(512, cores * Math.max(1, factor)));
        }
    }
}
