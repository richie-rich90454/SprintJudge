package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiGradingServiceTest {

    @Test
    void disabledReturnsUnavailable() {
        AiGradingService svc = new AiGradingService();
        // enabled defaults to false via @Value("${sprintjudge.ai.enabled:false}")
        AiGradingService.AiGradeResult result = svc.grade("python", "print(1)", "Q1", "Desc", true, 100, 100);

        assertFalse(result.available());
        assertEquals("unavailable", result.status());
        assertEquals(0, result.suggestedScore());
    }
}
