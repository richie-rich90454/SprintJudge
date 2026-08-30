package com.sprintjudge.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Post-game review data sent to all players after the game ends.
 * Contains answer key, per-student results, and class-wide difficulty analysis.
 */
public record GameReview(
        String type,
        List<LeaderboardEntry> rankings,
        List<QuestionReview> questions,
        List<PlayerReview> players,
        ClassStats classStats
) {
    public record QuestionReview(
            String questionId,
            String title,
            String questionType,
            int timeLimitSec,
            int pointsBase,
            JsonNode answer,
            int totalAttempts,
            int correctCount,
            double correctRate,
            double avgTimeSec
    ) {}

    public record PlayerReview(
            String playerUuid,
            String playerName,
            int totalScore,
            List<PlayerAnswer> answers
    ) {}

    public record PlayerAnswer(
            String questionId,
            boolean correct,
            int scoreEarned,
            int attemptCount
    ) {}

    public record ClassStats(
            int totalPlayers,
            int totalQuestions,
            double avgScore,
            int totalCorrect,
            int totalAttempts,
            String hardestQuestionId,
            String easiestQuestionId
    ) {}
}
