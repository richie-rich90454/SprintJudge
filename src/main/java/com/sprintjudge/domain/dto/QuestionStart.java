package com.sprintjudge.domain.dto;

public record QuestionStart(
        String type,
        QuestionDto question,
        long timeLimitSec,
        long startedAtEpochMs
) {}
