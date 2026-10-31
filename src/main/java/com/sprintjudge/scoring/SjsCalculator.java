package com.sprintjudge.scoring;

/**
 * SprintJudge Score: accuracy-first composite with explainable parts.
 * Pure math, no I/O. Weights: accuracy 50, speed 20, consistency 10,
 * improvement 15, mastery 5. Response-time credit is linear between the
 * calibrated quartiles so careful students lose little.
 */
public final class SjsCalculator {

    private SjsCalculator() {
    }

    /** Difficulty weight from DOK, calibrated p-value, and format. */
    public static double difficultyWeight(int dok, double pValue, String format) {
        double dokWeight = switch (dok) {
            case 1 -> 1.0;
            case 2 -> 1.5;
            case 3 -> 2.5;
            case 4 -> 4.0;
            default -> 1.0;
        };
        double difficulty = 1.0 + (0.5 - clamp(pValue, 0.0, 1.0));
        difficulty = clamp(difficulty, 0.5, 1.5);
        return dokWeight * difficulty * formatMultiplier(format);
    }

    /** Linear speed credit between quartiles: fast earns 1, slow earns 0.25. */
    public static double speedCredit(double seconds, double p25, double median, double p75) {
        if (seconds <= p25) {
            return 1.0;
        }
        if (seconds >= p75) {
            return 0.25;
        }
        if (seconds <= median) {
            double span = Math.max(0.001, median - p25);
            return 1.0 - 0.25 * ((seconds - p25) / span);
        }
        double span = Math.max(0.001, p75 - median);
        return 0.75 - 0.5 * ((seconds - median) / span);
    }

    /** Plain-language speed-accuracy signal for students and teachers. */
    public static String speedAccuracySignal(double medianSeconds, double questionMedian, double accuracy) {
        boolean fast = medianSeconds <= questionMedian;
        boolean accurate = accuracy >= 0.7;
        if (fast && accurate) {
            return "FLUENT";
        }
        if (fast) {
            return "GUESSING";
        }
        if (accurate) {
            return "CAREFUL";
        }
        return "STRUGGLING";
    }

    /** Public tier; the lower half is never named on shared displays. */
    public static String tier(double sjs) {
        if (sjs >= 85.0) {
            return "Gold";
        }
        if (sjs >= 70.0) {
            return "Silver";
        }
        if (sjs >= 55.0) {
            return "Bronze";
        }
        if (sjs >= 40.0) {
            return "Rising";
        }
        return "Practicing";
    }

    /** Weighted composite of the five dimensions, each 0..100. */
    public static double composite(double accuracy, double speed, double consistency,
                                   double improvement, double mastery) {
        return 0.50 * accuracy + 0.20 * speed + 0.10 * consistency + 0.15 * improvement + 0.05 * mastery;
    }

    private static double formatMultiplier(String format) {
        if (format == null) {
            return 1.0;
        }
        return switch (format) {
            case "MULTI_SELECT", "NUMERIC" -> 1.2;
            case "ORDERING" -> 1.3;
            case "CODE", "OJ_FULL", "OJ_PATCH", "CODE_COMPLETION" -> 1.5;
            case "FREE_RESPONSE", "COMPOSITION", "PROOF" -> 2.0;
            default -> 1.0;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}
