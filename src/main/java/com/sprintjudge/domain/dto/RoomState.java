package com.sprintjudge.domain.dto;

import java.util.List;

public record RoomState(
        String type,
        String status,
        int questionCount,
        int currentQuestionIndex,
        String currentQuestionId,
        List<PlayerInfo> players,
        String gameMode
) {
    /** Backward-compatible shape for callers that do not track the index yet. */
    public RoomState(String type, String status, int questionCount, String currentQuestionId,
                     List<PlayerInfo> players, String gameMode) {
        this(type, status, questionCount, 0, currentQuestionId, players, gameMode);
    }

    public record PlayerInfo(String uuid, String name, int score, boolean connected) {}
}
