package com.sprintjudge.service;

import com.sprintjudge.domain.dto.LeaderboardEntry;
import com.sprintjudge.service.leaderboard.LiveLeaderboard;
import com.sprintjudge.service.leaderboard.RankedSkipList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live, in-memory state for a single game room.
 *
 * <p>Identity/sessions live in a lock-free map; ranking truth lives in the
 * exact {@link LiveLeaderboard} (order-statistic skip list + delta ledger).
 * Score mutations are O(log n); full leaderboards are O(n) with zero sorting.
 *
 * <p>Disconnected players stay in the board (ranked, marked offline) so a
 * rejoin or refresh reclaims their seat and final standings keep them a
 * kicked player is hard-removed instead.
 */
public class GameRoom {

    public enum GameMode { STANDARD, AUTO_PILOT, PRACTICE, EXAM, TEAM, BATTLE }

    private final String sessionId;
    private final String quizId;
    private final String pin;
    private volatile String status;
    private volatile int currentQuestionIndex;
    private volatile String currentQuestionId;
    private volatile long currentQuestionEndEpochMs;
    private volatile long currentQuestionStartEpochMs;
    // Original deadline at round start; extendTimer caps total growth at +300s over this.
    private volatile long questionEndBaseEpochMs;
    private volatile String hostUuid;
    private final int maxPlayers;
    private final GameMode gameMode;

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final LiveLeaderboard board = new LiveLeaderboard();

    // In-memory per-(question,player) attempt counter (authoritative; the async
    // write buffer used to lag behind so rapid resubmits scored full marks).
    private final ConcurrentHashMap<String, Integer> attempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> streaks = new ConcurrentHashMap<>();
    // Per-round contribution for the reveal screen: [roundScore, bonus].
    private final ConcurrentHashMap<String, int[]> lastRound = new ConcurrentHashMap<>();

    // Exam mode: total end time across all questions.
    private volatile long totalEndEpochMs;

    // Team mode: team management.
    public record Team(String id, String name, java.util.Set<String> memberUuids, long score) {
        public Team withScore(long s) { return new Team(id, name, memberUuids, s); }
        public Team addMember(String uuid) {
            var m = new java.util.HashSet<>(memberUuids);
            m.add(uuid);
            return new Team(id, name, m, score);
        }
    }
    private final ConcurrentHashMap<String, Team> teams = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger teamSeq = new java.util.concurrent.atomic.AtomicInteger(0);

    // Battle mode: matchmaking and bracket.
    public record BattleMatch(String id, String p1Uuid, String p2Uuid, String questionId,
                               String p1Answer, String p2Answer, boolean p1Correct, boolean p2Correct,
                               String winnerUuid) {}
    private final java.util.concurrent.CopyOnWriteArrayList<BattleMatch> battleMatches = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<String[]> bracket = new CopyOnWriteArrayList<>();

    private volatile long lastActivityMs = System.currentTimeMillis();

    public GameRoom(String sessionId, String quizId, String pin, String status, int maxPlayers, GameMode gameMode) {
        this.sessionId = sessionId;
        this.quizId = quizId;
        this.pin = pin;
        this.status = status;
        this.maxPlayers = maxPlayers;
        this.gameMode = gameMode;
    }

    /** Legacy ctor kept for tests/tools: default cap 500, standard mode. */
    public GameRoom(String sessionId, String quizId, String pin, String status) {
        this(sessionId, quizId, pin, status, 500, GameMode.STANDARD);
    }

    /** 5-arg ctor for computeIfAbsent: defaults to STANDARD mode. */
    public GameRoom(String sessionId, String quizId, String pin, String status, int maxPlayers) {
        this(sessionId, quizId, pin, status, maxPlayers, GameMode.STANDARD);
    }

    /** Returns false when the room is at capacity. */
    public synchronized boolean addPlayer(Player p) {
        if (players.size() >= maxPlayers) return false;
        players.put(p.uuid(), p);
        board.join(p.uuid(), p.name());
        return true;
    }

    /** Hosts occupy a roster seat but never enter the scored board. */
    public synchronized boolean addHost(Player p) {
        if (players.size() >= maxPlayers) return false;
        players.put(p.uuid(), p);
        return true;
    }

    /** Disconnect: keep the board row so standings/rankings survive the gap. */
    public synchronized void softRemove(String uuid) {
        Player p = players.get(uuid);
        if (p != null) players.put(uuid, p.disconnected());
    }

    /** Hard remove (kick or sweep): drop from board too. */
    public synchronized void hardRemove(String uuid) {
        players.remove(uuid);
        board.remove(uuid);
    }

