package com.sprintjudge.domain.models;

import java.time.Instant;

public record AdminSetting(
        String key,
        String value,
        Instant updatedAt
) {}
