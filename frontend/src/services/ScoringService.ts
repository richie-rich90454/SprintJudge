import { QuestionType } from "../types";

/**
 * Client-side helpers shared by player views.
 */
export function isCoding(type: QuestionType): boolean {
    return type === "OJ_FULL" || type === "OJ_PATCH";
}

export type SpeedAccuracySignal = "FLUENT" | "GUESSING" | "CAREFUL" | "STRUGGLING";

/**
 * Plain-language speed-accuracy signal mirroring the backend calculator.
 */
export function speedAccuracySignal(
    medianSeconds: number,
    questionMedian: number,
    accuracy: number,
): SpeedAccuracySignal {
    const fast = medianSeconds <= questionMedian;
    const accurate = accuracy >= 0.7;
    if (fast && accurate) return "FLUENT";
    if (fast) return "GUESSING";
    if (accurate) return "CAREFUL";
    return "STRUGGLING";
}

/** Public tier; the lower half is never named on shared displays. */
export function tierForScore(sjs: number): string {
    if (sjs >= 85) return "Gold";
    if (sjs >= 70) return "Silver";
    if (sjs >= 55) return "Bronze";
    if (sjs >= 40) return "Rising";
    return "Practicing";
}
