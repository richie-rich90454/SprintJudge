package com.sprintjudge.domain.dto;

import java.util.List;

public record RoomState(
        String type,
        String status,
        int questionCount,
        String currentQuestionId,
        List<PlayerInfo> players,
        String gameMode
) {
    public record PlayerInfo(String uuid, String name, int score, boolean connected) {}
}