    public Player reclaim(String token, String sessionId) {
        if (token == null) return null;
        for (Player p : players.values()) {
            if (!p.connected() && token.equals(p.token())) {
                Player reclaimed = p.withSession(sessionId);
                players.put(reclaimed.uuid(), reclaimed);
                return reclaimed;
            }
        }
        return null;
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

    public int connectedCount() {
        int n = 0;
        for (Player p : players.values()) if (p.connected()) n++;
        return n;
    }

    // ---------- attempts / streaks / round stats ----------

    public boolean tryBeginAttempt(String questionId, String uuid, int max) {
        String key = questionId + " " + uuid;
        boolean[] accepted = {false};
        attempts.compute(key, (k, v) -> {
            int cur = v == null ? 0 : v;
            if (cur >= max) return v;
            accepted[0] = true;
            return cur + 1;
        });
        return accepted[0];
    }

    public int attemptCount(String questionId, String uuid) {
        return attempts.getOrDefault(questionId + " " + uuid, 0);
    }

    /** Returns one attempt (busy-rejected judge submissions must not consume the cap). */
    public void refundAttempt(String questionId, String uuid) {
        attempts.computeIfPresent(questionId + " " + uuid, (k, v) -> v > 0 ? v - 1 : v);
    }

    public int bumpStreak(String uuid) {
        return streaks.merge(uuid, 1, (a, b) -> a + b);
    }

    public void resetStreak(String uuid) {
        streaks.put(uuid, 0);
    }

    public int streakOf(String uuid) {
        return streaks.getOrDefault(uuid, 0);
    }

    public void recordRound(String uuid, int roundScore, int bonus) {
        lastRound.put(uuid, new int[]{roundScore, bonus});
    }

    public void clearRounds() {
        lastRound.clear();
    }

    public int[] roundOf(String uuid) {
        return lastRound.getOrDefault(uuid, new int[]{0, 0});
    }

    // ---------- lifecycle bookkeeping ----------

    public void touch() {
        lastActivityMs = System.currentTimeMillis();
    }

    public long idleMs(long now) {
        return now - lastActivityMs;
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
    public String currentQuestionId() { return currentQuestionId; }
    public void setCurrentQuestionId(String id) { this.currentQuestionId = id; }
    public long currentQuestionEndEpochMs() { return currentQuestionEndEpochMs; }
    public void setCurrentQuestionEndEpochMs(long ms) { this.currentQuestionEndEpochMs = ms; }
    public long questionEndBaseEpochMs() { return questionEndBaseEpochMs; }
    public void setQuestionEndBaseEpochMs(long ms) { this.questionEndBaseEpochMs = ms; }
    public long currentQuestionStartEpochMs() { return currentQuestionStartEpochMs; }
    public void setCurrentQuestionStartEpochMs(long ms) { this.currentQuestionStartEpochMs = ms; }
    public GameMode gameMode() { return gameMode; }
    public long totalEndEpochMs() { return totalEndEpochMs; }
    public void setTotalEndEpochMs(long ms) { this.totalEndEpochMs = ms; }

    // ---------- team mode ----------

    public Team createTeam(String name) {
        String id = "team-" + teamSeq.incrementAndGet();
        Team t = new Team(id, name, java.util.Set.of(), 0);
        teams.put(id, t);
        return t;
    }

    public Team joinTeam(String teamId, String playerUuid) {
        return teams.compute(teamId, (id, t) -> {
            if (t == null) return null;
            return t.addMember(playerUuid);
        });
    }

    public Team getTeam(String teamId) { return teams.get(teamId); }
    public java.util.Collection<Team> allTeams() { return teams.values(); }

    public String teamIdOf(String playerUuid) {
        for (Team t : teams.values()) {
            if (t.memberUuids().contains(playerUuid)) return t.id();
        }
        return null;
    }

    public long applyTeamScore(String teamId, long delta) {
        Team result = teams.compute(teamId, (id, t) -> {
            if (t == null) return null;
            return t.withScore(t.score() + delta);
        });
        return result == null ? 0 : result.score();
    }

    // ---------- battle mode ----------

    public void addBattleMatch(BattleMatch m) { battleMatches.add(m); }
    public java.util.List<BattleMatch> battleMatches() { return battleMatches; }
    public synchronized void setBracket(java.util.List<String[]> rounds) {
        bracket.clear();
        bracket.addAll(rounds);
    }
    public java.util.List<String[]> bracket() { return bracket; }
}
