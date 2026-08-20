package com.openquiz.domain.dto;

import java.util.List;

public record QuestionStart(
        String type,
        QuestionDto question,
        long timeLimitSec,
        long startedAtEpochMs
) {}
