package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.dto.*;
import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.room.RoomRegistry;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.Json;
import com.sprintjudge.util.NameSanitizer;
import com.sprintjudge.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Live-game orchestrator: rooms, rounds, scoring, leaderboard transport.
 *
 * <p>Authoritative state lives in {@link GameRoom}; this class is the only
 * writer of round transitions and the only place that computes the visible
 * award (selection + coding share the streak/bonus math here).
 */
@Service
public class GameRoomManager implements LeaderboardBroadcaster {

    private static final int MAX_ATTEMPTS_PER_QUESTION = 50;
    // ponytail: streak bonus = roundScore * BONUS_RATE * min(streak-1, BONUS_CAP_STEPS).
    private static final double STREAK_BONUS_RATE = 0.1;
    private static final int STREAK_BONUS_CAP_STEPS = 5;
    private static final long LOBBY_TTL_MS = 30L * 60_000;

    private final RoomRegistry registry = new RoomRegistry();

    private final GameSessionRepository sessionRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final ScoringEngine scoringEngine;
    private final org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> submissionProcessor;
    private final WebSocketSessionManager ws;
    private final EvaluationService evaluationService;
    private final AdminSettingsService settingsService;
    private final BroadcastScheduler scheduler;
    private final SubmissionWriteBuffer writeBuffer;
    private final RoundTimeoutScheduler roundTimer;

    @Value("${sprintjudge.room.max-players:10000}")
    private int maxPlayers = 10000;

