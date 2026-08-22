package com.sprintjudge.domain.models;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Question(
        String id,
        String quizId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        String questionType,
        List<String> languagesAllowed,
        @Min(1) int timeLimitSec,
        @Min(0) int pointsBase,
        String config,
        int orderIndex,
        Instant createdAt
) {}
