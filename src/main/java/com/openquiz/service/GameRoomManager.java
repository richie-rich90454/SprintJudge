package com.openquiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.openquiz.domain.dto.*;
import com.openquiz.domain.enums.QuestionType;
import com.openquiz.domain.models.GameSession;
import com.openquiz.domain.models.Question;
import com.openquiz.domain.models.Submission;
import com.openquiz.repository.GameSessionRepository;
import com.openquiz.repository.QuizRepository;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.SubmissionRepository;
import com.openquiz.util.Ids;
import com.openquiz.util.Json;
import com.openquiz.websocket.WebSocketSessionManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameRoomManager {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    private final GameSessionRepository sessionRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final ScoringEngine scoringEngine;
    private final SubmissionProcessor submissionProcessor;
    private final WebSocketSessionManager ws;
    private final EvaluationService evaluationService;
    private final AdminSettingsService settingsService;

    public GameRoomManager(GameSessionRepository sessionRepository,
                           QuizRepository quizRepository,
                           QuestionRepository questionRepository,
                           SubmissionRepository submissionRepository,
                           ScoringEngine scoringEngine,
                           SubmissionProcessor submissionProcessor,
                           WebSocketSessionManager ws,
                           EvaluationService evaluationService,
                           AdminSettingsService settingsService) {
        this.sessionRepository = sessionRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.scoringEngine = scoringEngine;
        this.submissionProcessor = submissionProcessor;
        this.ws = ws;
        this.evaluationService = evaluationService;
        this.settingsService = settingsService;
    }

    public GameSession createRoom(String quizId, String hostUserId) {
        if (quizRepository.findById(quizId).isEmpty()) {
            throw new IllegalArgumentException("Quiz not found: " + quizId);
        }
        String pin = Ids.pin();
        while (rooms.containsKey(pin) || sessionRepository.findByPin(pin).isPresent()) {
            pin = Ids.pin();
        }
        GameSession session = sessionRepository.create(quizId, hostUserId, pin, null);
        rooms.put(pin, new GameRoom(session.id(), quizId, pin, "LOBBY"));
        return session;
    }

    public Player join(String pin, String name, String sessionId) {
        GameRoom room = rooms.get(pin);
        if (room == null) {
            Optional<GameSession> s = sessionRepository.findByPin(pin);
            if (s.isEmpty()) throw new IllegalArgumentException("Invalid PIN");
            room = new GameRoom(s.get().id(), s.get().quizId(), pin, s.get().status());
            rooms.put(pin, room);
        }
        Player p = new Player(Ids.uuid(), name, 0, sessionId, true);
        room.addPlayer(p);
        broadcastRoomState(pin);
        return p;
    }

    public void linkSession(String pin, String playerUuid, String sessionId) {
        GameRoom room = rooms.get(pin);
        if (room == null) return;
        Player p = room.getPlayer(playerUuid);
        if (p != null) room.addPlayer(p.withSession(sessionId));
    }

    public void leave(String pin, String playerUuid) {
        GameRoom room = rooms.get(pin);
        if (room == null) return;
        room.removePlayer(playerUuid);
        broadcastRoomState(pin);
    }

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
        room.setCurrentQuestionIndex(room.currentQuestionIndex() + 1);
        sessionRepository.setCurrentIndex(room.sessionId(), room.currentQuestionIndex());
        if (room.currentQuestionIndex() >= questionRepository.findByQuiz(room.quizId()).size()) {
            endGame(pin);
        } else {
            startQuestion(pin);
        }
    }

    public void submit(String pin, String questionId, String playerUuid,
                       String language, JsonNode response) {
        GameRoom room = require(pin);
        Question q = questionRepository.findById(questionId).orElse(null);
        if (q == null) return;
        QuestionType type = QuestionType.from(q.questionType());

        if (type.isCoding()) {
            String source = response == null ? "" : response.path("source").asText("");
            Player p = room.getPlayer(playerUuid);
            if (p == null) return;
            submissionProcessor.processCoding(room.sessionId(), questionId, p.name(),
                    playerUuid, language, source, settingsService.asMap());
            return;
        }

        double fraction = evaluationService.evaluateCorrectness(q, response);
        Player p = room.getPlayer(playerUuid);
        if (p == null) return;
        int attempts = countAttempts(room.sessionId(), questionId, playerUuid) + 1;
        long limitMs = (long) q.timeLimitSec() * 1000;
        long remainingMs = Math.max(0, room.currentQuestionEndEpochMs() - Instant.now().toEpochMilli());
        long taken = Math.max(0, (limitMs - remainingMs) / 1000);
        int raw = scoringEngine.scoreSelection(fraction >= 1.0, taken, q.timeLimitSec(), attempts, settingsService.asMap());
        int score = (int) Math.round(raw * fraction);
        boolean correct = fraction >= 1.0;

        submissionRepository.save(new Submission(Ids.uuid(), room.sessionId(), questionId,
                p.name(), playerUuid,
                Json.write(response), score, correct, null, attempts, Instant.now()));
        room.applyScore(playerUuid, score);
        broadcastLeaderboard(pin);
    }

    public void forceSubmit(String pin) {
        GameRoom room = require(pin);
        room.setStatus("REVIEW");
        sessionRepository.updateStatus(room.sessionId(), "REVIEW");
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
    }

    public void endGame(String pin) {
        GameRoom room = require(pin);
        room.setStatus("ENDED");
        sessionRepository.updateStatus(room.sessionId(), "ENDED");
        GameEnd end = new GameEnd("GAME_END", room.leaderboard());
        broadcastToRoom(pin, end);
        rooms.remove(pin);
    }

    public void broadcastLeaderboard(String pin) {
        GameRoom room = rooms.get(pin);
        if (room == null) return;
        broadcastToRoom(pin, new LeaderboardMessage("LEADERBOARD", room.leaderboard()));
    }

    public RoomState getRoomState(String pin) {
        GameRoom room = require(pin);
        List<RoomState.PlayerInfo> players = room.players().stream()
                .map(p -> new RoomState.PlayerInfo(p.uuid(), p.name(), p.score()))
                .toList();
        int count = questionRepository.findByQuiz(room.quizId()).size();
        return new RoomState("ROOM_STATE", room.status(), count, players);
    }

    private void sendRoundResult(String pin, boolean revealed) {
        GameRoom room = rooms.get(pin);
        if (room == null) return;
        int idx = room.currentQuestionIndex();
        List<Question> qs = questionRepository.findByQuiz(room.quizId());
        if (idx >= qs.size()) return;
        Question q = qs.get(idx);
        JsonNode answer = revealed ? Json.readTree(q.config()) : null;
        List<RoundResult.PlayerScore> scores = room.players().stream()
                .map(p -> new RoundResult.PlayerScore(p.uuid(), p.name(), p.score(), true))
                .toList();
        broadcastToRoom(pin, new RoundResult("ROUND_RESULT", q.id(), revealed, answer, scores));
    }

    private int countAttempts(String sessionId, String questionId, String playerUuid) {
        return submissionRepository.findBySessionQuestion(sessionId, questionId).stream()
                .filter(s -> s.playerUuid().equals(playerUuid))
                .mapToInt(Submission::attemptCount).sum();
    }

    private void broadcastRoomState(String pin) {
        ws.broadcast(playerSessionIds(pin), getRoomState(pin));
    }

    private void broadcastToRoom(String pin, Object message) {
        ws.broadcast(playerSessionIds(pin), message);
    }

    private List<String> playerSessionIds(String pin) {
        GameRoom room = rooms.get(pin);
        if (room == null) return List.of();
        return room.players().stream().map(Player::sessionId).filter(s -> s != null).toList();
    }

    private GameRoom require(String pin) {
        GameRoom room = rooms.get(pin);
        if (room == null) throw new IllegalArgumentException("No active room for PIN " + pin);
        return room;
    }

    private QuestionDto toDto(Question q) {
        List<String> langs = q.languagesAllowed();
        Object config = q.config() == null ? Map.of() : Json.readTree(q.config());
        return new QuestionDto(q.id(), q.questionType(), q.title(), q.description(),
                q.timeLimitSec(), q.pointsBase(), langs, config);
    }
}