    private final java.util.concurrent.ScheduledExecutorService sweeper =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "oq-room-sweep");
                t.setDaemon(true);
                return t;
            });

    public GameRoomManager(GameSessionRepository sessionRepository,
                            QuizRepository quizRepository,
                            QuestionRepository questionRepository,
                            SubmissionRepository submissionRepository,
                            ScoringEngine scoringEngine,
                            org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> submissionProcessor,
                            WebSocketSessionManager ws,
                            EvaluationService evaluationService,
                            AdminSettingsService settingsService,
                            BroadcastScheduler scheduler,
                            SubmissionWriteBuffer writeBuffer,
                            RoundTimeoutScheduler roundTimer) {
        this.sessionRepository = sessionRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.scoringEngine = scoringEngine;
        this.submissionProcessor = submissionProcessor;
        this.ws = ws;
        this.evaluationService = evaluationService;
        this.settingsService = settingsService;
        this.scheduler = scheduler;
        this.writeBuffer = writeBuffer;
        this.roundTimer = roundTimer;
    }

    // ---------- lifecycle ----------

    public GameSession createRoom(String quizId, String hostUserId) {
        if (quizRepository.findById(quizId).isEmpty()) {
            throw new IllegalArgumentException("Quiz not found: " + quizId);
        }
        String pin;
        do {
            pin = Ids.pin();
        } while (registry.get(Integer.parseInt(pin)) != null
                || sessionRepository.findByPin(pin).isPresent());
        GameSession session = sessionRepository.create(quizId, hostUserId, pin, null);
        registry.put(Integer.parseInt(pin), new GameRoom(session.id(), quizId, pin, "LOBBY", maxPlayers));
        return session;
    }

    public Player join(String pin, String name, String sessionId, String role, String rejoinToken) {
        int key = Integer.parseInt(pin);
        GameRoom room = registry.get(key);
        if (room == null) {
            // Lookup is done OUTSIDE the registry write lock (H6 fix); the
            // factory only constructs — no DB access under lock.
            GameSession s = sessionRepository.findByPin(pin).orElse(null);
            if (s == null) throw new IllegalArgumentException("Invalid PIN");
            if ("ENDED".equals(s.status())) throw new IllegalStateException("This game has ended");
            room = registry.computeIfAbsent(key, p -> new GameRoom(s.id(), s.quizId(), pin, s.status(), maxPlayers));
        }
        String safeName = NameSanitizer.sanitize(name);
        if (safeName.isEmpty()) safeName = "Player";
        boolean isHost = "host".equalsIgnoreCase(role);
        synchronized (room) {
            if (isHost) {
                if (room.hostUuid() != null) throw new IllegalStateException("A host is already connected");
                Player p = new Player(Ids.uuid(), safeName, 0, sessionId, true, Ids.uuid());
                if (!room.addPlayer(p)) throw new IllegalStateException("Room is full");
                room.setHostUuid(p.uuid());
                room.touch();
            broadcastLeaderboard(pin);
                return p;
            }
            // Rejoin: reclaim a disconnected seat by token so scores survive.
            if (rejoinToken != null) {
                Player reclaimed = room.reclaim(rejoinToken, sessionId);
                if (reclaimed != null) {
                    room.touch();
            broadcastLeaderboard(pin);
                    return reclaimed;
                }
            }
            if (room.isFull()) throw new IllegalStateException("Room is full");
            Player p = new Player(Ids.uuid(), safeName, 0, sessionId, true, Ids.uuid());
            if (!room.addPlayer(p)) throw new IllegalStateException("Room is full");
            room.touch();
            broadcastLeaderboard(pin);
            return p;
        }
    }

    public void leave(String pin, String playerUuid) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        synchronized (room) {
            if (playerUuid.equals(room.hostUuid())) room.setHostUuid(null);
            room.hardRemove(playerUuid);
        }
        broadcastRoomState(pin);
        broadcastLeaderboard(pin);
    }

    // ---------- rounds ----------

    public void startQuestion(String pin) {
        GameRoom room = require(pin);
        List<Question> questions = questionRepository.findByQuiz(room.quizId());
        if (questions.isEmpty()) throw new IllegalStateException("Quiz has no questions");
        if (room.currentQuestionIndex() >= questions.size()) {
            endGame(pin);
            return;
        }
        Question q = questions.get(room.currentQuestionIndex());
        long end = Instant.now().toEpochMilli() + (long) q.timeLimitSec() * 1000;
        room.setCurrentQuestionEndEpochMs(end);
        room.setCurrentQuestionId(q.id());
        room.setStatus("ACTIVE");
        room.clearRounds();
        sessionRepository.updateStatus(room.sessionId(), "ACTIVE");
        QuestionDto dto = toDto(q);
        long now = Instant.now().toEpochMilli();
        QuestionStart start = new QuestionStart("QUESTION_START", dto, q.timeLimitSec(),
                room.currentQuestionEndEpochMs() - (long) q.timeLimitSec() * 1000, now);
        broadcastToRoom(pin, start);
        broadcastRoomState(pin);
        // Server-side auto-transition: lock the round when the clock hits zero.
        roundTimer.schedule(Integer.parseInt(pin), room.currentQuestionEndEpochMs(), () -> onTimerExpired(pin));
    }

    public void nextQuestion(String pin) {
        GameRoom room = require(pin);
        if (!"LOBBY".equals(room.status())) {
            room.setCurrentQuestionIndex(room.currentQuestionIndex() + 1);
            sessionRepository.setCurrentIndex(room.sessionId(), room.currentQuestionIndex());
        }
        if (room.currentQuestionIndex() >= questionRepository.findByQuiz(room.quizId()).size()) {
            endGame(pin);
        } else {
            startQuestion(pin);
        }
    }

    // ---------- submissions ----------

    public void submit(String pin, String questionId, String playerUuid,
                        String language, JsonNode response) {
        GameRoom room = require(pin);
        Question q = questionRepository.findById(questionId).orElse(null);
        if (q == null) return;
        QuestionType type = QuestionType.from(q.questionType());

        // Gate: score only during the live round for the current question.
        if (!"ACTIVE".equals(room.status()) || !questionId.equals(room.currentQuestionId())) {
            ws.send(room.getPlayer(playerUuid) == null ? null : room.getPlayer(playerUuid).sessionId(),
                    new ErrorMessage("ERROR", "Round is locked — answers are no longer accepted"));
            return;
        }
        if (!room.tryBeginAttempt(questionId, playerUuid, MAX_ATTEMPTS_PER_QUESTION)) {
            ws.send(sessionIdOf(room, playerUuid), new ErrorMessage("ERROR",
                    "Attempt limit reached for this question"));
            return;
        }

        if (type.isCoding()) {
            submitCoding(room, q, playerUuid, language, response);
            return;
        }

        double fraction = evaluationService.evaluateCorrectness(q, response);
        Player p = room.getPlayer(playerUuid);
        if (p == null) return;
        long limitMs = (long) q.timeLimitSec() * 1000;
        long remainingMs = Math.max(0, room.currentQuestionEndEpochMs() - Instant.now().toEpochMilli());
        long taken = Math.max(0, (limitMs - remainingMs) / 1000);
        int attempts = room.attemptCount(questionId, playerUuid);
        boolean correct = fraction >= 1.0;
        int base = scoringEngine.scoreSelection(fraction, taken, q.timeLimitSec(), attempts,
                q.pointsBase(), settingsService.asMap());
        int bonus = streakBonus(room, playerUuid, correct, base);
        int total = base + bonus;

        writeBuffer.offer(new Submission(Ids.uuid(), room.sessionId(), questionId,
                p.name(), playerUuid, Json.write(response), total, correct, null, attempts, Instant.now()));
        room.applyScore(playerUuid, total);
        room.recordRound(playerUuid, base, bonus);
        broadcastLeaderboard(pin);
    }

    private void submitCoding(GameRoom room, Question q, String playerUuid, String language, JsonNode response) {
        Player p = room.getPlayer(playerUuid);
        if (p == null) return;
        List<String> allowed = q.languagesAllowed();
        if (allowed != null && !allowed.isEmpty()
                && allowed.stream().noneMatch(a -> a.equalsIgnoreCase(language))) {
            ws.send(p.sessionId(), new ErrorMessage("ERROR", "Language not allowed for this question"));
            return;
        }
        String source = response == null ? "" : response.path("source").asText("");
        long limitMs = (long) q.timeLimitSec() * 1000;
        long deadline = room.currentQuestionEndEpochMs();
        long taken = Math.max(0, Math.min((deadline - Instant.now().toEpochMilli()) / 1000, q.timeLimitSec()));
        int attempts = room.attemptCount(q.id(), playerUuid);
        String sessionId = p.sessionId();
        String pin = room.pin();
        String questionId = q.id();

        CodingOutcomeConsumer handler = (uuid, baseScore, allPassed, passed, totalTests) -> {
            int bonus;
            synchronized (room) {
                boolean correct = allPassed;
                bonus = streakBonus(room, uuid, correct, baseScore);
                int total = baseScore + bonus;
                room.applyScore(uuid, total);
                room.recordRound(uuid, baseScore, bonus);
            }
            ws.send(sessionId, new SubmissionResult("SUBMISSION_RESULT", questionId, baseScore + bonus, allPassed, passed, totalTests));
            broadcastLeaderboard(pin);
        };

        boolean accepted = submissionProcessor.getObject().processCoding(room.sessionId(), pin,
                questionId, p.name(), playerUuid, language, source, attempts, settingsService.asMap(), handler);
        if (!accepted) {
            ws.send(p.sessionId(), new ErrorMessage("ERROR",
                    "Judge queue is busy — resubmit shortly (auto-retry in 250ms)"));
        }
    }

    /** Updates streak and returns the bonus to add; 0 until the second correct in a row. */
    private int streakBonus(GameRoom room, String uuid, boolean correct, int base) {
        int streak = correct ? room.bumpStreak(uuid) : 0;
        if (!correct) {
            room.resetStreak(uuid);
            return 0;
        }
        if (streak < 2) return 0;
        double mult = Math.min(streak - 1, STREAK_BONUS_CAP_STEPS);
        return (int) Math.round(base * STREAK_BONUS_RATE * mult);
    }

    // ---------- host controls ----------

    public void forceSubmit(String pin) {
        roundTimer.cancel(Integer.parseInt(pin));
        transitionToReview(pin);
    }

    public void extendTimer(String pin, int seconds) {
        GameRoom room = require(pin);
        if (!"ACTIVE".equals(room.status())) return;
        long newEnd = room.currentQuestionEndEpochMs() + (long) seconds * 1000;
        room.setCurrentQuestionEndEpochMs(newEnd);
        roundTimer.schedule(Integer.parseInt(pin), newEnd, () -> onTimerExpired(pin));
        broadcastToRoom(pin, new TimerUpdate("TIMER_UPDATE", newEnd, seconds));
    }

    public void kickPlayer(String pin, String playerUuid) {
        GameRoom room = require(pin);
        Player p = room.getPlayer(playerUuid);
        if (p != null) {
            ws.send(p.sessionId(), new ErrorMessage("ERROR", "You were removed by the host"));
            room.hardRemove(playerUuid);
        }
        if (playerUuid.equals(room.hostUuid())) room.setHostUuid(null);
        broadcastRoomState(pin);
        broadcastLeaderboard(pin);
    }

    private void onTimerExpired(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        synchronized (room) {
            if (!"ACTIVE".equals(room.status())) return;          // host already advanced
            if (Instant.now().toEpochMilli() < room.currentQuestionEndEpochMs()) return;
            transitionToReview(pin);
        }
    }

    private void transitionToReview(String pin) {
        GameRoom room = require(pin);
        roundTimer.cancel(Integer.parseInt(pin));
        room.setStatus("REVIEW");
        sessionRepository.updateStatus(room.sessionId(), "REVIEW");
        writeBuffer.flush();                       // accuracy boundary
        flushLeaderboardDelta(pin);
        sendRoundResult(pin, true);
        broadcastRoomState(pin);
    }

    public void endGame(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        roundTimer.cancel(Integer.parseInt(pin));
        room.setStatus("ENDED");
        sessionRepository.updateStatus(room.sessionId(), "ENDED");
        writeBuffer.flush();
        flushLeaderboardDelta(pin);
        GameEnd end = new GameEnd("GAME_END", room.leaderboard());
        broadcastToRoom(pin, end);
        registry.remove(Integer.parseInt(pin));
    }

    /** Sweeps unrecoverable rooms: empty lobbies idle past the TTL. */
    public void sweepIdleRooms() {
        long now = System.currentTimeMillis();
        for (GameRoom room : registry.snapshot()) {
            if ("LOBBY".equals(room.status()) && room.connectedCount() == 0 && room.idleMs(now) > LOBBY_TTL_MS) {
                registry.remove(Integer.parseInt(room.pin()));
            }
        }
    }

    public int activeRooms() {
        return registry.size();
    }

    @jakarta.annotation.PostConstruct
    void startSweep() {
        sweeper.scheduleWithFixedDelay(this::sweepIdleRooms, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    @jakarta.annotation.PreDestroy
    void stopSweep() {
        sweeper.shutdownNow();
    }

    // ---------- leaderboard transport ----------

    public void broadcastLeaderboard(String pin) {
        int key = Integer.parseInt(pin);
        scheduler.markDirty(key, () -> flushLeaderboardDelta(pin));
    }

    public void flushLeaderboardDelta(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        String json = deltaJson(room.board().drainDeltas(false));
        if (json != null) ws.broadcastRaw(playerSessionIds(pin), json);
    }

    public void sendFullLeaderboard(String pin, String sessionId) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        String json = deltaJson(room.board().fullBatch());
        if (json != null) ws.sendRaw(sessionId, json);
    }

    private String deltaJson(com.sprintjudge.service.leaderboard.DeltaLedger.Batch b) {
        if (b == null || (b.resync() && b.upserts().isEmpty())) return null;
        List<LeaderboardEntry> entries = b.upserts().stream()
                .map(d -> new LeaderboardEntry(d.uuid(), d.name(), (int) d.score(), d.rank()))
                .toList();
        return Json.write(new LeaderboardDelta("LEADERBOARD_DELTA", b.seq(), b.resync(), entries));
    }

    // ---------- state ----------

    public RoomState getRoomState(String pin) {
        GameRoom room = require(pin);
        List<RoomState.PlayerInfo> players = room.players().stream()
                .map(p -> new RoomState.PlayerInfo(p.uuid(), p.name(), p.score(), p.connected()))
                .toList();
        int count = questionRepository.findByQuiz(room.quizId()).size();
        return new RoomState("ROOM_STATE", room.status(), count, room.currentQuestionId(), players);
    }

    private void sendRoundResult(String pin, boolean revealed) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        int idx = room.currentQuestionIndex();
        List<Question> qs = questionRepository.findByQuiz(room.quizId());
        if (idx >= qs.size()) return;
        Question q = qs.get(idx);
        QuestionType type = QuestionType.from(q.questionType());
        JsonNode answer = revealed ? QuestionAnswers.answerPayload(type, Json.readTree(q.config())) : null;
        List<RoundResult.PlayerScore> scores = room.players().stream()
                .map(pl -> {
                    int[] round = room.roundOf(pl.uuid());
                    boolean correct = round[0] > 0;
                    return new RoundResult.PlayerScore(pl.uuid(), pl.name(), (int) room.board().scoreOf(pl.uuid()),
                            correct, round[0], room.streakOf(pl.uuid()), round[1]);
                })
                .toList();
        broadcastToRoom(pin, new RoundResult("ROUND_RESULT", q.id(), revealed, answer, scores));
    }

    private String sessionIdOf(GameRoom room, String uuid) {
        Player p = room.getPlayer(uuid);
        return p == null ? null : p.sessionId();
    }

    private void broadcastRoomState(String pin) {
        ws.broadcast(playerSessionIds(pin), getRoomState(pin));
    }

    private void broadcastToRoom(String pin, Object message) {
        ws.broadcast(playerSessionIds(pin), message);
    }

    private List<String> playerSessionIds(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return List.of();
        return room.players().stream().map(p -> p.sessionId()).filter(s -> s != null).toList();
    }

    private GameRoom require(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) throw new IllegalArgumentException("No active room for PIN " + pin);
        return room;
    }

    private QuestionDto toDto(Question q) {
        Object config = q.config() == null ? Map.of()
                : QuestionAnswers.sanitize(QuestionType.from(q.questionType()), Json.readTree(q.config()));
        return new QuestionDto(q.id(), q.questionType(), q.title(), q.description(),
                q.timeLimitSec(), q.pointsBase(), q.languagesAllowed(), config);
    }
}
