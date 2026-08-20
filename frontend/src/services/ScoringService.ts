import { QuestionType } from "../types";

/**
 * Client-side mirror of the backend ScoringEngine, used for live preview in
 * the admin wizard and the result screen.
 */
export class ScoringService {
  private minSpeedFraction = 0.5;
  private attemptDecayBase = 0.5;

  scoreSelection(correct: boolean, timeTakenSec: number, timeLimitSec: number, attemptsUsed: number): number {
    if (!correct) return 0;
    const limit = Math.max(1, timeLimitSec);
    const taken = Math.max(0, Math.min(timeTakenSec, limit));
    const speed = this.minSpeedFraction + (1 - this.minSpeedFraction) * (1 - taken / limit);
    return Math.round(speed * this.attemptMultiplier(attemptsUsed));
  }

  scoreCoding(passed: number, total: number, basePoints: number, fullySolved: boolean, timeTakenSec: number, timeLimitSec: number): number {
    if (total <= 0) return 0;
    const fraction = passed / total;
    const speed = fullySolved
      ? this.minSpeedFraction + (1 - this.minSpeedFraction) * (1 - Math.min(timeTakenSec, timeLimitSec) / Math.max(1, timeLimitSec))
      : 1;
    return Math.round(fraction * basePoints * speed);
  }

  private attemptMultiplier(attemptsUsed: number): number {
    return Math.pow(this.attemptDecayBase, Math.max(1, attemptsUsed) - 1);
  }
}

export function isCoding(type: QuestionType): boolean {
  return type === "OJ_FULL" || type === "OJ_PATCH";
}
