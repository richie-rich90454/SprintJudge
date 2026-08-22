package com.sprintjudge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();

    // ---------- selection scoring ----------

    @Test
    void wrongAnswerScoresZero() {
        assertEquals(0, engine.scoreSelection(0.0, 0, 30, 1, Map.of()));
    }

    @ParameterizedTest
    @CsvSource({
        "0,1000",   // instant -> 100%
        "3,950",    // 90% remaining
        "6,900",
        "9,850",
        "12,800",
        "15,750",
        "18,700",
        "21,650",
        "24,600",
        "27,550",
        "30,500"    // full time -> floor (minSpeedFraction)
    })
    void selectionSpeedDecay(long taken, int expected) {
        assertEquals(expected, engine.scoreSelection(1.0, taken, 30, 1, Map.of()), 20);
    }

    @Test
    void overtimeIsClampedToLimit() {
        int late = engine.scoreSelection(1.0, 999, 30, 1, Map.of());
        assertEquals(500, late, 20);
    }

    @Test
    void zeroOrNegativeLimitDoesNotDivideByZero() {
        assertTrue(engine.scoreSelection(1.0, 5, 0, 1, Map.of()) >= 0);
    }

    @ParameterizedTest
    @CsvSource({"1,1000", "2,500", "3,250", "4,125", "5,60"})
    void attemptDecayHalvesEachTime(int attempts, int expected) {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", "5");
        assertEquals(expected, engine.scoreSelection(1.0, 0, 30, attempts, s), 20);
    }

    @Test
    void singleAttemptPolicyIgnoresAttemptCount() {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", "1");
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, s),
                     engine.scoreSelection(1.0, 0, 30, 7, s));
    }

    @ParameterizedTest
    @ValueSource(strings = {"garbage", ""})
    void unparsableSettingFallsBackToSingleAttempt(String raw) {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", raw);
        long first = engine.scoreSelection(1.0, 0, 30, 1, s);
        assertEquals(first, engine.scoreSelection(1.0, 0, 30, 9, s));
    }

    @Test
    void numericSettingTypeIsAccepted() {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", 3);   // Integer instead of String
        int second = engine.scoreSelection(1.0, 0, 30, 2, s);
        assertEquals(500, second, 20);
    }

    @Test
    void nullSettingMapMeansFirstAttemptFullScore() {
        assertEquals(1000, engine.scoreSelection(1.0, 0, 30, 4, null), 20);
    }

    // ---------- coding scoring ----------

    @Test
    void codingScoreScalesWithPassedFraction() {
        assertEquals(500, engine.scoreCoding(5, 5, 500, true, 0L, 30L, 1, Map.of()));
        assertEquals(250, engine.scoreCoding(2, 4, 500, false, 0L, 30L, 1, Map.of()));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,50", "2,100", "3,150", "4,200", "10,500"})
    void partialPassFractionsAreLinear(int passed, int expected) {
        assertEquals(expected, engine.scoreCoding(passed, 10, 500, false, 99L, 100L, 1, Map.of()));
    }

    @Test
    void fullySolvedAddsSpeedBonus() {
        int fast = engine.scoreCoding(10, 10, 1000, true, 0L, 100L, 1, Map.of());
        int slow = engine.scoreCoding(10, 10, 1000, true, 100L, 100L, 1, Map.of());
        assertEquals(1000, fast, 20);
        assertEquals(500, slow, 20);
    }

    @Test
    void unsolvedPartialGetsNoSpeedBonus() {
        assertEquals(500, engine.scoreCoding(5, 10, 1000, false, 0L, 100L, 1, Map.of()));
    }

    @Test
    void zeroTotalTestsYieldZero() {
        assertEquals(0, engine.scoreCoding(0, 0, 500, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void attemptMultiplierAppliesToCodingToo() {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", "2");
        int first = engine.scoreCoding(10, 10, 400, false, 0L, 30L, 1, s);
        int second = engine.scoreCoding(10, 10, 400, false, 0L, 30L, 2, s);
        assertEquals(first / 2, second, 20);
    }

    // ---------- constructor tuning ----------

    @Test
    void customMinSpeedFractionRaisesTheFloor() {
        ScoringEngine generous = new ScoringEngine(0.8, 0.5);
        int floor = generous.scoreSelection(1.0, 30, 30, 1, Map.of());
        assertEquals(800, floor, 20);
    }

    @Test
    void customAttemptDecayBaseFlattensPenalty() {
        ScoringEngine gentle = new ScoringEngine(0.5, 0.9);   // 90% retained per retry
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", "3");
        int third = gentle.scoreSelection(1.0, 0, 30, 3, s);
        assertEquals(810, third, 20);
    }

    @Test
    void fractionGatesZeroButScalingLivesInCaller() {
        // Engine treats any fraction > 0 as "eligible for decay bonus";
        // GameRoomManager multiplies by the fraction. Documented contract.
        double full = engine.scoreSelection(1.0, 0, 30, 1, Map.of());
        double half = engine.scoreSelection(0.5, 0, 30, 1, Map.of());
        assertEquals(full, half, 20);
        assertEquals(0, engine.scoreSelection(0.0, 0, 30, 1, Map.of()));
    }

    @Test
    void negativeFractionHardZeroes() {
        assertEquals(0, engine.scoreSelection(-0.5, 0, 30, 1, Map.of()));
    }
}

