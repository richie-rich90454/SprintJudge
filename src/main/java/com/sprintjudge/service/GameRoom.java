package com.sprintjudge.service;

import com.sprintjudge.domain.dto.LeaderboardEntry;
import com.sprintjudge.service.leaderboard.LiveLeaderboard;
import com.sprintjudge.service.leaderboard.RankedSkipList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live, in-memory state for a single game room.
 *
 * <p>Identity/sessions live in a lock-free map; ranking truth lives in the
 * exact {@link LiveLeaderboard} (order-statistic skip list + delta ledger).
 * Score mutations are O(log n); full leaderboards are O(n) with zero sorting.
 */
public class GameRoom {

    private final String sessionId;
    private final String quizId;
    private final String pin;
    private String status;
    private int currentQuestionIndex;
    private long currentQuestionEndEpochMs;
    private volatile String hostUuid;
    private final int maxPlayers;

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final LiveLeaderboard board = new LiveLeaderboard();

    public GameRoom(String sessionId, String quizId, String pin, String status, int maxPlayers) {
        this.sessionId = sessionId;
        this.quizId = quizId;
        this.pin = pin;
        this.status = status;
        this.maxPlayers = maxPlayers;
    }

    /** Legacy ctor kept for tests/tools: default cap 500. */
    public GameRoom(String sessionId, String quizId, String pin, String status) {
        this(sessionId, quizId, pin, status, 500);
    }

    /** Returns false when the room is at capacity. */
    public synchronized boolean addPlayer(Player p) {
        if (players.size() >= maxPlayers) return false;
        players.put(p.uuid(), p);
        board.join(p.uuid(), p.name());
        return true;
    }

    public void removePlayer(String uuid) {
        players.remove(uuid);
        board.remove(uuid);
    }

    public Player getPlayer(String uuid) {
        Player p = players.get(uuid);
        return p == null ? null : p.withScore((int) board.scoreOf(uuid));
    }

    /** Applies a score delta; returns the new exact rank (or -1 for unknown). */
    public int applyScore(String uuid, long delta) {
        return board.applyScore(uuid, delta);
    }

    public synchronized List<Player> players() {
        List<Player> out = new ArrayList<>(players.size());
        for (RankedSkipList.Entry e : board.snapshot()) {
            Player p = players.get(e.uuid());
            if (p != null) out.add(p.withScore((int) e.score()));
        }
        // Any session present in the map but missing from the board (race on join)
        // is appended so state never silently loses a connected player.
        for (Player p : players.values()) {
            if (out.stream().noneMatch(x -> x.uuid().equals(p.uuid()))) {
                out.add(p);
            }
        }
        return out;
    }

    public List<LeaderboardEntry> leaderboard() {
        List<RankedSkipList.Entry> snap = board.snapshot();
        List<LeaderboardEntry> out = new ArrayList<>(snap.size());
        int rank = 1;
        for (RankedSkipList.Entry e : snap) {
            out.add(new LeaderboardEntry(e.uuid(), e.name(), (int) e.score(), rank++));
        }
        return out;
    }

    public LiveLeaderboard board() {
        return board;
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public int capacity() {
        return maxPlayers;
    }

    // ---------- accessors ----------

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
