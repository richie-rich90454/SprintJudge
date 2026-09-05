package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringEngineFlowsTest {

    private final ScoringEngine engine = new ScoringEngine();

    private static Map<String, Object> att(int n) {
        return Map.of("mcq_max_attempts", n);
    }

    @Test
    void selectionFullInstantBase1000Scores1000() {
        assertEquals(1000, engine.scoreSelection(1.0, 0, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionFullHalfLimitScores750() {
        assertEquals(750, engine.scoreSelection(1.0, 15, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionFullAtLimitScores500() {
        assertEquals(500, engine.scoreSelection(1.0, 30, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionOverLimitClampsToFloor() {
        assertEquals(500, engine.scoreSelection(1.0, 1000, 30, 1, 1000, Map.of()));
        assertEquals(engine.scoreSelection(1.0, 30, 30, 1, 1000, Map.of()),
                engine.scoreSelection(1.0, 1000, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionHalfFractionInstantScores500() {
        assertEquals(500, engine.scoreSelection(0.5, 0, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionAlmostFullFractionInstantScores990() {
        assertEquals(990, engine.scoreSelection(0.99, 0, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionZeroFractionScoresZeroAtEveryTaken() {
        for (long taken : new long[]{0, 15, 30, 999}) {
            assertEquals(0, engine.scoreSelection(0.0, taken, 30, 1, 1000, Map.of()));
        }
    }

    @Test
    void selectionNegativeFractionScoresZero() {
        assertEquals(0, engine.scoreSelection(-0.5, 0, 30, 1, 1000, Map.of()));
        assertEquals(0, engine.scoreSelection(-2.0, 0, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionZeroBaseFallsBackTo1000Scale() {
        assertEquals(1000, engine.scoreSelection(1.0, 0, 30, 1, 0, Map.of()));
    }

    @Test
    void selectionBase100FullInstantScores100() {
        assertEquals(100, engine.scoreSelection(1.0, 0, 30, 1, 100, Map.of()));
    }

    @Test
    void selectionBase100AtLimitScores50() {
        assertEquals(50, engine.scoreSelection(1.0, 30, 30, 1, 100, Map.of()));
    }

    @Test
    void selectionSecondAttemptHalvesAward() {
        assertEquals(500, engine.scoreSelection(1.0, 0, 30, 2, 1000, att(5)));
    }

    @Test
    void selectionThirdAttemptQuartersAward() {
        assertEquals(250, engine.scoreSelection(1.0, 0, 30, 3, 1000, att(5)));
    }

    @Test
    void selectionFourthAttemptEighthsAward() {
        assertEquals(125, engine.scoreSelection(1.0, 0, 30, 4, 1000, att(5)));
    }

    @Test
    void selectionFifthAttemptRoundsHalfUpTo63() {
        assertEquals(63, engine.scoreSelection(1.0, 0, 30, 5, 1000, att(5)));
    }

    @Test
    void selectionCustomDecayBaseQuarterHalvesHarder() {
        ScoringEngine custom = new ScoringEngine(0.5, 0.25);
        assertEquals(250, custom.scoreSelection(1.0, 0, 30, 2, 1000, att(5)));
        assertEquals(63, custom.scoreSelection(1.0, 0, 30, 3, 1000, att(5)));
    }

    @Test
    void selectionCustomMinSpeedFloor800AtLimit() {
        ScoringEngine custom = new ScoringEngine(0.8, 0.5);
        assertEquals(800, custom.scoreSelection(1.0, 30, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionCustomMinSpeedInstantStill1000() {
        ScoringEngine custom = new ScoringEngine(0.8, 0.5);
        assertEquals(1000, custom.scoreSelection(1.0, 0, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionUnrelatedSettingsKeyMeansNoDecay() {
        Map<String, Object> s = Map.of("other", 1);
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, s),
                engine.scoreSelection(1.0, 0, 30, 5, 1000, s));
    }

    @Test
    void selectionNullSettingsMeansNoDecay() {
        assertEquals(1000, engine.scoreSelection(1.0, 0, 30, 1, 1000, null));
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, null),
                engine.scoreSelection(1.0, 0, 30, 9, 1000, null));
    }

    @Test
    void selectionIntegerAttemptsSettingDecays() {
        assertEquals(500, engine.scoreSelection(1.0, 0, 30, 2, 1000, att(3)));
    }

    @Test
    void selectionStringAttemptsSettingDecays() {
        Map<String, Object> s = Map.of("mcq_max_attempts", "4");
        assertEquals(500, engine.scoreSelection(1.0, 0, 30, 2, 1000, s));
    }

    @Test
    void selectionSingleAllowedAttemptIgnoresRetryCount() {
        Map<String, Object> s = att(1);
        assertEquals(engine.scoreSelection(1.0, 5, 30, 1, 1000, s),
                engine.scoreSelection(1.0, 5, 30, 9, 1000, s));
    }

    @Test
    void selectionMonotonicFasterNeverScoresLess() {
        long[] takens = {0, 5, 10, 15, 20, 25, 30, 60};
        int prev = Integer.MAX_VALUE;
        for (long taken : takens) {
            int score = engine.scoreSelection(1.0, taken, 30, 1, 1000, Map.of());
            assertTrue(score <= prev, "taken=" + taken);
            prev = score;
        }
    }

    @Test
    void selectionMonotonicEarlierAttemptNeverScoresLess() {
        int prev = Integer.MAX_VALUE;
        for (int attempt = 1; attempt <= 6; attempt++) {
            int score = engine.scoreSelection(1.0, 0, 30, attempt, 1000, att(9));
            assertTrue(score <= prev, "attempt=" + attempt);
            prev = score;
        }
    }

    @Test
    void selectionMonotonicHigherFractionNeverScoresLess() {
        double[] fractions = {0.1, 0.5, 0.99, 1.0};
        int prev = -1;
        for (double f : fractions) {
            int score = engine.scoreSelection(f, 10, 30, 1, 1000, Map.of());
            assertTrue(score >= prev, "fraction=" + f);
            prev = score;
        }
    }

    @Test
    void selectionPositiveFractionAlwaysYieldsAtLeastOne() {
        for (double f : new double[]{0.0001, 0.01, 0.5, 1.0}) {
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertTrue(engine.scoreSelection(f, 30, 30, attempt, 100, att(9)) >= 1);
            }
        }
    }

    @Test
    void selectionNegativeTakenWithDecayMatchesZeroTaken() {
        Map<String, Object> s = att(5);
        assertEquals(engine.scoreSelection(0.5, 0, 30, 2, 1000, s),
                engine.scoreSelection(0.5, -100, 30, 2, 1000, s));
    }

    @Test
    void selectionZeroTimeLimitTreatsWindowAsOneSecond() {
        assertEquals(500, engine.scoreSelection(1.0, 7, 0, 1, 1000, Map.of()));
    }

    @Test
    void selectionZeroLimitZeroTakenScoresFull() {
        assertEquals(1000, engine.scoreSelection(1.0, 0, 0, 1, 1000, Map.of()));
    }

    @Test
    void selectionLimitOneSecondBoundary() {
        assertEquals(1000, engine.scoreSelection(1.0, 0, 1, 1, 1000, Map.of()));
        assertEquals(500, engine.scoreSelection(1.0, 1, 1, 1, 1000, Map.of()));
    }

    @Test
    void selectionFraction99AtLimitScores495() {
        assertEquals(495, engine.scoreSelection(0.99, 30, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionFractionHalfAtLimitScores250() {
        assertEquals(250, engine.scoreSelection(0.5, 30, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionFractionHalfHalfLimitScores375() {
        assertEquals(375, engine.scoreSelection(0.5, 15, 30, 1, 1000, Map.of()));
    }

    @Test
    void selectionBase100FifthAttemptScores6() {
        assertEquals(6, engine.scoreSelection(1.0, 0, 30, 5, 100, att(5)));
    }

    @Test
    void selectionTinyFractionSmallBaseFloorsAtOne() {
        assertEquals(1, engine.scoreSelection(0.001, 30, 30, 1, 100, Map.of()));
    }

    @Test
    void selectionBaseScalesLinearly() {
        assertEquals(engine.scoreSelection(1.0, 0, 30, 1, 1000, Map.of()),
                10 * engine.scoreSelection(1.0, 0, 30, 1, 100, Map.of()));
    }

    @Test
    void selectionCustomBothParamsCombined() {
        ScoringEngine custom = new ScoringEngine(0.8, 0.25);
        assertEquals(63, custom.scoreSelection(1.0, 0, 30, 3, 1000, att(9)));
    }

    @Test
    void codingAllPassedInstantScoresBase() {
        assertEquals(1000, engine.scoreCoding(10, 10, 1000, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingAllPassedAtLimitScoresHalfBase() {
        assertEquals(500, engine.scoreCoding(10, 10, 1000, true, 30L, 30L, 1, Map.of()));
    }

    @Test
    void codingHalfUnsolvedScoresHalfRegardlessOfTime() {
        assertEquals(500, engine.scoreCoding(5, 10, 1000, false, 0L, 30L, 1, Map.of()));
        assertEquals(500, engine.scoreCoding(5, 10, 1000, false, 30L, 30L, 1, Map.of()));
    }

    @Test
    void codingHalfSolvedInstantScores500() {
        assertEquals(500, engine.scoreCoding(5, 10, 1000, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingHalfSolvedAtLimitScores250() {
        assertEquals(250, engine.scoreCoding(5, 10, 1000, true, 30L, 30L, 1, Map.of()));
    }

    @Test
    void codingZeroTotalScoresZero() {
        assertEquals(0, engine.scoreCoding(5, 0, 1000, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingZeroPassedScoresZeroEvenWhenSolved() {
        assertEquals(0, engine.scoreCoding(0, 10, 1000, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingZeroBaseFallsBackTo1000Scale() {
        assertEquals(1000, engine.scoreCoding(10, 10, 0, true, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingBase100PartialUnsolvedScores30() {
        assertEquals(30, engine.scoreCoding(3, 10, 100, false, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingOverLimitTakenClampsToLimit() {
        assertEquals(engine.scoreCoding(10, 10, 1000, true, 30L, 30L, 1, Map.of()),
                engine.scoreCoding(10, 10, 1000, true, 9999L, 30L, 1, Map.of()));
    }

    @Test
    void codingUnsolvedIgnoresTimeAtDifferentFraction() {
        assertEquals(engine.scoreCoding(3, 7, 1000, false, 0L, 30L, 1, Map.of()),
                engine.scoreCoding(3, 7, 1000, false, 500L, 30L, 1, Map.of()));
    }

    @Test
    void codingSecondAttemptHalvesPartial() {
        assertEquals(200, engine.scoreCoding(10, 10, 400, false, 0L, 30L, 2, att(3)));
    }

    @Test
    void codingThirdAttemptQuartersPartial() {
        assertEquals(100, engine.scoreCoding(10, 10, 400, false, 0L, 30L, 3, att(3)));
    }

    @Test
    void codingMissingAttemptKeyMeansNoDecay() {
        assertEquals(engine.scoreCoding(7, 10, 1000, true, 5L, 30L, 1, Map.of("x", 1)),
                engine.scoreCoding(7, 10, 1000, true, 5L, 30L, 8, Map.of("x", 1)));
    }

    @Test
    void codingMonotonicMorePassedNeverScoresLess() {
        int prev = -1;
        for (int passed = 0; passed <= 10; passed++) {
            int score = engine.scoreCoding(passed, 10, 1000, false, 0L, 30L, 1, Map.of());
            assertTrue(score >= prev, "passed=" + passed);
            prev = score;
        }
    }

    @Test
    void codingMonotonicFasterSolvedNeverScoresLess() {
        int prev = Integer.MAX_VALUE;
        for (long taken = 0; taken <= 40; taken += 5) {
            int score = engine.scoreCoding(10, 10, 1000, true, taken, 30L, 1, Map.of());
            assertTrue(score <= prev, "taken=" + taken);
            prev = score;
        }
    }

    @Test
    void codingSolvedLateScoresBelowUnsolvedSameFraction() {
        int solvedLate = engine.scoreCoding(5, 10, 1000, true, 30L, 30L, 1, Map.of());
        int unsolved = engine.scoreCoding(5, 10, 1000, false, 30L, 30L, 1, Map.of());
        assertTrue(solvedLate < unsolved);
    }

    @Test
    void codingPassedAboveTotalScalesLinearlyTo1500() {
        assertEquals(1500, engine.scoreCoding(15, 10, 1000, false, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingNegativeTakenSolvedScores1083() {
        assertEquals(1083, engine.scoreCoding(10, 10, 1000, true, -5L, 30L, 1, Map.of()));
    }

    @Test
    void codingZeroLimitSolvedInstantScoresFull() {
        assertEquals(1000, engine.scoreCoding(10, 10, 1000, true, 5L, 0L, 1, Map.of()));
    }

    @Test
    void codingSingleQuestionPartialRounds() {
        assertEquals(333, engine.scoreCoding(1, 3, 1000, false, 0L, 30L, 1, Map.of()));
    }

    @Test
    void codingSolvedInstantWithDecayOnSecondAttempt() {
        assertEquals(500, engine.scoreCoding(10, 10, 1000, true, 0L, 30L, 2, att(5)));
    }
}
