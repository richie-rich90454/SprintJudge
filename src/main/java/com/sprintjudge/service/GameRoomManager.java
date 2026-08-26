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
import com.sprintjudge.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class GameRoomManager implements LeaderboardBroadcaster {

    private final RoomRegistry registry = new RoomRegistry();

    private final GameSessionRepository sessionRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final ScoringEngine scoringEngine;
    // Lazy lookup breaks the Manager→Processor→Broadcaster(Manager) construction
    // cycle while keeping the runtime collaboration identical.
    private final org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> submissionProcessor;
    private final WebSocketSessionManager ws;
    private final EvaluationService evaluationService;
    private final AdminSettingsService settingsService;
    private final BroadcastScheduler scheduler;
    private final SubmissionWriteBuffer writeBuffer;

    @Value("${sprintjudge.room.max-players:10000}")
    private int maxPlayers = 10000;   // literal default keeps plain `new` usable in tests

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
                           SubmissionWriteBuffer writeBuffer) {
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
    }

    // ---------- lifecycle ----------

    public GameSession createRoom(String quizId, String hostUserId) {
        if (quizRepository.findById(quizId).isEmpty()) {
            throw new IllegalArgumentException("Quiz not found: " + quizId);
        }
        String pin = Ids.pin();
        while (registry.get(Integer.parseInt(pin)) != null
                || sessionRepository.findByPin(pin).isPresent()) {
            pin = Ids.pin();
        }
        GameSession session = sessionRepository.create(quizId, hostUserId, pin, null);
        registry.put(Integer.parseInt(pin), new GameRoom(session.id(), quizId, pin, "LOBBY", maxPlayers));
        return session;
    }

    public Player join(String pin, String name, String sessionId, String role) {
        int key = Integer.parseInt(pin);
        GameRoom room = registry.computeIfAbsent(key, p -> {
            GameSession s = sessionRepository.findByPin(pin)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid PIN"));
            return new GameRoom(s.id(), s.quizId(), pin, s.status(), maxPlayers);
        });
        // Edge case Z: sanitise the player name before it ever enters state/broadcasts.
        String safeName = com.sprintjudge.util.NameSanitizer.sanitize(name);
        if (safeName.isEmpty()) safeName = "Player";
        boolean isHost = "host".equalsIgnoreCase(role);
        Player p;
        synchronized (room) {
            if (isHost) {
                if (room.hostUuid() != null) throw new IllegalStateException("A host is already connected");
            } else if (room.isFull()) {
                throw new IllegalStateException("Room is full");
            }
            p = new Player(Ids.uuid(), safeName, 0, sessionId, true);
            if (!room.addPlayer(p)) throw new IllegalStateException("Room is full");
            if (isHost) room.setHostUuid(p.uuid());
        }
        broadcastRoomState(pin);
        // The join delta (score 0 upsert) must reach clients even if nobody scores.
        broadcastLeaderboard(pin);
        return p;
    }

    public void leave(String pin, String playerUuid) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        room.removePlayer(playerUuid);
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
        room.setStatus("ACTIVE");
        sessionRepository.updateStatus(room.sessionId(), "ACTIVE");
        QuestionDto dto = toDto(q);
        QuestionStart start = new QuestionStart("QUESTION_START", dto, q.timeLimitSec(), Instant.now().toEpochMilli());
        broadcastToRoom(pin, start);
        broadcastRoomState(pin);
    }

    public void nextQuestion(String pin) {
        GameRoom room = require(pin);
        // First start (LOBBY) begins at index 0; only subsequent rounds advance.
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

        if (type.isCoding()) {
            Player p = room.getPlayer(playerUuid);
            if (p == null) return;
            // Per-question language restriction mirrors the client's locked select.
            List<String> allowed = q.languagesAllowed();
            if (allowed != null && !allowed.isEmpty()
                    && allowed.stream().noneMatch(a -> a.equalsIgnoreCase(language))) {
                ws.send(p.sessionId(), new ErrorMessage("ERROR",
                        "Language not allowed for this question"));
                return;
            }
            String source = response == null ? "" : response.path("source").asText("");
            boolean accepted = submissionProcessor.getObject().processCoding(room.sessionId(), pin,
                    questionId, p.name(), playerUuid, language, source, settingsService.asMap());
            if (!accepted) {
                // Edge case Y companion: saturated judge queue answers with a friendly retry.
                ws.send(p.sessionId(), new ErrorMessage("ERROR",
                        "Judge queue is busy — resubmit shortly (auto-retry in 250ms)"));
            }
            return;
        }

        double fraction = evaluationService.evaluateCorrectness(q, response);
        Player p = room.getPlayer(playerUuid);
        if (p == null) return;
        int attempts = countAttempts(room.sessionId(), questionId, playerUuid) + 1;
        long limitMs = (long) q.timeLimitSec() * 1000;
        long remainingMs = Math.max(0, room.currentQuestionEndEpochMs() - Instant.now().toEpochMilli());
        long taken = Math.max(0, (limitMs - remainingMs) / 1000);
        // Fraction-aware engine: partial credit scales the decayed base directly.
        int raw = scoringEngine.scoreSelection(fraction, taken, q.timeLimitSec(), attempts, settingsService.asMap());
        int score = (int) Math.round(raw * fraction);
        boolean correct = fraction >= 1.0;

        writeBuffer.offer(new Submission(Ids.uuid(), room.sessionId(), questionId,
                p.name(), playerUuid, Json.write(response), score, correct, null, attempts, Instant.now()));
        room.applyScore(playerUuid, score);
        broadcastLeaderboard(pin);
    }

    // ---------- host controls ----------

    public void forceSubmit(String pin) {
        GameRoom room = require(pin);
        room.setStatus("REVIEW");
        sessionRepository.updateStatus(room.sessionId(), "REVIEW");
        // Accuracy boundary: persist buffered scores before revealing results.
        writeBuffer.flush();
        flushLeaderboardDelta(pin);
        sendRoundResult(pin, true);
    }

    public void extendTimer(String pin, int seconds) {
        GameRoom room = require(pin);
        long newEnd = room.currentQuestionEndEpochMs() + (long) seconds * 1000;
        room.setCurrentQuestionEndEpochMs(newEnd);
        broadcastToRoom(pin, new TimerUpdate("TIMER_UPDATE", newEnd, seconds));
    }

    public void kickPlayer(String pin, String playerUuid) {
        GameRoom room = require(pin);
        Player p = room.getPlayer(playerUuid);
        if (p != null) {
            ws.send(p.sessionId(), new ErrorMessage("ERROR", "You were removed by the host"));
            room.removePlayer(playerUuid);
        }
        broadcastRoomState(pin);
        broadcastLeaderboard(pin);
    }

    public void endGame(String pin) {
        GameRoom room = require(pin);
        room.setStatus("ENDED");
        sessionRepository.updateStatus(room.sessionId(), "ENDED");
        writeBuffer.flush();                       // accuracy boundary
        flushLeaderboardDelta(pin);                // deliver final deltas first
        GameEnd end = new GameEnd("GAME_END", room.leaderboard());
        broadcastToRoom(pin, end);
        registry.remove(Integer.parseInt(pin));
    }

    /** Live-room count for metrics. */
    public int activeRooms() {
        return registry.size();
    }

    // ---------- leaderboard transport ----------

    /** Hot path: mark dirty; the shared 16 ms tick fans out one delta per room. */
    public void broadcastLeaderboard(String pin) {
        int key = Integer.parseInt(pin);
        scheduler.markDirty(key, () -> flushLeaderboardDelta(pin));
    }

    /** Builds and sends one delta batch for the room (no-op when nothing pending). */
    public void flushLeaderboardDelta(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        String json = deltaJson(room.board().drainDeltas(false));
        if (json != null) ws.broadcastRaw(playerSessionIds(pin), json);
    }

    /** Authoritative full snapshot to one recipient (joiner or resync). */
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
                .map(p -> new RoomState.PlayerInfo(p.uuid(), p.name(), p.score()))
                .toList();
        int count = questionRepository.findByQuiz(room.quizId()).size();
        return new RoomState("ROOM_STATE", room.status(), count, players);
    }

    // ---------- internals ----------

    private void sendRoundResult(String pin, boolean revealed) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        int idx = room.currentQuestionIndex();
        List<Question> qs = questionRepository.findByQuiz(room.quizId());
        if (idx >= qs.size()) return;
        Question q = qs.get(idx);
        JsonNode answer = revealed ? Json.readTree(q.config()) : null;
        List<RoundResult.PlayerScore> scores = room.players().stream()
                .map(pl -> new RoundResult.PlayerScore(pl.uuid(), pl.name(), pl.score(), true))
                .toList();
        broadcastToRoom(pin, new RoundResult("ROUND_RESULT", q.id(), revealed, answer, scores));
    }

    private int countAttempts(String sessionId, String questionId, String playerUuid) {
        return submissionRepository.findBySessionQuestion(sessionId, questionId).stream()
                .filter(s -> s.playerUuid().equals(playerUuid))
                .mapToInt(s -> s.attemptCount()).sum();
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
        Object config = q.config() == null ? Map.of() : Json.readTree(q.config());
        return new QuestionDto(q.id(), q.questionType(), q.title(), q.description(),
                q.timeLimitSec(), q.pointsBase(), q.languagesAllowed(), config);
    }
}
