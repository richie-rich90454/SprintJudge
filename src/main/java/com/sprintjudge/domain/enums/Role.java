package com.sprintjudge.domain.enums;

public enum Role {
    ADMIN,
    PLAYER;

    public static Role from(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Role is required");
        return Role.valueOf(value.trim().toUpperCase());
    }
}
