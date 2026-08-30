package com.sprintjudge.domain.dto;

/**
 * Personal feedback after a coding submission is judged. Lets the player see
 * pass/fail immediately instead of waiting for the leaderboard to move.
 */
public record SubmissionResult(
        String type,
        String questionId,
        int score,
        boolean allPassed,
        int passed,
        int total
) {}
