package com.sprintjudge.domain.models;

import java.time.Instant;

public record User(
        String id,
        String email,
        String name,
        String avatarUrl,
        String role,
        Instant createdAt
) {}
