package com.openquiz.domain.models;

import java.time.Instant;
import java.util.List;

public record Question(
        String id,
        String quizId,
        String title,
        String description,
        String questionType,
        List<String> languagesAllowed,
        int timeLimitSec,
        int pointsBase,
        String config,
        int orderIndex,
        Instant createdAt
) {}
