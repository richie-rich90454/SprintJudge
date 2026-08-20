package com.openquiz.domain.models;

import java.time.Instant;

public record Quiz(
        String id,
        String title,
        String description,
        String createdBy,
        Instant createdAt,
        boolean template
) {}
