package com.sprintjudge.domain.dto;

import java.util.List;

public record RoomState(
        String type,
        String status,
        int questionCount,
        List<PlayerInfo> players
) {
    public record PlayerInfo(String uuid, String name, int score) {}
}
