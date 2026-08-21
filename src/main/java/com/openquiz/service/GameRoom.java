package com.openquiz.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live, in-memory state for a single game session. The {@link GameRoomManager}
 * owns the map of these; this class is a plain mutable aggregate.
 */
public class GameRoom {

    private final String sessionId;
    private final String quizId;
    private final String pin;
    private String status;
    private int currentQuestionIndex;
    private long currentQuestionEndEpochMs;
    private String hostUuid;
    private final Map<String, Player> players = new LinkedHashMap<>();

    public GameRoom(String sessionId, String quizId, String pin, String status) {
        this.sessionId = sessionId;
        this.quizId = quizId;
        this.pin = pin;
        this.status = status;
    }

    public synchronized void addPlayer(Player p) {
        players.put(p.uuid(), p);
    }

    public synchronized void removePlayer(String uuid) {
        players.remove(uuid);
    }

    public synchronized Player getPlayer(String uuid) {
        return players.get(uuid);
    }

    public synchronized void applyScore(String uuid, int additional) {
        Player p = players.get(uuid);
        if (p != null) players.put(uuid, p.withScore(p.score() + additional));
    }

    public synchronized List<Player> players() {
        return new ArrayList<>(players.values());
    }

    public synchronized List<com.openquiz.domain.dto.LeaderboardEntry> leaderboard() {
        List<Player> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<com.openquiz.domain.dto.LeaderboardEntry> out = new ArrayList<>();
        int rank = 1;
        for (Player p : sorted) {
            out.add(new com.openquiz.domain.dto.LeaderboardEntry(p.uuid(), p.name(), p.score(), rank++));
        }
        return out;
    }

    public String sessionId() { return sessionId; }
    public String quizId() { return quizId; }
    public String pin() { return pin; }
    public String status() { return status; }
    public void setStatus(String status) { this.status = status; }
    public synchronized String hostUuid() { return hostUuid; }
    public synchronized void setHostUuid(String hostUuid) { this.hostUuid = hostUuid; }
    public int currentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int i) { this.currentQuestionIndex = i; }
    public long currentQuestionEndEpochMs() { return currentQuestionEndEpochMs; }
    public void setCurrentQuestionEndEpochMs(long ms) { this.currentQuestionEndEpochMs = ms; }
}
