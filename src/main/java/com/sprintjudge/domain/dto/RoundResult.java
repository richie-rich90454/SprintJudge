package com.sprintjudge.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Round reveal. {@code correctAnswer} carries only the answer-bearing config
 * keys for the question type (never full config, never hidden OJ test cases).
 * {@code scores} reports each player's standing plus this round's contribution
 * so the client can show streaks / bonuses.
 */
public record RoundResult(
        String type,
        String questionId,
        boolean revealed,
        JsonNode correctAnswer,
        List<PlayerScore> scores
) {
    public record PlayerScore(String uuid, String name, int score, boolean correct,
                              int roundScore, int streak, int bonus) {}
}
