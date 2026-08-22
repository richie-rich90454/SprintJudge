package com.sprintjudge.domain.enums;

public enum GameStatus {
    LOBBY,
    ACTIVE,
    REVIEW,
    ENDED;

    public static GameStatus from(String value) {
        return GameStatus.valueOf(value.trim().toUpperCase());
    }
}
