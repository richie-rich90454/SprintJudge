package com.sprintjudge.service;

public record Player(
        String uuid,
        String name,
        int score,
        String sessionId,
        boolean connected,
        String token
) {
    public Player(String uuid, String name, int score, String sessionId, boolean connected) {
        this(uuid, name, score, sessionId, connected, null);
    }

    public Player withScore(int score) {
        return new Player(uuid, name, score, sessionId, connected, token);
    }

    public Player withSession(String sessionId) {
        return new Player(uuid, name, score, sessionId, true, token);
    }

    public Player disconnected() {
        return new Player(uuid, name, score, sessionId, false, token);
    }
}
