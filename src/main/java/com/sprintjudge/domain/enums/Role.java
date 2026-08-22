package com.openquiz.domain.enums;

public enum Role {
    ADMIN,
    PLAYER;

    public static Role from(String value) {
        return Role.valueOf(value.trim().toUpperCase());
    }
}
