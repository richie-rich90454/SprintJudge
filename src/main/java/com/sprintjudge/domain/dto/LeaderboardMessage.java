package com.sprintjudge.domain.dto;

import java.util.List;

public record LeaderboardMessage(
        String type,
        List<LeaderboardEntry> rankings
) {}
