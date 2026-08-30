package com.sprintjudge.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Pure scoring math. No I/O.
 * - MCQ / selection: correct -> linear speed-decay bonus, wrong -> 0.
 * - Coding (OJ): (passed/total) * base * speedDecay(if fully solved) * attemptMultiplier.
 * - Attempt multiplier (admin-configurable): 1st = 100%, 2nd = 50%, 3rd = 25%, ... (1/2^(n-1)).
 */
@Service
public class ScoringEngine {

    private final double minSpeedFraction;
    private final double attemptDecayBase;

    public ScoringEngine() {
        this(0.5, 0.5);
    }

    public ScoringEngine(double minSpeedFraction, double attemptDecayBase) {
        this.minSpeedFraction = minSpeedFraction;
        this.attemptDecayBase = attemptDecayBase;
    }

    /**
     * Selection scoring driven by correctness FRACTION (0..1) so MULTIPLE_SELECT
     * partial credit flows through the same speed-decay pipeline. Fraction 0
     * hard-zeroes; otherwise speed decay × attempt multiplier × base points,
     * scaled by the fraction. {@code basePoints} &le; 0 falls back to 1000 so
     * the fixed 0..1000 scale is preserved when a question omits it.
     */
    public int scoreSelection(double fraction, long timeTakenSec, long timeLimitSec,
                               int attemptsUsed, int basePoints, Map<String, Object> settings) {
        if (fraction <= 0.0) return 0;
        long limit = Math.max(1, timeLimitSec);
        long taken = Math.max(0, Math.min(timeTakenSec, limit));
        double speed = minSpeedFraction + (1 - minSpeedFraction) * (1.0 - (double) taken / limit);
        Map<String, Object> safeSettings = settings == null ? Map.of() : settings;
        double mult = attemptMultiplier(attemptsUsed, safeSettings);
        int base = basePoints > 0 ? basePoints : 1000;
        int award = (int) Math.round(speed * mult * fraction * base);
        return Math.max(1, award);
    }

    public int scoreCoding(int passed, int total, int basePoints, boolean fullySolved,
                           long timeTakenSec, long timeLimitSec, int attemptsUsed,
                           Map<String, Object> settings) {
        if (total <= 0) return 0;
        double fraction = (double) passed / total;
        double speed = fullySolved ? (minSpeedFraction + (1 - minSpeedFraction) * (1.0 - (double) Math.min(timeTakenSec, timeLimitSec) / Math.max(1, timeLimitSec))) : 1.0;
        double attemptMult = attemptMultiplier(attemptsUsed, settings);
        return (int) Math.round(fraction * basePoints * speed * attemptMult);
    }

    private double attemptMultiplier(int attemptsUsed, Map<String, Object> settings) {
        Object v = settings.get("mcq_max_attempts");
        int allowed = 1;
        if (v instanceof Number n) allowed = n.intValue();
        else if (v != null) {
            try { allowed = Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
        }
        if (allowed <= 1) return 1.0;
        return Math.pow(attemptDecayBase, Math.max(1, attemptsUsed) - 1);
    }
}
