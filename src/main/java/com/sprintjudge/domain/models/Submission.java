package com.sprintjudge.domain.models;

import java.time.Instant;

public record Submission(
        String id,
        String gameSessionId,
        String questionId,
        String playerName,
        String playerUuid,
        String responseData,
        int scoreEarned,
        boolean correct,
        String judgeLog,
        int attemptCount,
        Instant submittedAt
) {}
