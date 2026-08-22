package com.sprintjudge.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record RoundResult(
        String type,
        String questionId,
        boolean revealed,
        JsonNode correctAnswer,
        List<PlayerScore> scores
) {
    public record PlayerScore(String uuid, String name, int score, boolean correct) {}
}
