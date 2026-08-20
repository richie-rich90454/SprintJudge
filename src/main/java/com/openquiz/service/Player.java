package com.openquiz.service;

public record Player(
        String uuid,
        String name,
        int score,
        String sessionId,
        boolean connected
) {
    public Player withScore(int score) {
        return new Player(uuid, name, score, sessionId, connected);
    }

    public Player withSession(String sessionId) {
        return new Player(uuid, name, score, sessionId, true);
    }

    public Player disconnected() {
        return new Player(uuid, name, score, sessionId, false);
    }
}
