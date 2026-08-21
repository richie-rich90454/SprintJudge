package com.openquiz.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();

    @Test
    void wrongAnswerScoresZero() {
        assertEquals(0, engine.scoreSelection(false, 0, 30, 1, Map.of()));
    }

    @ParameterizedTest
    @CsvSource({
        "0,1000",   // instant answer -> full base
        "15,750",   // half time -> 75% of base
        "30,500"    // full time -> minimum speed fraction (50%)
    })
    void selectionSpeedDecay(long taken, int expectedApprox) {
        int score = engine.scoreSelection(true, taken, 30, 1, Map.of());
        assertEquals(expectedApprox, score, 20);
    }

    @Test
    void secondAttemptHalvesScore() {
        int first = engine.scoreSelection(true, 0, 30, 1, Map.of("mcq_max_attempts", "2"));
        int second = engine.scoreSelection(true, 0, 30, 2, Map.of("mcq_max_attempts", "2"));
        assertEquals(first / 2, second, 20);
    }

    @Test
    void codingScoreScalesWithPassedFraction() {
        int full = engine.scoreCoding(5, 5, 500, true, 0L, 30L, 1, Map.of());
        int half = engine.scoreCoding(2, 4, 500, false, 0L, 30L, 1, Map.of());
        assertEquals(500, full);
        assertEquals(250, half);
    }
}
