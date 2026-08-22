package com.openquiz.domain.dto;

public record TimerUpdate(
        String type,
        long newEndEpochMs,
        long extendSec
) {}
