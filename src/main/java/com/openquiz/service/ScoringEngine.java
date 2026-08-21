package com.openquiz.service;

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

    public int scoreSelection(boolean correct, long timeTakenSec, long timeLimitSec, int attemptsUsed, Map<String, Object> settings) {
        if (!correct) return 0;
        long limit = Math.max(1, timeLimitSec);
        long taken = Math.max(0, Math.min(timeTakenSec, limit));
        double speed = minSpeedFraction + (1 - minSpeedFraction) * (1.0 - (double) taken / limit);
        double attemptMult = attemptMultiplier(attemptsUsed, settings);
        return (int) Math.round(speed * attemptMult * 1000) / 10 * 10;
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
        int allowed = parseInt(settings, "mcq_max_attempts", 1);
        if (allowed <= 1) return 1.0;
        int n = Math.max(1, attemptsUsed);
        return Math.pow(attemptDecayBase, n - 1);
    }

    private int parseInt(Map<String, Object> settings, String key, int def) {
        Object v = settings.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
