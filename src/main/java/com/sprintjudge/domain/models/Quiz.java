package com.sprintjudge.domain.models;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Quiz(
        String id,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        String createdBy,
        Instant createdAt,
        boolean template
) {}
