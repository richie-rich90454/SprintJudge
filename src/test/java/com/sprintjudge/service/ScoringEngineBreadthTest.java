package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringEngineBreadthTest {

    private final ScoringEngine engine = new ScoringEngine();

    private static Map<String, Object> attempts(String v) {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", v);
        return s;
    }

    @Test
    void negativeTakenClampsToZero() {
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, Map.of()),
                engine.scoreSelection(1.0, -50, 30, 1, 1000, Map.of()));
    }

    @Test
    void takenExactlyAtLimitHitsFloor() {
        assertEquals(500, engine.scoreSelection(1.0, 30, 30, 1, 1000, Map.of()));
    }

    @Test
    void negativeLimitFallsBackToOneSecondWindow() {
        assertEquals(500, engine.scoreSelection(1.0, 5, -10, 1, 1000, Map.of()));
    }

    @Test
    void negativeBasePointsFallsBackTo1000() {
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, Map.of()),
                engine.scoreSelection(1.0, 0, 30, 1, -250, Map.of()));
    }

    @Test
    void fractionAboveOneScalesLinearly() {
        assertEquals(1500, engine.scoreSelection(1.5, 0, 30, 1, 1000, Map.of()));
    }

    @Test
    void tinyPositiveFractionFloorsAtOne() {
        assertEquals(1, engine.scoreSelection(0.0001, 30, 30, 1, 1000, Map.of()));
    }

    @Test
    void zeroAttemptsCountsAsFirstAttempt() {
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, attempts("5")),
                engine.scoreSelection(1.0, 0, 30, 0, 1000, attempts("5")));
    }

    @Test
    void negativeAttemptsCountsAsFirstAttempt() {
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, attempts("5")),
                engine.scoreSelection(1.0, 0, 30, -3, 1000, attempts("5")));
    }

    @Test
    void zeroAllowedAttemptsMeansNoDecay() {
        Map<String, Object> s = attempts("0");
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, s),
                engine.scoreSelection(1.0, 0, 30, 9, 1000, s));
    }

    @Test
    void negativeAllowedAttemptsMeansNoDecay() {
        Map<String, Object> s = attempts("-2");
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, s),
                engine.scoreSelection(1.0, 0, 30, 9, 1000, s));
    }

    @Test
    void doubleSettingValueIsAccepted() {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", 3.0);
        assertEquals(500, engine.scoreSelection(1.0, 0, 30, 2, 1000, s));
    }

    @Test
    void longSettingValueIsAccepted() {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", 2L);
        assertEquals(500, engine.scoreSelection(1.0, 0, 30, 2, 1000, s));
    }

    @Test
    void booleanSettingValueFallsBackToSingleAttempt() {
        Map<String, Object> s = new HashMap<>();
        s.put("mcq_max_attempts", Boolean.TRUE);
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, s),
                engine.scoreSelection(1.0, 0, 30, 9, 1000, s));
    }

    @Test
    void whitespaceSettingValueFallsBackToSingleAttempt() {
        Map<String, Object> s = attempts(" 3 ");
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, s),
                engine.scoreSelection(1.0, 0, 30, 9, 1000, s));
    }

    @Test
    void missingKeyInNonEmptyMapMeansNoDecay() {
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, Map.of("other", "x")),
                engine.scoreSelection(1.0, 0, 30, 9, 1000, Map.of("other", "x")));
    }

    @Test
    void minSpeedFractionOneRemovesTimePressure() {
        ScoringEngine flat = new ScoringEngine(1.0, 0.5);
        assertEquals(flat.scoreSelection(1.0, 0, 30, 1, 1000, Map.of()),
                flat.scoreSelection(1.0, 30, 30, 1, 1000, Map.of()));
    }

    @Test
    void minSpeedFractionZeroFloorsAtOnePoint() {
        ScoringEngine harsh = new ScoringEngine(0.0, 0.5);
        assertEquals(1, harsh.scoreSelection(1.0, 30, 30, 1, 1000, Map.of()));
    }

    @Test
    void attemptDecayBaseOneRemovesRetryPenalty() {
        ScoringEngine forgiving = new ScoringEngine(0.5, 1.0);
        Map<String, Object> s = attempts("5");
        assertEquals(forgiving.scoreSelection(1.0, 0, 30, 1, 1000, s),
                forgiving.scoreSelection(1.0, 0, 30, 5, 1000, s));
    }

    @Test
    void attemptDecayBaseZeroZeroesEveryRetry() {
        ScoringEngine brutal = new ScoringEngine(0.5, 0.0);
        assertEquals(0, brutal.scoreCoding(10, 10, 1000, false, 0L, 30L, 2, attempts("5")));
    }

    @Test
    void codingPassedAboveTotalScalesPastBase() {
        assertEquals(600, engine.scoreCoding(12, 10, 500, false, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingNegativePassedYieldsNegativeScore() {
        assertEquals(-50, engine.scoreCoding(-1, 10, 500, false, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingNegativeTotalYieldsZero() {
        assertEquals(0, engine.scoreCoding(5, -10, 500, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingZeroBaseFallsBackTo1000() {
        assertEquals(1000, engine.scoreCoding(10, 10, 0, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingNegativeBaseFallsBackTo1000() {
        assertEquals(1000, engine.scoreCoding(10, 10, -5, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingNullSettingsThrows() {
        assertThrows(NullPointerException.class,
                () -> engine.scoreCoding(10, 10, 500, true, 0L, 30L, 1, null));
    }

    @Test
    void codingEmptySettingsMeansNoAttemptDecay() {
        assertEquals(engine.scoreCoding(10, 10, 500, false, 0L, 30L, 1, Map.of()),
                engine.scoreCoding(10, 10, 500, false, 0L, 30L, 9, Map.of()));
    }

    @Test
    void codingNegativeTakenExceedsBase() {
        assertTrue(engine.scoreCoding(10, 10, 1000, true, -5L, 30L, 1, Map.of()) > 1000);
    }

    @Test
    void codingUnsolvedIgnoresTimeTaken() {
        assertEquals(engine.scoreCoding(5, 10, 1000, false, 0L, 30L, 1, Map.of()),
                engine.scoreCoding(5, 10, 1000, false, 9999L, 30L, 1, Map.of()));
    }

    @Test
    void codingZeroLimitDoesNotDivideByZero() {
        assertTrue(engine.scoreCoding(10, 10, 1000, true, 5L, 0L, 1, Map.of()) >= 0);
    }

    @Test
    void codingZeroPassedYieldsZero() {
        assertEquals(0, engine.scoreCoding(0, 10, 1000, false, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingStringAttemptsSettingDecays() {
        int first = engine.scoreCoding(10, 10, 400, false, 0L, 30L, 1, attempts("3"));
        int second = engine.scoreCoding(10, 10, 400, false, 0L, 30L, 2, attempts("3"));
        assertEquals(first / 2, second, 20);
    }
}
