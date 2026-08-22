package com.sprintjudge.domain.models;

import java.time.Instant;

public record GameSession(
        String id,
        String quizId,
        String pinCode,
        String hostUserId,
        String status,
        int currentQuestionIndex,
        Instant startedAt,
        Instant endedAt,
        String settingsOverride,
        Instant createdAt
) {}
