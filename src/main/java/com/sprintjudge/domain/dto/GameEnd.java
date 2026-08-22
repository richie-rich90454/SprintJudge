package com.sprintjudge.domain.dto;

import java.util.List;

public record GameEnd(
        String type,
        List<LeaderboardEntry> rankings
) {}
