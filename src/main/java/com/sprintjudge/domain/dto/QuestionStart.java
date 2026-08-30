package com.sprintjudge.domain.dto;

public record QuestionStart(
        String type,
        QuestionDto question,
        int timeLimitSec,
        long startedAtEpochMs,
        long serverNowEpochMs
) {}
