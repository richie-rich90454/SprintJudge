package com.sprintjudge.domain.dto;

public record TimerUpdate(
        String type,
        long newEndEpochMs,
        long extendSec
) {}
