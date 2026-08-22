package com.openquiz.domain.dto;

public record LeaderboardEntry(
        String uuid,
        String name,
        int score,
        int rank
) {}
