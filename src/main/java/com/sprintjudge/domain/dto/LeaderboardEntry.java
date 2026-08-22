package com.sprintjudge.domain.dto;

public record LeaderboardEntry(
        String uuid,
        String name,
        int score,
        int rank
) {}
