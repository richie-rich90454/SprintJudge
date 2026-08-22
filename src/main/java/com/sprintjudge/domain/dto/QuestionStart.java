package com.openquiz.domain.dto;

public record QuestionStart(
        String type,
        QuestionDto question,
        long timeLimitSec,
        long startedAtEpochMs
) {}
