package com.sprintjudge.service;

/**
 * Reports a judged coding submission back to the owning room so it can apply
 * the score (plus streak bonus), notify the player individually, and refresh
 * the leaderboard. Carries only the outcome the manager needs; the processor
 * never reaches room state directly.
 */
public interface CodingOutcomeConsumer {
    void accept(String playerUuid, int baseScore, boolean allPassed, int passed, int total, String aiFeedback);

    /**
     * The submission never reached the judge (unknown question, misrouted
     * type, bad source, judge crash). Default: same visible outcome as a
     * zero-score run; the manager overrides to also refund the attempt.
     */
    default void rejected(String playerUuid) {
        accept(playerUuid, 0, false, 0, 0, null);
    }
}
