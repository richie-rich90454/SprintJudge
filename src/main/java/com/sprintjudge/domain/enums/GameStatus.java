package com.sprintjudge.domain.enums;

public enum GameStatus {
    LOBBY,
    ACTIVE,
    REVIEW,
    ENDED;

    public static GameStatus from(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Game status is required");
        return GameStatus.valueOf(value.trim().toUpperCase());
    }
}
