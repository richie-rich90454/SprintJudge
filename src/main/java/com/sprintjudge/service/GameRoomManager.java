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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(GameRoomManager.class);
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
    private final ApplicationEventPublisher eventPublisher;

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
                            RoundTimeoutScheduler roundTimer,
                            ApplicationEventPublisher eventPublisher) {
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
        this.eventPublisher = eventPublisher;
    }

    // ---------- lifecycle ----------

    public GameSession createRoom(String quizId, String hostUserId) {
        return createRoom(quizId, hostUserId, GameRoom.GameMode.STANDARD);
    }

    public GameSession createRoom(String quizId, String hostUserId, GameRoom.GameMode gameMode) {
        if (quizRepository.findById(quizId).isEmpty()) {
            throw new IllegalArgumentException("Quiz not found: " + quizId);
        }
        String pin;
        do {
            pin = Ids.pin();
        } while (registry.get(Integer.parseInt(pin)) != null
                || sessionRepository.findByPin(pin).isPresent());
        GameSession session = sessionRepository.create(quizId, hostUserId, pin, null);
        registry.put(Integer.parseInt(pin), new GameRoom(session.id(), quizId, pin, "LOBBY", maxPlayers, gameMode));
        eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.GameCreated(pin, quizId, gameMode.name()));
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
                if (!room.addHost(p)) throw new IllegalStateException("Room is full");
                room.setHostUuid(p.uuid());
                room.touch();
                broadcastLeaderboard(pin);
                eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.PlayerJoined(pin, safeName, p.uuid()));
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
            eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.PlayerJoined(pin, safeName, p.uuid()));
            return p;
        }
    }

    public void leave(String pin, String playerUuid) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        synchronized (room) {
            if (playerUuid.equals(room.hostUuid())) room.setHostUuid(null);
            // ponytail: soft-remove keeps score in the board so a rejoin
            // via token reclaims the seat. hardRemove was destroying
            // standings on every disconnect/page-refresh.
            room.softRemove(playerUuid);
        }
        eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.PlayerLeft(pin, playerUuid));
        broadcastRoomState(pin);
        broadcastLeaderboard(pin);
    }

    // ---------- rounds ----------

    public void startQuestion(String pin) {
        GameRoom room = require(pin);
        if (!"LOBBY".equals(room.status()) && !"REVIEW".equals(room.status())) return;
        List<Question> questions = questionRepository.findByQuiz(room.quizId());
        if (questions.isEmpty()) throw new IllegalStateException("Quiz has no questions");
        if (room.currentQuestionIndex() >= questions.size()) {
            endGame(pin);
            return;
        }
        Question q = questions.get(room.currentQuestionIndex());
        room.setCurrentQuestionId(q.id());
        room.setStatus("ACTIVE");
        room.clearRounds();
        sessionRepository.updateStatus(room.sessionId(), "ACTIVE");
        eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.QuestionStarted(
                pin, q.id(), room.currentQuestionIndex()));
        QuestionDto dto = toDto(q);
        long now = Instant.now().toEpochMilli();

        if (room.gameMode() == GameRoom.GameMode.PRACTICE) {
            // Practice: no timer, unlimited time.
            room.setCurrentQuestionStartEpochMs(now);
            room.setCurrentQuestionEndEpochMs(Long.MAX_VALUE);
            room.setQuestionEndBaseEpochMs(Long.MAX_VALUE);
            broadcastToRoom(pin, new QuestionStart("QUESTION_START", dto, -1, now, now));
            broadcastRoomState(pin);
            return;
        }

        if (room.gameMode() == GameRoom.GameMode.EXAM) {
            // Exam: use the total end time (set at game creation), no per-question timer.
            room.setCurrentQuestionStartEpochMs(now);
            long end = room.totalEndEpochMs();
            if (end <= 0) {
                long totalSec = questions.stream().mapToLong(Question::timeLimitSec).sum();
                end = now + totalSec * 1000;
            }
            room.setCurrentQuestionEndEpochMs(end);
            room.setQuestionEndBaseEpochMs(end);
            broadcastToRoom(pin, new QuestionStart("QUESTION_START", dto, (int) ((end - now) / 1000), now, now));
            broadcastRoomState(pin);
            // Schedule exam total timer.
            roundTimer.schedule(Integer.parseInt(pin), end, () -> onTimerExpired(pin));
            return;
        }

        // STANDARD, AUTO_PILOT, TEAM, BATTLE: per-question timer.
        room.setCurrentQuestionStartEpochMs(now);
        long end = now + (long) q.timeLimitSec() * 1000;
        room.setCurrentQuestionEndEpochMs(end);
        room.setQuestionEndBaseEpochMs(end);
        broadcastToRoom(pin, new QuestionStart("QUESTION_START", dto, q.timeLimitSec(), now, now));
        broadcastRoomState(pin);
        roundTimer.schedule(Integer.parseInt(pin), end, () -> onTimerExpired(pin));
    }

    public void nextQuestion(String pin) {
        GameRoom room = require(pin);
        synchronized (room) {
            if ("ACTIVE".equals(room.status())) {
                // Host skip mid-round: close the live round first so startQuestion
                // below (LOBBY/REVIEW-only) can proceed instead of silently stalling.
                roundTimer.cancel(Integer.parseInt(pin));
                room.setStatus("REVIEW");
                sessionRepository.updateStatus(room.sessionId(), "REVIEW");
            }
            nextQuestionLocked(room);
        }
    }

    private void nextQuestionLocked(GameRoom room) {
        String pin = room.pin();
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
        if (q == null) {
            ws.send(sessionIdOf(room, playerUuid),
                    new ErrorMessage("ERROR", "Unknown question — wait for the next round"));
            return;
        }
        QuestionType type = QuestionType.from(q.questionType());

        // Gate: score only during the live round for the current question.
        if (!"ACTIVE".equals(room.status()) || !questionId.equals(room.currentQuestionId())) {
            Player target = room.getPlayer(playerUuid);
            ws.send(target == null ? null : target.sessionId(),
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
        long taken = Math.max(0, (Instant.now().toEpochMilli() - room.currentQuestionStartEpochMs()) / 1000);
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

        // Every mode gets immediate per-player feedback (correct/wrong + score)
        // the moment they submit — host-led rooms reveal instantly per player.
        ws.send(p.sessionId(), new SubmissionResult("SUBMISSION_RESULT",
                questionId, total, correct, correct ? 1 : 0, 1, null));
        broadcastScoreChanged(room);
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
        int attempts = room.attemptCount(q.id(), playerUuid);
        String pin = room.pin();
        String questionId = q.id();
        long startMs = room.currentQuestionStartEpochMs();

        CodingOutcomeConsumer handler = new CodingOutcomeConsumer() {
            @Override
            public void accept(String uuid, int baseScore, boolean allPassed, int passed, int totalTests,
                               String aiFeedback) {
                int bonus;
                synchronized (room) {
                    boolean correct = allPassed;
                    bonus = streakBonus(room, uuid, correct, baseScore);
                    int total = baseScore + bonus;
                    room.applyScore(uuid, total);
                    room.recordRound(uuid, baseScore, bonus);
                }
                // ponytail: re-resolve the session at reply time — the player may
                // have reconnected (new sessionId) while the judge was running.
                Player current = room.getPlayer(uuid);
                ws.send(current == null ? null : current.sessionId(), new SubmissionResult("SUBMISSION_RESULT", questionId,
                        baseScore + bonus, allPassed, passed, totalTests, aiFeedback));
                broadcastScoreChanged(room);
            }

            @Override
            public void rejected(String uuid) {
                // Never reached the judge: refund the consumed attempt and say so.
                room.refundAttempt(questionId, uuid);
                Player current = room.getPlayer(uuid);
                ws.send(current == null ? null : current.sessionId(), new SubmissionResult("SUBMISSION_RESULT",
                        questionId, 0, false, 0, 0, null));
            }
        };

        long timeTakenSec = Math.max(0, (Instant.now().toEpochMilli() - startMs) / 1000);
        submissionProcessor.getObject().processCoding(room.sessionId(), pin,
                questionId, p.name(), playerUuid, language, source, attempts, settingsService.asMap(), timeTakenSec, handler)
                .thenAccept(accepted -> {
                    if (!accepted) {
                        // Busy-reject consumed nothing: refund the attempt so a
                        // retry is not punished for judge saturation.
                        room.refundAttempt(questionId, playerUuid);
                        Player current = room.getPlayer(playerUuid);
                        ws.send(current == null ? null : current.sessionId(), new ErrorMessage("ERROR",
                                "Judge queue is busy — resubmit shortly (auto-retry in 250ms)"));
                    }
                });
    }

    /** Updates streak and returns the bonus to add; 0 until the second correct in a row. */
    private int streakBonus(GameRoom room, String uuid, boolean correct, int base) {
        if (!correct) {
            room.resetStreak(uuid);
            return 0;
        }
        int streak = room.bumpStreak(uuid);
        if (streak < 2) return 0;
        double mult = Math.min(streak - 1, STREAK_BONUS_CAP_STEPS);
        return (int) Math.round(base * STREAK_BONUS_RATE * mult);
    }

    // ---------- host controls ----------

    public void forceSubmit(String pin) {
        GameRoom room = require(pin);
        if (!"ACTIVE".equals(room.status())) return;
        roundTimer.cancel(Integer.parseInt(pin));
        transitionToReview(pin);
    }

    public void extendTimer(String pin, int seconds) {
        GameRoom room = require(pin);
        if (!"ACTIVE".equals(room.status())) return;
        // ponytail: total cap +300s over the original deadline — repeated
        // +time calls previously grew the round without bound.
        long base = room.questionEndBaseEpochMs();
        if (base <= 0) base = room.currentQuestionEndEpochMs();
        long cap = base >= Long.MAX_VALUE - 300_000 ? Long.MAX_VALUE : base + 300_000;
        long cur = room.currentQuestionEndEpochMs();
        long extension = (long) seconds * 1000;
        long newEnd = extension >= 0 && cur >= Long.MAX_VALUE - extension
                ? Long.MAX_VALUE : cur + extension;
        if (newEnd > cap) newEnd = cap;
        long appliedSec = (newEnd - cur) / 1000;
        room.setCurrentQuestionEndEpochMs(newEnd);
        roundTimer.schedule(Integer.parseInt(pin), newEnd, () -> onTimerExpired(pin));
        broadcastToRoom(pin, new TimerUpdate("TIMER_UPDATE", newEnd, appliedSec));
    }

    public void kickPlayer(String pin, String playerUuid) {
        GameRoom room = require(pin);
        Player p = room.getPlayer(playerUuid);
        if (p != null) {
            String kickedSession = p.sessionId();
            ws.send(kickedSession, new ErrorMessage("ERROR", "You were removed by the host"));
            room.hardRemove(playerUuid);
            ws.close(kickedSession);
        }
        if (playerUuid.equals(room.hostUuid())) room.setHostUuid(null);
        broadcastRoomState(pin);
        broadcastLeaderboard(pin);
    }

    // ---------- team mode ----------

    public GameRoom.Team createTeam(String pin, String name) {
        GameRoom room = require(pin);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Team name is required");
        return room.createTeam(name);
    }

    public GameRoom.Team joinTeam(String pin, String teamId, String playerUuid) {
        GameRoom room = require(pin);
        if (teamId == null || teamId.isBlank()) throw new IllegalArgumentException("teamId is required");
        if (playerUuid == null || playerUuid.isBlank()) throw new IllegalArgumentException("playerUuid is required");
        if (room.getPlayer(playerUuid) == null) throw new IllegalArgumentException("Unknown player: " + playerUuid);
        GameRoom.Team team = room.joinTeam(teamId, playerUuid);
        if (team == null) throw new IllegalArgumentException("Unknown team: " + teamId);
        return team;
    }

    public java.util.List<GameRoom.Team> getTeams(String pin) {
        GameRoom room = require(pin);
        return new java.util.ArrayList<>(room.allTeams());
    }

    // ---------- battle mode ----------

    public void startBattle(String pin) {
        GameRoom room = require(pin);
        if (!"LOBBY".equals(room.status())) throw new IllegalStateException("Battle can only start from LOBBY");
        List<Player> players = room.players();
        if (players.size() < 2) {
            throw new IllegalStateException("Need at least 2 players for battle");
        }
        List<Question> questions = questionRepository.findByQuiz(room.quizId());
        if (questions.isEmpty()) throw new IllegalStateException("Quiz has no questions");
        if (room.currentQuestionIndex() < 0 || room.currentQuestionIndex() >= questions.size()) {
            throw new IllegalStateException("No question available for battle at index " + room.currentQuestionIndex());
        }
        Question q = questions.get(room.currentQuestionIndex());

        // Clear any prior battle state so a re-start never stacks matches.
        room.battleMatches().clear();
        room.bracket().clear();

        // Pair players for 1v1 matches.
        java.util.Collections.shuffle(players);
        for (int i = 0; i + 1 < players.size(); i += 2) {
            String matchId = Ids.uuid();
            GameRoom.BattleMatch match = new GameRoom.BattleMatch(
                    matchId, players.get(i).uuid(), players.get(i + 1).uuid(),
                    q.id(), null, null, false, false, null);
            room.addBattleMatch(match);
        }
        // Build bracket (round 1 matchups).
        java.util.List<String[]> rounds = new java.util.ArrayList<>();
        for (GameRoom.BattleMatch m : room.battleMatches()) {
            rounds.add(new String[]{m.p1Uuid(), m.p2Uuid()});
        }
        room.setBracket(rounds);

        if (players.size() % 2 == 1) {
            Player odd = players.get(players.size() - 1);
            Player current = room.getPlayer(odd.uuid());
            ws.send(current == null ? null : current.sessionId(),
                    new ErrorMessage("ERROR", "Odd player out — waiting for the next battle round"));
            broadcastRoomState(pin);
        }

        // Start the question for all players.
        startQuestion(pin);
    }

    public java.util.List<GameRoom.BattleMatch> getBattleMatches(String pin) {
        GameRoom room = require(pin);
        return room.battleMatches();
    }

    public java.util.List<String[]> getBracket(String pin) {
        GameRoom room = require(pin);
        return room.bracket();
    }

    private void onTimerExpired(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        // Fast guards stay on the timer thread (in-memory only); the blocking
        // DB work in transitionToReview/endGame runs on the sweeper instead so
        // one slow database never stalls other rooms' deadlines.
        String status;
        GameRoom.GameMode mode;
        long end;
        synchronized (room) {
            status = room.status();
            mode = room.gameMode();
            end = room.currentQuestionEndEpochMs();
        }
        if (!"ACTIVE".equals(status)) return;
        if (mode == GameRoom.GameMode.PRACTICE) return; // no timer in practice
        if (mode == GameRoom.GameMode.EXAM) {
            // Exam: total time expired, end game immediately.
            sweeper.execute(() -> {
                try {
                    endGame(pin);
                } catch (RuntimeException e) {
                    log.warn("Async exam expiry failed for room {}", pin, e);
                }
            });
            return;
        }
        if (Instant.now().toEpochMilli() < end) return;
        sweeper.execute(() -> {
            try {
                transitionToReview(pin);
            } catch (RuntimeException e) {
                log.warn("Async round expiry failed for room {}", pin, e);
            }
        });
    }

    private void transitionToReview(String pin) {
        GameRoom room = require(pin);
        // ponytail: idempotency guard — timer expiry + forceSubmit can race here.
        if (!"ACTIVE".equals(room.status())) return;
        roundTimer.cancel(Integer.parseInt(pin));
        room.setStatus("REVIEW");
        sessionRepository.updateStatus(room.sessionId(), "REVIEW");
        writeBuffer.flush();
        // Exam mode: suppress leaderboard during game (only show at end).
        if (room.gameMode() != GameRoom.GameMode.EXAM) {
            flushLeaderboardDelta(pin);
        }
        sendRoundResult(pin, true);
        broadcastRoomState(pin);
        eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.QuestionCompleted(
                pin, room.currentQuestionId(), room.currentQuestionIndex()));
        // Auto-pilot / Practice: auto-advance after brief review.
        if (room.gameMode() == GameRoom.GameMode.AUTO_PILOT
                || room.gameMode() == GameRoom.GameMode.PRACTICE) {
            roundTimer.schedule(Integer.parseInt(pin),
                    Instant.now().toEpochMilli() + (room.gameMode() == GameRoom.GameMode.PRACTICE ? 2000 : 3000),
                    () -> nextQuestion(pin));
        }
    }

    public void endGame(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        synchronized (room) {
            if ("ENDED".equals(room.status())) return;
            room.setStatus("ENDED");
        }
        roundTimer.cancel(Integer.parseInt(pin));
        int playerCount = room.players().size();
        int questionCount = questionRepository.findByQuiz(room.quizId()).size();
        sessionRepository.updateStatus(room.sessionId(), "ENDED");
        writeBuffer.flush();
        flushLeaderboardDelta(pin);

        // Build review data before removing the room.
        GameReview review = buildReview(room);
        broadcastToRoom(pin, review);

        eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.GameEnded(pin, playerCount, questionCount));
        registry.remove(Integer.parseInt(pin));
    }

    private GameReview buildReview(GameRoom room) {
        List<Question> questions = questionRepository.findByQuiz(room.quizId());
        List<com.sprintjudge.domain.models.Submission> submissions =
                submissionRepository.findBySession(room.sessionId());

        // Build question reviews.
        java.util.Map<String, List<com.sprintjudge.domain.models.Submission>> byQuestion = new java.util.HashMap<>();
        for (var s : submissions) {
            byQuestion.computeIfAbsent(s.questionId(), k -> new java.util.ArrayList<>()).add(s);
        }

        List<GameReview.QuestionReview> qReviews = new java.util.ArrayList<>();
        String hardestId = null, easiestId = null;
        double hardestRate = 1.1, easiestRate = -0.1;

        for (Question q : questions) {
            List<com.sprintjudge.domain.models.Submission> qSubs =
                    byQuestion.getOrDefault(q.id(), List.of());
            int total = qSubs.size();
            int correct = 0;
            long totalAttempts = 0;
            for (var s : qSubs) {
                if (s.correct()) correct++;
                totalAttempts += s.attemptCount();
            }
            double rate = total > 0 ? (double) correct / total : 0;
            double avgAttempts = total > 0 ? (double) totalAttempts / total : 0;

            JsonNode answer = QuestionAnswers.answerPayload(
                    QuestionType.from(q.questionType()), Json.readTree(q.config()));

            qReviews.add(new GameReview.QuestionReview(
                    q.id(), q.title(), q.questionType(), q.timeLimitSec(),
                    q.pointsBase(), answer, total, correct, rate, avgAttempts));

            if (rate < hardestRate) { hardestRate = rate; hardestId = q.id(); }
            if (rate > easiestRate) { easiestRate = rate; easiestId = q.id(); }
        }

        // Build player reviews.
        java.util.Map<String, List<com.sprintjudge.domain.models.Submission>> byPlayer = new java.util.HashMap<>();
        for (var s : submissions) {
            byPlayer.computeIfAbsent(s.playerUuid(), k -> new java.util.ArrayList<>()).add(s);
        }

        List<GameReview.PlayerReview> pReviews = new java.util.ArrayList<>();
        int totalCorrect = 0, totalAttempts = 0;
        double totalScore = 0;

        for (Player p : room.players()) {
            List<com.sprintjudge.domain.models.Submission> pSubs =
                    byPlayer.getOrDefault(p.uuid(), List.of());
            List<GameReview.PlayerAnswer> answers = new java.util.ArrayList<>();
            int pScore = 0;
            for (var s : pSubs) {
                answers.add(new GameReview.PlayerAnswer(s.questionId(), s.correct(), s.scoreEarned(), s.attemptCount()));
                if (s.correct()) totalCorrect++;
                totalAttempts += s.attemptCount();
                pScore += s.scoreEarned();
            }
            pReviews.add(new GameReview.PlayerReview(p.uuid(), p.name(), pScore, answers));
            totalScore += pScore;
        }

        GameReview.ClassStats stats = new GameReview.ClassStats(
                room.players().size(), questions.size(),
                room.players().isEmpty() ? 0 : totalScore / room.players().size(),
                totalCorrect, totalAttempts, hardestId, easiestId);

        return new GameReview("GAME_REVIEW", room.leaderboard(), qReviews, pReviews, stats);
    }

    /** Sweeps unrecoverable rooms: lobbies idle past the TTL, plus ACTIVE/REVIEW
     * rooms with zero connected players past the TTL (abandoned mid-game).
     * Eviction cancels timers, flushes the audit trail, and ends the session
     * row so neither pins nor memory leak. */
    public void sweepIdleRooms() {
        long now = System.currentTimeMillis();
        boolean evicted = false;
        for (GameRoom room : registry.snapshot()) {
            String status = room.status();
            boolean eligible = "LOBBY".equals(status) || "ACTIVE".equals(status) || "REVIEW".equals(status);
            if (!eligible || room.idleMs(now) <= LOBBY_TTL_MS) continue;
            synchronized (room) {
                if (room.connectedCount() != 0) continue;
                int key = Integer.parseInt(room.pin());
                roundTimer.cancel(key);
                try {
                    sessionRepository.updateStatus(room.sessionId(), "ENDED");
                } catch (RuntimeException e) {
                    log.warn("Sweep failed to end session for room {}", room.pin(), e);
                }
                registry.remove(key);
                evicted = true;
            }
        }
        if (evicted) {
            try {
                writeBuffer.flush();
            } catch (RuntimeException e) {
                log.warn("Sweep flush failed", e);
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

    /**
     * Score-driven board updates. Host-led live modes (STANDARD/TEAM/BATTLE/
     * AUTO_PILOT) freeze the public board mid-round — deltas accumulate in the
     * ledger and flush at review — while each player still gets instant
     * per-player feedback via SUBMISSION_RESULT. PRACTICE/EXAM keep the
     * historical live-board behavior.
     */
    private void broadcastScoreChanged(GameRoom room) {
        GameRoom.GameMode mode = room.gameMode();
        if (mode == GameRoom.GameMode.PRACTICE || mode == GameRoom.GameMode.EXAM) {
            broadcastLeaderboard(room.pin());
        }
    }

    public void broadcastLeaderboard(String pin) {
        int key = Integer.parseInt(pin);
        scheduler.markDirty(key, () -> flushLeaderboardDelta(pin));
    }

    public void flushLeaderboardDelta(String pin) {
        GameRoom room = registry.get(Integer.parseInt(pin));
        if (room == null) return;
        com.sprintjudge.service.leaderboard.DeltaLedger.Batch batch = room.board().drainDeltas(false);
        if (batch.resync() && batch.upserts().isEmpty()) {
            // Nothing pending: a client that missed the last batch still has a
            // seq gap, so heal with the authoritative baseline instead of
            // dropping the flush. Empty boards (seq 0) stay silent.
            if (batch.seq() <= 0 || room.board().size() == 0) return;
            batch = room.board().fullBatch();
        }
        String json = deltaJson(batch);
        if (json != null) ws.broadcastRaw(playerSessionIds(pin), json);
        // Deltas that arrived mid-flush would otherwise wait for the next
        // score event; re-mark so the next tick drains them.
        if (room.board().pendingDeltaCount() > 0) broadcastLeaderboard(pin);
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
        return new RoomState("ROOM_STATE", room.status(), count, room.currentQuestionId(), players,
                room.gameMode().name());
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
