package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.dto.BattleBracket;
import com.sprintjudge.domain.dto.BattleChampion;
import com.sprintjudge.domain.dto.BattleResult;
import com.sprintjudge.domain.dto.ErrorMessage;
import com.sprintjudge.domain.dto.GameReview;
import com.sprintjudge.domain.dto.LeaderboardDelta;
import com.sprintjudge.domain.dto.LeaderboardEntry;
import com.sprintjudge.domain.dto.QuestionDto;
import com.sprintjudge.domain.dto.QuestionStart;
import com.sprintjudge.domain.dto.RoomState;
import com.sprintjudge.domain.dto.RoundResult;
import com.sprintjudge.domain.dto.SubmissionResult;
import com.sprintjudge.domain.dto.TimerUpdate;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live-game orchestrator: rooms, rounds, scoring, leaderboard transport.
 *
 * <p>
 * Authoritative state lives in {@link GameRoom}; this class is the only writer
 * of round transitions and the only place that computes the visible award
 * (selection + coding share the streak/bonus math here).
 */
@Service
@SuppressWarnings("all")
public class GameRoomManager implements LeaderboardBroadcaster {

	private static final Logger log = LoggerFactory.getLogger(GameRoomManager.class);
	private static final int MAX_ATTEMPTS_PER_QUESTION = 50;
	// ponytail: streak bonus = roundScore * BONUS_RATE * min(streak-1,
	// BONUS_CAP_STEPS).
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

	private final java.util.concurrent.ScheduledExecutorService sweeper = java.util.concurrent.Executors
			.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r, "oq-room-sweep");
					t.setDaemon(true);
					return t;
				}
			});

	public GameRoomManager(GameSessionRepository sessionRepository, QuizRepository quizRepository,
			QuestionRepository questionRepository, SubmissionRepository submissionRepository,
			ScoringEngine scoringEngine,
			org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> submissionProcessor,
			WebSocketSessionManager ws, EvaluationService evaluationService, AdminSettingsService settingsService,
			BroadcastScheduler scheduler, SubmissionWriteBuffer writeBuffer, RoundTimeoutScheduler roundTimer,
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

	public GameSession createRoom(String quizId, String hostUserId, String gameMode) {
		if (quizRepository.findById(quizId).isEmpty()) {
			throw new IllegalArgumentException("Quiz not found: " + quizId);
		}
		String pin;
		do {
			pin = Ids.pin();
			String candidate = pin;
			// ENDED rows never come back: reclaim the PIN instead of letting
			// 900k dead rows exhaust the space.
			java.util.Optional<GameSession> opt = sessionRepository.findByPin(candidate);
			if (opt.isPresent()) {
				GameSession s = opt.get();
				if ("ENDED".equals(s.getStatus()))
					sessionRepository.deleteByPin(candidate);
			}
		} while (registry.get(Integer.parseInt(pin)) != null || sessionRepository.findByPin(pin).isPresent());
		GameSession session = sessionRepository.create(quizId, hostUserId, pin, null, gameMode);
		registry.put(Integer.parseInt(pin), new GameRoom(session.getId(), quizId, pin, "LOBBY", maxPlayers, gameMode));
		eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.GameCreated(pin, quizId, gameMode));
		return session;
	}

	public Player join(String pin, String name, String sessionId, String role, String rejoinToken) {
		int key = Integer.parseInt(pin);
		GameRoom room = registry.get(key);
		if (room == null) {
			// Lookup is done OUTSIDE the registry write lock (H6 fix); the
			// factory only constructs -?no DB access under lock.
			GameSession s = sessionRepository.findByPin(pin).orElse(null);
			if (s == null)
				throw new IllegalArgumentException("Invalid PIN");
			if ("ENDED".equals(s.getStatus()))
				throw new IllegalStateException("This game has ended");
			room = registry.computeIfAbsent(key, new java.util.function.IntFunction<GameRoom>() {
				@Override
				public GameRoom apply(int p) {
					return new GameRoom(s.getId(), s.getQuizId(), pin, s.getStatus(), maxPlayers,
							s.getGameMode() == null || s.getGameMode().isBlank() ? "STANDARD" : s.getGameMode());
				}
			});
		}
		String safeName = NameSanitizer.sanitize(name);
		if (safeName.isEmpty())
			safeName = "Player";
		boolean isHost = "host".equalsIgnoreCase(role);
		synchronized (room) {
			if (isHost) {
				if (room.getHostUuid() != null)
					throw new IllegalStateException("A host is already connected");
				Player p = new Player(Ids.uuid(), safeName, 0, sessionId, true, Ids.uuid());
				if (!room.addHost(p))
					throw new IllegalStateException("Room is full");
				room.setHostUuid(p.getUuid());
				room.touch();
				broadcastLeaderboard(pin);
				eventPublisher.publishEvent(
						new com.sprintjudge.service.event.GameEvent.PlayerJoined(pin, safeName, p.getUuid()));
				return p;
			}
			// Read-only projector: receives broadcasts, takes no seat, no score.
			if ("spectator".equalsIgnoreCase(role)) {
				Player viewer = new Player(Ids.uuid(), safeName, 0, sessionId, true, null);
				if (!room.addSpectator(viewer))
					throw new IllegalStateException("Room is full");
				room.touch();
				return viewer;
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
			if (room.isFull())
				throw new IllegalStateException("Room is full");
			Player joined = new Player(Ids.uuid(), safeName, 0, sessionId, true, Ids.uuid());
			// isFull above holds under synchronized(room), so the add cannot fail.
			room.addPlayer(joined);
			room.touch();
			// Solo practice has no host to press start: kick off Q1 when the
			// first player joins a fresh PRACTICE room. The LOBBY check (same
			// atomic flip startQuestion uses) keeps later joins from
			// re-firing the round.
			boolean autostart = GameRoom.GameMode.PRACTICE.equals(room.getGameMode())
					&& "LOBBY".equals(room.getStatus());
			broadcastLeaderboard(pin);
			eventPublisher.publishEvent(
					new com.sprintjudge.service.event.GameEvent.PlayerJoined(pin, safeName, joined.getUuid()));
			if (autostart) {
				log.info("Practice room {} auto-starting Q1 for first join", pin);
				startQuestion(pin);
			}
			return joined;
		}
	}

	public void leave(String pin, String playerUuid) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null)
			return;
		synchronized (room) {
			if (playerUuid.equals(room.getHostUuid()))
				room.setHostUuid(null);
			room.removeSpectator(playerUuid);
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
		synchronized (room) {
			// Atomic LOBBY/REVIEW -> ACTIVE flip: a racing timer expiry must
			// not double-fire QUESTION_START for the same round.
			if (!"LOBBY".equals(room.getStatus()) && !"REVIEW".equals(room.getStatus()))
				return;
			room.setStatus("ACTIVE");
		}
		List<Question> questions = questionRepository.findByQuiz(room.getQuizId());
		if (questions.isEmpty())
			throw new IllegalStateException("Quiz has no questions");
		if (room.getCurrentQuestionIndex() >= questions.size()) {
			endGame(pin);
			return;
		}
		Question q = questions.get(room.getCurrentQuestionIndex());
		room.setCurrentQuestionId(q.getId());
		room.clearRounds();
		sessionRepository.updateStatus(room.getSessionId(), "ACTIVE");
		eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.QuestionStarted(pin, q.getId(),
				room.getCurrentQuestionIndex()));
		QuestionDto dto = toDto(q);
		long now = Instant.now().toEpochMilli();

		if (GameRoom.GameMode.PRACTICE.equals(room.getGameMode())) {
			// Practice: no timer, unlimited time.
			room.setCurrentQuestionStartEpochMs(now);
			room.setCurrentQuestionEndEpochMs(Long.MAX_VALUE);
			room.setQuestionEndBaseEpochMs(Long.MAX_VALUE);
			broadcastToRoom(pin, new QuestionStart("QUESTION_START", dto, -1, now, now));
			broadcastRoomState(pin);
			return;
		}

		if (GameRoom.GameMode.EXAM.equals(room.getGameMode())) {
			// Exam: fix the total budget once on the first question. Recomputing
			// "now + sum(limits)" each round pushed the deadline forward every
			// question and extended the exam indefinitely.
			room.setCurrentQuestionStartEpochMs(now);
			long end = room.getTotalEndEpochMs();
			if (end <= 0) {
				long totalSec = 0;
				for (Question examQuestion : questions)
					totalSec += examQuestion.getTimeLimitSec();
				end = now + totalSec * 1000;
				room.setTotalEndEpochMs(end);
			}
			room.setCurrentQuestionEndEpochMs(end);
			room.setQuestionEndBaseEpochMs(end);
			broadcastToRoom(pin, new QuestionStart("QUESTION_START", dto, (int) ((end - now) / 1000), now, now));
			broadcastRoomState(pin);
			// Schedule exam total timer.
			roundTimer.schedule(Integer.parseInt(pin), end, new Runnable() {
				@Override
				public void run() {
					onTimerExpired(pin);
				}
			});
			return;
		}

		// STANDARD, AUTO_PILOT, TEAM, BATTLE: per-question timer.
		room.setCurrentQuestionStartEpochMs(now);
		long end = now + (long) q.getTimeLimitSec() * 1000;
		room.setCurrentQuestionEndEpochMs(end);
		room.setQuestionEndBaseEpochMs(end);
		broadcastToRoom(pin, new QuestionStart("QUESTION_START", dto, q.getTimeLimitSec(), now, now));
		broadcastRoomState(pin);
		roundTimer.schedule(Integer.parseInt(pin), end, new Runnable() {
			@Override
			public void run() {
				onTimerExpired(pin);
			}
		});
	}

	public void nextQuestion(String pin) {
		GameRoom room = require(pin);
		synchronized (room) {
			if ("ACTIVE".equals(room.getStatus())) {
				// Host skip mid-round: close the live round first so startQuestion
				// below (LOBBY/REVIEW-only) can proceed instead of silently stalling.
				roundTimer.cancel(Integer.parseInt(pin));
				room.setStatus("REVIEW");
				sessionRepository.updateStatus(room.getSessionId(), "REVIEW");
			}
			if (GameRoom.GameMode.BATTLE.equals(room.getGameMode())) {
				if (room.currentBattleRound() == null) {
					// The host pressed "Start round" instead of "Start battle":
					// a battle question without pairings would score nobody out.
					startBattle(pin);
					return;
				}
				// Advance the bracket before the next question: pair the winners,
				// hand the odd player a bye, and refresh both clients' views.
				room.clearBattleAnswerTimes();
				room.beginBattleRound(pairUp(room.getBattleWinners()));
				broadcastBracket(room);
				broadcastBattleRound(room);
			}
			nextQuestionLocked(room);
		}
	}

	private void nextQuestionLocked(GameRoom room) {
		String pin = room.getPin();
		if (!"LOBBY".equals(room.getStatus())) {
			room.setCurrentQuestionIndex(room.getCurrentQuestionIndex() + 1);
			sessionRepository.setCurrentIndex(room.getSessionId(), room.getCurrentQuestionIndex());
		}
		if (room.getCurrentQuestionIndex() >= questionRepository.findByQuiz(room.getQuizId()).size()) {
			endGame(pin);
		} else {
			startQuestion(pin);
		}
	}

	// ---------- submissions ----------

	public void submit(String pin, String questionId, String playerUuid, String language, JsonNode response) {
		GameRoom room = require(pin);
		if (playerUuid != null && playerUuid.equals(room.getHostUuid())) {
			ws.send(sessionIdOf(room, playerUuid), new ErrorMessage("ERROR", "Hosts can't submit answers"));
			return;
		}
		Question q = questionRepository.findById(questionId).orElse(null);
		if (q == null) {
			ws.send(sessionIdOf(room, playerUuid),
					new ErrorMessage("ERROR", "Unknown question -?wait for the next round"));
			return;
		}
		String type = QuestionType.from(q.getQuestionType());

		// Gate: score only during the live round for the current question.
		if (!"ACTIVE".equals(room.getStatus()) || !questionId.equals(room.getCurrentQuestionId())) {
			Player target = room.getPlayer(playerUuid);
			ws.send(target == null ? null : target.getSessionId(),
					new ErrorMessage("ERROR", "Round is locked -?answers are no longer accepted"));
			return;
		}
		// Membership before attempts: a submit with no live seat (left,
		// kicked, or stale session) must neither burn an attempt nor score.
		Player member = room.getPlayer(playerUuid);
		if (member == null || !member.isConnected())
			return;
		if (!room.tryBeginAttempt(questionId, playerUuid, MAX_ATTEMPTS_PER_QUESTION)) {
			ws.send(sessionIdOf(room, playerUuid),
					new ErrorMessage("ERROR", "Attempt limit reached for this question"));
			return;
		}

		if (QuestionType.isCoding(type)) {
			submitCoding(room, q, playerUuid, language, response);
			return;
		}

		double fraction = evaluationService.evaluateCorrectness(q, response);
		Player p = room.getPlayer(playerUuid);
		if (p == null)
			return;
		long taken = Math.max(0, (Instant.now().toEpochMilli() - room.getCurrentQuestionStartEpochMs()) / 1000);
		int attempts = room.attemptCount(questionId, playerUuid);
		boolean correct = fraction >= 1.0;
		int base = scoringEngine.scoreSelection(fraction, taken, q.getTimeLimitSec(), attempts, q.getPointsBase(),
				settingsService.asMap());
		int bonus = streakBonus(room, playerUuid, correct, base);
		int total = base + bonus;

		writeBuffer.offer(new Submission(Ids.uuid(), room.getSessionId(), questionId, p.getName(), playerUuid,
				Json.write(response), total, correct, null, attempts, Instant.now()));
		room.applyScore(playerUuid, total);
		room.recordRound(playerUuid, base, bonus, correct);
		applyTeamScore(room, playerUuid, total);
		if (GameRoom.GameMode.BATTLE.equals(room.getGameMode())) {
			room.markBattleAnswer(playerUuid, System.currentTimeMillis());
		}

		// Every mode gets immediate per-player feedback (correct/wrong + score)
		// the moment they submit -?host-led rooms reveal instantly per player.
		ws.send(p.getSessionId(),
				new SubmissionResult("SUBMISSION_RESULT", questionId, total, correct, correct ? 1 : 0, 1, null));
		eventPublisher.publishEvent(
				new com.sprintjudge.service.event.GameEvent.SubmissionReceived(pin, questionId, playerUuid, correct));
		broadcastScoreChanged(room);
		// Solo practice has no host to advance the round: each submit closes
		// it, and the scheduled auto-advance takes it from there. Host-led
		// modes stay put until the host (or the timer) moves them on.
		if (GameRoom.GameMode.PRACTICE.equals(room.getGameMode())) {
			transitionToReview(pin);
		}
	}

	private void submitCoding(GameRoom room, Question q, String playerUuid, String language, JsonNode response) {
		Player p = room.getPlayer(playerUuid);
		if (p == null)
			return;
		List<String> allowed = q.getLanguagesAllowed();
		if (allowed != null && !allowed.isEmpty()) {
			boolean allowedLang = false;
			for (String a : allowed) {
				if (a.equalsIgnoreCase(language)) {
					allowedLang = true;
					break;
				}
			}
			if (!allowedLang) {
				ws.send(p.getSessionId(), new ErrorMessage("ERROR", "Language not allowed for this question"));
				return;
			}
		}
		String source = response == null ? "" : response.path("source").asText("");
		int attempts = room.attemptCount(q.getId(), playerUuid);
		String pin = room.getPin();
		String questionId = q.getId();
		long startMs = room.getCurrentQuestionStartEpochMs();

		CodingOutcomeConsumer handler = new CodingOutcomeConsumer() {
			@Override
			public void accept(String uuid, int baseScore, boolean allPassed, int passed, int totalTests,
					String aiFeedback) {
				int bonus;
				synchronized (room) {
					// A judge result is asynchronous: the host may have skipped or
					// the timer may have expired while the code was running. Never
					// apply a stale result to a different round (or a removed room).
					if (!"ACTIVE".equals(room.getStatus()) || !questionId.equals(room.getCurrentQuestionId()))
						return;
					boolean correct = allPassed;
					bonus = streakBonus(room, uuid, correct, baseScore);
					int total = baseScore + bonus;
					room.applyScore(uuid, total);
					room.recordRound(uuid, baseScore, bonus, correct);
					applyTeamScore(room, uuid, total);
					if (GameRoom.GameMode.BATTLE.equals(room.getGameMode())) {
						room.markBattleAnswer(uuid, System.currentTimeMillis());
					}
				}
				// ponytail: re-resolve the session at reply time -?the player may
				// have reconnected (new sessionId) while the judge was running.
				Player current = room.getPlayer(uuid);
				ws.send(current == null ? null : current.getSessionId(), new SubmissionResult("SUBMISSION_RESULT",
						questionId, baseScore + bonus, allPassed, passed, totalTests, aiFeedback));
				eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.SubmissionReceived(pin,
						questionId, uuid, allPassed));
				broadcastScoreChanged(room);
				// Solo practice has no host: advance once the judged result is
				// in (never on busy-reject -?the attempt was refunded, the
				// round must stay open for the retry).
				if (GameRoom.GameMode.PRACTICE.equals(room.getGameMode())) {
					transitionToReview(pin);
				}
			}

			@Override
			public void rejected(String uuid) {
				// Never reached the judge: refund the consumed attempt and say so.
				room.refundAttempt(questionId, uuid);
				Player current = room.getPlayer(uuid);
				ws.send(current == null ? null : current.getSessionId(),
						new SubmissionResult("SUBMISSION_RESULT", questionId, 0, false, 0, 0, null));
			}
		};

		long timeTakenSec = Math.max(0, (Instant.now().toEpochMilli() - startMs) / 1000);
		submissionProcessor.getObject()
				.processCoding(room.getSessionId(), pin, questionId, p.getName(), playerUuid, language, source,
						attempts, settingsService.asMap(), timeTakenSec, handler)
				.thenAccept(new java.util.function.Consumer<Boolean>() {
					@Override
					public void accept(Boolean accepted) {
						if (!accepted) {
							// Busy-reject consumed nothing: refund the attempt so a
							// retry is not punished for judge saturation.
							room.refundAttempt(questionId, playerUuid);
							Player current = room.getPlayer(playerUuid);
							ws.send(current == null ? null : current.getSessionId(), new ErrorMessage("ERROR",
									"Judge queue is busy -?resubmit shortly (auto-retry in 250ms)"));
						}
					}
				});
	}

	/**
	 * Updates streak and returns the bonus to add; 0 until the second correct in a
	 * row.
	 */
	private int streakBonus(GameRoom room, String uuid, boolean correct, int base) {
		if (!correct) {
			room.resetStreak(uuid);
			return 0;
		}
		int streak = room.bumpStreak(uuid);
		if (streak < 2)
			return 0;
		double mult = Math.min(streak - 1, STREAK_BONUS_CAP_STEPS);
		return (int) Math.round(base * STREAK_BONUS_RATE * mult);
	}

	// ---------- host controls ----------

	public void forceSubmit(String pin) {
		GameRoom room = require(pin);
		if (!"ACTIVE".equals(room.getStatus()))
			return;
		roundTimer.cancel(Integer.parseInt(pin));
		transitionToReview(pin);
	}

	public void extendTimer(String pin, int seconds) {
		GameRoom room = require(pin);
		if (!"ACTIVE".equals(room.getStatus()))
			return;
		// ponytail: total cap +300s over the original deadline -?repeated
		// +time calls previously grew the round without bound.
		long base = room.getQuestionEndBaseEpochMs();
		if (base <= 0)
			base = room.getCurrentQuestionEndEpochMs();
		long cap = base >= Long.MAX_VALUE - 300_000 ? Long.MAX_VALUE : base + 300_000;
		long cur = room.getCurrentQuestionEndEpochMs();
		long extension = (long) seconds * 1000;
		long newEnd = extension >= 0 && cur >= Long.MAX_VALUE - extension ? Long.MAX_VALUE : cur + extension;
		if (newEnd > cap)
			newEnd = cap;
		long appliedSec = (newEnd - cur) / 1000;
		room.setCurrentQuestionEndEpochMs(newEnd);
		// Cancel-then-arm: without the cancel, the pre-extension deadline
		// would still fire (early exam end / duplicate review).
		roundTimer.cancel(Integer.parseInt(pin));
		roundTimer.schedule(Integer.parseInt(pin), newEnd, new Runnable() {
			@Override
			public void run() {
				onTimerExpired(pin);
			}
		});
		broadcastToRoom(pin, new TimerUpdate("TIMER_UPDATE", newEnd, appliedSec));
	}

	public void kickPlayer(String pin, String playerUuid) {
		GameRoom room = require(pin);
		Player p = room.getPlayer(playerUuid);
		if (p != null) {
			String kickedSession = p.getSessionId();
			ws.send(kickedSession, new ErrorMessage("ERROR", "You were removed by the host"));
			room.hardRemove(playerUuid);
			ws.close(kickedSession);
		}
		if (playerUuid.equals(room.getHostUuid()))
			room.setHostUuid(null);
		broadcastRoomState(pin);
		broadcastLeaderboard(pin);
	}

	// ---------- team mode ----------

	public GameRoom.Team createTeam(String pin, String name) {
		GameRoom room = require(pin);
		requireLobby(room, "Teams can only be created in the lobby");
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Team name is required");
		GameRoom.Team team = room.createTeam(name.trim());
		broadcastTeamList(room);
		return team;
	}

	public GameRoom.Team joinTeam(String pin, String teamId, String playerUuid) {
		GameRoom room = require(pin);
		if (teamId == null || teamId.isBlank())
			throw new IllegalArgumentException("teamId is required");
		if (playerUuid == null || playerUuid.isBlank())
			throw new IllegalArgumentException("playerUuid is required");
		if (room.getPlayer(playerUuid) == null)
			throw new IllegalArgumentException("Unknown player: " + playerUuid);
		requireLobby(room, "Teams are locked once the game starts");
		GameRoom.Team team = room.joinTeam(teamId, playerUuid);
		if (team == null)
			throw new IllegalArgumentException("Unknown team: " + teamId);
		broadcastTeamList(room);
		return team;
	}

	public void leaveTeam(String pin, String playerUuid) {
		GameRoom room = require(pin);
		requireLobby(room, "Teams are locked once the game starts");
		room.leaveTeam(playerUuid);
		broadcastTeamList(room);
	}

	public java.util.List<GameRoom.Team> getTeams(String pin) {
		GameRoom room = require(pin);
		return room.teamsSorted();
	}

	private static void requireLobby(GameRoom room, String message) {
		if (!"LOBBY".equals(room.getStatus()))
			throw new IllegalStateException(message);
	}

	/**
	 * Team score follows the player's awarded points, in selection and coding
	 * alike.
	 */
	private void applyTeamScore(GameRoom room, String playerUuid, int delta) {
		if (!GameRoom.GameMode.TEAM.equals(room.getGameMode()))
			return;
		String teamId = room.teamIdOf(playerUuid);
		if (teamId != null)
			room.applyTeamScore(teamId, delta);
		broadcastTeamList(room);
	}

	private void broadcastTeamList(GameRoom room) {
		broadcastToRoom(room.getPin(),
				Map.of("type", "TEAM_LIST", "teams", new java.util.ArrayList<>(room.teamsSorted())));
	}

	// ---------- battle mode ----------

	public void startBattle(String pin) {
		GameRoom room = require(pin);
		if (!"LOBBY".equals(room.getStatus()))
			throw new IllegalStateException("Battle can only start from LOBBY");
		// Contestants only: the host holds a roster seat but never fights.
		String hostUuid = room.getHostUuid();
		List<Player> players = new java.util.ArrayList<>();
		for (Player p : room.getPlayers()) {
			if (!p.getUuid().equals(hostUuid))
				players.add(p);
		}
		if (players.size() < 2) {
			throw new IllegalStateException("Need at least 2 players for battle");
		}
		List<Question> questions = questionRepository.findByQuiz(room.getQuizId());
		if (questions.isEmpty())
			throw new IllegalStateException("Quiz has no questions");
		if (room.getCurrentQuestionIndex() < 0 || room.getCurrentQuestionIndex() >= questions.size()) {
			throw new IllegalStateException(
					"No question available for battle at index " + room.getCurrentQuestionIndex());
		}

		room.resetBattle();
		List<String> playerUuids = new java.util.ArrayList<>();
		for (Player pl : players)
			playerUuids.add(pl.getUuid());
		List<GameRoom.BattlePair> pairs = pairUp(playerUuids);
		room.beginBattleRound(pairs);
		Question q = questions.get(room.getCurrentQuestionIndex());
		for (GameRoom.BattlePair pair : pairs) {
			if (pair.isBye())
				continue;
			room.addBattleMatch(new GameRoom.BattleMatch(Ids.uuid(), pair.getP1Uuid(), pair.getP2Uuid(), q.getId(),
					null, null, false, false, null));
		}
		broadcastBracket(room);
		broadcastBattleRound(room);
		startQuestion(pin);
	}

	/** Sequential pairing; an odd player out advances on a bye. */
	static List<GameRoom.BattlePair> pairUp(List<String> uuids) {
		List<GameRoom.BattlePair> pairs = new java.util.ArrayList<>();
		for (int i = 0; i + 1 < uuids.size(); i += 2) {
			pairs.add(new GameRoom.BattlePair(uuids.get(i), uuids.get(i + 1), null, false));
		}
		if (uuids.size() % 2 == 1) {
			String bye = uuids.get(uuids.size() - 1);
			pairs.add(new GameRoom.BattlePair(bye, null, bye, true));
		}
		return pairs;
	}

	public java.util.List<GameRoom.BattleRound> getBracket(String pin) {
		return require(pin).getBracketRounds();
	}

	public BattleBracket getBracketView(String pin) {
		GameRoom room = require(pin);
		List<BattleBracket.Round> rounds = new java.util.ArrayList<>();
		for (GameRoom.BattleRound r : room.getBracketRounds()) {
			List<BattleBracket.Pair> pairs = new java.util.ArrayList<>();
			for (GameRoom.BattlePair p : r.getPairs()) {
				pairs.add(new BattleBracket.Pair(p.getP1Uuid(), p.getP2Uuid(), p.getWinnerUuid(), p.isBye()));
			}
			rounds.add(new BattleBracket.Round(r.getNumber(), pairs));
		}
		String championUuid = room.getChampionUuid();
		Player champion = championUuid == null ? null : room.getPlayer(championUuid);
		return new BattleBracket("BRACKET", rounds, championUuid, champion == null ? null : champion.getName());
	}

	public java.util.List<GameRoom.BattleMatch> getBattleMatches(String pin) {
		return require(pin).getBattleMatches();
	}

	private void broadcastBracket(GameRoom room) {
		broadcastToRoom(room.getPin(), getBracketView(room.getPin()));
	}

	/** Tells each fighter who they face in the round that just opened. */
	private void broadcastBattleRound(GameRoom room) {
		GameRoom.BattleRound cur = room.currentBattleRound();
		for (Player p : room.getPlayers()) {
			GameRoom.BattlePair pair = room.pairOf(p.getUuid());
			if (pair == null || p.getSessionId() == null)
				continue;
			String opponentUuid = p.getUuid().equals(pair.getP1Uuid()) ? pair.getP2Uuid() : pair.getP1Uuid();
			Player opponent = opponentUuid == null ? null : room.getPlayer(opponentUuid);
			ws.send(p.getSessionId(),
					Map.of("type", "BATTLE_OPPONENT", "round", cur.getNumber(), "opponentUuid",
							opponentUuid == null ? "" : opponentUuid, "opponentName",
							opponent == null ? "" : opponent.getName(), "bye", pair.isBye()));
		}
	}

	/**
	 * Resolves the round at reveal: best score wins, then the faster answer, then
	 * total score, then the first-listed player (a perfect dead heat must still
	 * advance exactly one player).
	 */
	private void resolveBattleRound(GameRoom room) {
		GameRoom.BattleRound cur = room.currentBattleRound();
		if (cur == null)
			return;
		List<GameRoom.BattlePair> resolved = new java.util.ArrayList<>();
		List<String> winners = new java.util.ArrayList<>();
		List<BattleResult.Match> matches = new java.util.ArrayList<>();
		for (GameRoom.BattlePair pair : cur.getPairs()) {
			String winner;
			if (pair.isBye() || pair.getP2Uuid() == null) {
				winner = pair.getP1Uuid();
			} else {
				winner = decideWinner(room, pair);
			}
			resolved.add(pair.withWinner(winner));
			winners.add(winner);
			matches.add(new BattleResult.Match(pair.getP1Uuid(), pair.getP2Uuid(), winner, pair.isBye()));
		}
		room.resolveCurrentBattleRound(resolved);
		broadcastToRoom(room.getPin(), new BattleResult("BATTLE_RESULT", cur.getNumber(), matches, winners));
		broadcastBracket(room);

		if (winners.size() == 1) {
			String championUuid = winners.get(0);
			room.setChampionUuid(championUuid);
			room.setBattleWinners(winners);
			Player champion = room.getPlayer(championUuid);
			broadcastToRoom(room.getPin(), new BattleChampion("BATTLE_CHAMPION", championUuid,
					champion == null ? "Player" : champion.getName()));
			broadcastBracket(room);
			endGame(room.getPin());
		} else {
			room.setBattleWinners(winners);
		}
	}

	private String decideWinner(GameRoom room, GameRoom.BattlePair pair) {
		String a = pair.getP1Uuid();
		String b = pair.getP2Uuid();
		int roundA = roundTotal(room, a);
		int roundB = roundTotal(room, b);
		if (roundA != roundB)
			return roundA > roundB ? a : b;
		Long timeA = room.battleAnswerMs(a);
		Long timeB = room.battleAnswerMs(b);
		if (timeA != null && timeB != null && !timeA.equals(timeB)) {
			return timeA < timeB ? a : b;
		}
		long scoreA = room.getBoard().scoreOf(a);
		long scoreB = room.getBoard().scoreOf(b);
		if (scoreA != scoreB)
			return scoreA > scoreB ? a : b;
		// shortcut: a perfect dead heat advances the first-listed player; upgrade
		// to a sudden-death question if true ties must be broken by play.
		return a;
	}

	private static int roundTotal(GameRoom room, String uuid) {
		int[] round = room.roundOf(uuid);
		return round[0] + round[1];
	}

	private void onTimerExpired(String pin) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null)
			return;
		// Fast guards stay on the timer thread (in-memory only); the blocking
		// DB work in transitionToReview/endGame runs on the sweeper instead so
		// one slow database never stalls other rooms' deadlines.
		String status;
		String mode;
		long end;
		synchronized (room) {
			status = room.getStatus();
			mode = room.getGameMode();
			end = room.getCurrentQuestionEndEpochMs();
		}
		if (!"ACTIVE".equals(status))
			return;
		if (GameRoom.GameMode.PRACTICE.equals(mode))
			return; // no timer in practice
		if (Instant.now().toEpochMilli() < end)
			return; // stale fire after +time
		if (GameRoom.GameMode.EXAM.equals(mode)) {
			// Exam: total time expired, end game immediately.
			sweeper.execute(new Runnable() {
				@Override
				public void run() {
					try {
						endGame(pin);
					} catch (RuntimeException e) {
						log.warn("Async exam expiry failed for room {}", pin, e);
					}
				}
			});
			return;
		}
		sweeper.execute(new Runnable() {
			@Override
			public void run() {
				try {
					transitionToReview(pin);
				} catch (RuntimeException e) {
					log.warn("Async round expiry failed for room {}", pin, e);
				}
			}
		});
	}

	private void transitionToReview(String pin) {
		GameRoom room = require(pin);
		synchronized (room) {
			// Atomic ACTIVE -> REVIEW flip: timer expiry and force-submit race
			// here, and a double transition would double-publish results.
			if (!"ACTIVE".equals(room.getStatus()))
				return;
			room.setStatus("REVIEW");
		}
		roundTimer.cancel(Integer.parseInt(pin));
		sessionRepository.updateStatus(room.getSessionId(), "REVIEW");
		try {
			writeBuffer.flush();
		} catch (RuntimeException e) {
			// Audit flush must never wedge the round: the batch stays queued
			// for the next tick while clients still get their reveal.
			log.warn("Review flush failed for room {}, revealing anyway", pin, e);
		}
		// Exam mode: suppress leaderboard during game (only show at end).
		if (!GameRoom.GameMode.EXAM.equals(room.getGameMode())) {
			flushLeaderboardDelta(pin);
		}
		sendRoundResult(pin, true);
		broadcastRoomState(pin);
		if (GameRoom.GameMode.BATTLE.equals(room.getGameMode())) {
			resolveBattleRound(room);
		}
		eventPublisher.publishEvent(new com.sprintjudge.service.event.GameEvent.QuestionCompleted(pin,
				room.getCurrentQuestionId(), room.getCurrentQuestionIndex()));
		// Auto-pilot / Practice: auto-advance after brief review.
		if (GameRoom.GameMode.AUTO_PILOT.equals(room.getGameMode())
				|| GameRoom.GameMode.PRACTICE.equals(room.getGameMode())) {
			roundTimer.schedule(Integer.parseInt(pin), Instant.now().toEpochMilli()
					+ (GameRoom.GameMode.PRACTICE.equals(room.getGameMode()) ? 2000 : 3000), new Runnable() {
						@Override
						public void run() {
							nextQuestion(pin);
						}
					});
		}
	}

	public void endGame(String pin) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null)
			return;
		synchronized (room) {
			if ("ENDED".equals(room.getStatus()))
				return;
			room.setStatus("ENDED");
		}
		roundTimer.cancel(Integer.parseInt(pin));
		int playerCount = room.getPlayers().size();
		int questionCount = questionRepository.findByQuiz(room.getQuizId()).size();
		sessionRepository.updateStatus(room.getSessionId(), "ENDED");
		try {
			writeBuffer.flush();
		} catch (RuntimeException e) {
			// Audit flush must never skip the reveal or leak the room: the
			// batch stays queued for the next tick.
			log.warn("End-game flush failed for room {}, revealing anyway", pin, e);
		}
		flushLeaderboardDelta(pin);

		// Build review data before removing the room.
		GameReview review = buildReview(room);
		broadcastToRoom(pin, review);

		eventPublisher
				.publishEvent(new com.sprintjudge.service.event.GameEvent.GameEnded(pin, playerCount, questionCount));
		registry.remove(Integer.parseInt(pin));
	}

	/**
	 * Display candidates for the review answer key (option texts, sortable lines);
	 * null when the type has none. Parsed defensively -?a ragged config must never
	 * break the end-of-game review.
	 */
	private static List<String> displayOptions(JsonNode config) {
		if (!config.isObject())
			return null;
		JsonNode options = config.has("options") ? config.get("options") : config.get("lines");
		if (options == null || !options.isArray() || options.isEmpty())
			return null;
		List<String> out = new java.util.ArrayList<>();
		for (JsonNode o : options) {
			if (o.isTextual())
				out.add(o.asText());
			else if (o.has("text"))
				out.add(o.path("text").asText(""));
			else if (o.has("label"))
				out.add(o.path("label").asText(""));
		}
		return out.isEmpty() ? null : List.copyOf(out);
	}

	private GameReview buildReview(GameRoom room) {
		List<Question> questions = questionRepository.findByQuiz(room.getQuizId());
		List<com.sprintjudge.domain.models.Submission> submissions = submissionRepository
				.findBySession(room.getSessionId());

		// Build question reviews.
		java.util.Map<String, List<com.sprintjudge.domain.models.Submission>> byQuestion = new java.util.HashMap<>();
		for (Submission s : submissions) {
			java.util.List<Submission> list = byQuestion.get(s.getQuestionId());
			if (list == null) {
				list = new java.util.ArrayList<>();
				byQuestion.put(s.getQuestionId(), list);
			}
			list.add(s);
		}

		List<GameReview.QuestionReview> qReviews = new java.util.ArrayList<>();
		String hardestId = null, easiestId = null;
		double hardestRate = 1.1, easiestRate = -0.1;

		for (Question q : questions) {
			List<com.sprintjudge.domain.models.Submission> qSubs = byQuestion.getOrDefault(q.getId(), List.of());
			int total = qSubs.size();
			int correct = 0;
			long totalAttempts = 0;
			for (Submission s : qSubs) {
				if (s.isCorrect())
					correct++;
				totalAttempts += s.getAttemptCount();
			}
			double rate = total > 0 ? (double) correct / total : 0;
			double avgAttempts = total > 0 ? (double) totalAttempts / total : 0;

			JsonNode config = q.getConfig() == null
					? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
					: Json.readTree(q.getConfig());
			JsonNode answer = QuestionAnswers.answerPayload(QuestionType.from(q.getQuestionType()), config);

			qReviews.add(
					new GameReview.QuestionReview(q.getId(), q.getTitle(), q.getQuestionType(), q.getTimeLimitSec(),
							q.getPointsBase(), answer, total, correct, rate, avgAttempts, displayOptions(config)));

			if (rate < hardestRate) {
				hardestRate = rate;
				hardestId = q.getId();
			}
			if (rate > easiestRate) {
				easiestRate = rate;
				easiestId = q.getId();
			}
		}

		// Build player reviews.
		java.util.Map<String, List<com.sprintjudge.domain.models.Submission>> byPlayer = new java.util.HashMap<>();
		for (Submission s : submissions) {
			java.util.List<Submission> list2 = byPlayer.get(s.getPlayerUuid());
			if (list2 == null) {
				list2 = new java.util.ArrayList<>();
				byPlayer.put(s.getPlayerUuid(), list2);
			}
			list2.add(s);
		}

		List<GameReview.PlayerReview> pReviews = new java.util.ArrayList<>();
		int totalCorrect = 0, totalAttempts = 0;
		double totalScore = 0;
		int contestants = 0;
		String hostUuid = room.getHostUuid();

		for (Player p : room.getPlayers()) {
			if (p.getUuid().equals(hostUuid) || "Host".equalsIgnoreCase(p.getName()))
				continue;
			contestants++;
			List<com.sprintjudge.domain.models.Submission> pSubs = byPlayer.getOrDefault(p.getUuid(), List.of());
			List<GameReview.PlayerAnswer> answers = new java.util.ArrayList<>();
			int pScore = 0;
			for (Submission s : pSubs) {
				answers.add(new GameReview.PlayerAnswer(s.getQuestionId(), s.isCorrect(), s.getScoreEarned(),
						s.getAttemptCount()));
				if (s.isCorrect())
					totalCorrect++;
				totalAttempts += s.getAttemptCount();
				pScore += s.getScoreEarned();
			}
			pReviews.add(new GameReview.PlayerReview(p.getUuid(), p.getName(), pScore, answers));
			totalScore += pScore;
		}

		GameReview.ClassStats stats = new GameReview.ClassStats(contestants, questions.size(),
				contestants == 0 ? 0 : totalScore / contestants, totalCorrect, totalAttempts, hardestId, easiestId);

		return new GameReview("GAME_REVIEW", room.getLeaderboard(), qReviews, pReviews, stats, room.getSessionId(),
				teamReviews(room));
	}

	private static List<GameReview.TeamReview> teamReviews(GameRoom room) {
		if (!GameRoom.GameMode.TEAM.equals(room.getGameMode()))
			return List.of();
		List<GameReview.TeamReview> out = new java.util.ArrayList<>();
		for (GameRoom.Team t : room.teamsSorted()) {
			out.add(new GameReview.TeamReview(t.getId(), t.getName(), t.getScore(), List.copyOf(t.getMemberUuids())));
		}
		return out;
	}

	/**
	 * Sweeps unrecoverable rooms: lobbies idle past the TTL, plus ACTIVE/REVIEW
	 * rooms with zero connected players past the TTL (abandoned mid-game). Eviction
	 * cancels timers, flushes the audit trail, and ends the session row so neither
	 * pins nor memory leak.
	 */
	public void sweepIdleRooms() {
		long now = System.currentTimeMillis();
		boolean evicted = false;
		for (GameRoom room : registry.snapshot()) {
			String status = room.getStatus();
			boolean eligible = "LOBBY".equals(status) || "ACTIVE".equals(status) || "REVIEW".equals(status);
			if (!eligible || room.idleMs(now) <= LOBBY_TTL_MS)
				continue;
			synchronized (room) {
				if (room.connectedCount() != 0)
					continue;
				int key = Integer.parseInt(room.getPin());
				roundTimer.cancel(key);
				try {
					sessionRepository.updateStatus(room.getSessionId(), "ENDED");
				} catch (RuntimeException e) {
					log.warn("Sweep failed to end session for room {}", room.getPin(), e);
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
	@SuppressWarnings("all")
	void restoreRooms() {
		java.util.List<GameSession> sessions;
		try {
			sessions = sessionRepository.findAllNonEnded();
		} catch (RuntimeException e) {
			log.warn("Room restore failed to load sessions", e);
			return;
		}
		if (sessions == null)
			return;
		for (GameSession s : sessions) {
			if (s == null || s.getPinCode() == null || s.getPinCode().isBlank())
				continue;
			int key;
			try {
				key = Integer.parseInt(s.getPinCode());
			} catch (NumberFormatException e) {
				continue;
			}
			if (registry.get(key) != null)
				continue;
			String mode = (s.getGameMode() == null || s.getGameMode().isBlank()
					|| !GameRoom.GameMode.isValid(s.getGameMode())) ? GameRoom.GameMode.STANDARD : s.getGameMode();
			GameRoom room = new GameRoom(s.getId(), s.getQuizId(), s.getPinCode(),
					s.getStatus() == null ? "LOBBY" : s.getStatus(), maxPlayers, mode);
			room.setCurrentQuestionIndex(s.getCurrentQuestionIndex());
			try {
				java.util.List<Submission> subs = submissionRepository.findBySession(s.getId());
				if (subs != null) {
					java.util.Map<String, String> names = new java.util.HashMap<>();
					java.util.Map<String, Integer> scores = new java.util.HashMap<>();
					for (Submission sub : subs) {
						if (sub == null || sub.getPlayerUuid() == null || sub.getPlayerUuid().isBlank())
							continue;
						names.putIfAbsent(sub.getPlayerUuid(), sub.getPlayerName());
						Integer prev = scores.get(sub.getPlayerUuid());
						scores.put(sub.getPlayerUuid(), (prev == null ? 0 : prev) + sub.getScoreEarned());
					}
					for (java.util.Map.Entry<String, String> e : names.entrySet()) {
						String uuid = e.getKey();
						String name = e.getValue();
						if (name == null || name.isBlank())
							name = "Player";
						int score = scores.getOrDefault(uuid, 0);
						Player p = new Player(uuid, name, 0, null, false, null);
						if (room.addPlayer(p) && score != 0) {
							room.applyScore(uuid, score);
						}
					}
				}
			} catch (RuntimeException e) {
				log.warn("Room {} submission restore failed", s.getPinCode(), e);
			}
			registry.put(key, room);
		}
	}

	@jakarta.annotation.PostConstruct
	void startSweep() {
		restoreRooms();
		sweeper.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				sweepIdleRooms();
			}
		}, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
	}

	@jakarta.annotation.PreDestroy
	void stopSweep() {
		sweeper.shutdownNow();
	}

	// ---------- leaderboard transport ----------

	/**
	 * Score-driven board updates. Host-led live modes (STANDARD/TEAM/BATTLE/
	 * AUTO_PILOT) freeze the public board mid-round -?deltas accumulate in the
	 * ledger and flush at review -?while each player still gets instant per-player
	 * feedback via SUBMISSION_RESULT. PRACTICE/EXAM keep the historical live-board
	 * behavior.
	 */
	private void broadcastScoreChanged(GameRoom room) {
		String mode = room.getGameMode();
		if (GameRoom.GameMode.PRACTICE.equals(mode) || GameRoom.GameMode.EXAM.equals(mode)) {
			broadcastLeaderboard(room.getPin());
		}
	}

	public void broadcastLeaderboard(String pin) {
		int key = Integer.parseInt(pin);
		scheduler.markDirty(key, new Runnable() {
			@Override
			public void run() {
				flushLeaderboardDelta(pin);
			}
		});
	}

	public void flushLeaderboardDelta(String pin) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null)
			return;
		com.sprintjudge.service.leaderboard.DeltaLedger.Batch batch = room.getBoard().drainDeltas(false);
		if (batch.isResync()) {
			// Nothing pending: a client that missed the last batch still has a
			// seq gap, so heal with the authoritative baseline instead of
			// dropping the flush. Empty boards (seq 0) stay silent.
			// (drain guarantees resync batches carry no upserts.)
			if (batch.getSeq() <= 0 || room.getBoard().size() == 0)
				return;
			batch = room.getBoard().fullBatch();
		}
		String json = deltaJson(batch);
		// Non-null by construction: non-resync batches carry upserts, and the
		// healing branch above replaces resync batches with a full snapshot.
		ws.broadcastRaw(playerSessionIds(pin), json);
		// Deltas that arrived mid-flush would otherwise wait for the next
		// score event; re-mark so the next tick drains them.
		if (room.getBoard().pendingDeltaCount() > 0)
			broadcastLeaderboard(pin);
	}

	public void sendFullLeaderboard(String pin, String sessionId) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null)
			return;
		String json = deltaJson(room.getBoard().fullBatch());
		if (json != null)
			ws.sendRaw(sessionId, json);
	}

	private String deltaJson(com.sprintjudge.service.leaderboard.DeltaLedger.Batch b) {
		if (b == null || (b.isResync() && b.getUpserts().isEmpty()))
			return null;
		List<LeaderboardEntry> entries = new java.util.ArrayList<>();
		for (com.sprintjudge.service.leaderboard.DeltaLedger.Delta d : b.getUpserts()) {
			entries.add(new LeaderboardEntry(d.getUuid(), d.getName(), (int) d.getScore(), d.getRank()));
		}
		return Json.write(new LeaderboardDelta("LEADERBOARD_DELTA", b.getSeq(), b.isResync(), entries));
	}

	// ---------- state ----------

	public RoomState getRoomState(String pin) {
		GameRoom room = require(pin);
		List<RoomState.PlayerInfo> players = new java.util.ArrayList<>();
		for (Player p : room.getPlayers()) {
			players.add(new RoomState.PlayerInfo(p.getUuid(), p.getName(), p.getScore(), p.isConnected()));
		}
		int count = questionRepository.findByQuiz(room.getQuizId()).size();
		return new RoomState("ROOM_STATE", room.getStatus(), count, room.getCurrentQuestionIndex(),
				room.getCurrentQuestionId(), players, room.getGameMode());
	}

	private void sendRoundResult(String pin, boolean revealed) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null)
			return;
		int idx = room.getCurrentQuestionIndex();
		List<Question> qs = questionRepository.findByQuiz(room.getQuizId());
		if (idx >= qs.size())
			return;
		Question q = qs.get(idx);
		String type = QuestionType.from(q.getQuestionType());
		JsonNode answer = revealed ? QuestionAnswers.answerPayload(type, Json.readTree(q.getConfig())) : null;
		String hostUuid = room.getHostUuid();
		List<RoundResult.PlayerScore> scores = new java.util.ArrayList<>();
		for (Player pl : room.getPlayers()) {
			if (pl.getUuid().equals(hostUuid))
				continue;
			int[] round = room.roundOf(pl.getUuid());
			boolean correct = room.roundCorrect(pl.getUuid());
			scores.add(
					new RoundResult.PlayerScore(pl.getUuid(), pl.getName(), (int) room.getBoard().scoreOf(pl.getUuid()),
							correct, round[0], room.streakOf(pl.getUuid()), round[1]));
		}
		Map<String, Integer> counts = revealed ? answerCounts(room, q, type, scores) : Map.of();
		broadcastToRoom(pin, new RoundResult("ROUND_RESULT", q.getId(), revealed, answer, scores, counts));
	}

	/**
	 * Option-level distribution for the host reveal. Only the latest answer per
	 * player counts; timed-out players stay in the denominator through the
	 * correct/incorrect fallback so a silent class never reads as 100%.
	 */
	Map<String, Integer> answerCounts(GameRoom room, Question q, String type, List<RoundResult.PlayerScore> scores) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, String> latest = new HashMap<>();
		for (Submission s : submissionRepository.findBySessionQuestion(room.getSessionId(), q.getId())) {
			latest.put(s.getPlayerUuid(), s.getResponseData());
		}
		if (QuestionType.MCQ.equals(type) || QuestionType.OUTPUT_PRED.equals(type)
				|| QuestionType.COMPLEXITY.equals(type)) {
			for (String data : latest.values()) {
				try {
					int idx = Json.readTree(data == null ? "{}" : data).path("selectedIndex").asInt(-1);
					if (idx >= 0 && idx < 26) {
						Integer __p = counts.get(String.valueOf((char) ('A' + idx)));
						counts.put(String.valueOf((char) ('A' + idx)), (__p == null ? 0 : __p) + 1);
					}
				} catch (RuntimeException ignored) {
					// A malformed legacy response is skipped, never a 500.
				}
			}
		} else if (QuestionType.TRUE_FALSE.equals(type)) {
			for (String data : latest.values()) {
				try {
					com.fasterxml.jackson.databind.JsonNode node = Json.readTree(data == null ? "{}" : data);
					if (node.has("value")) {
						Integer __p = counts.get(node.path("value").asBoolean() ? "True" : "False");
						counts.put(node.path("value").asBoolean() ? "True" : "False", (__p == null ? 0 : __p) + 1);
					}
				} catch (RuntimeException ignored) {
					// skip malformed legacy response
				}
			}
		} else if (QuestionType.MULTIPLE_SELECT.equals(type)) {
			for (String data : latest.values()) {
				try {
					com.fasterxml.jackson.databind.JsonNode node = Json.readTree(data == null ? "{}" : data)
							.path("selectedIndices");
					if (node.isArray()) {
						for (com.fasterxml.jackson.databind.JsonNode selected : node) {
							int idx = selected.asInt(-1);
							if (idx >= 0 && idx < 26) {
								Integer __p = counts.get(String.valueOf((char) ('A' + idx)));
								counts.put(String.valueOf((char) ('A' + idx)), (__p == null ? 0 : __p) + 1);
							}
						}
					}
				} catch (RuntimeException ignored) {
					// skip malformed legacy response
				}
			}
		}
		if (counts.isEmpty()) {
			int correct = 0;
			for (RoundResult.PlayerScore ps : scores)
				if (ps.isCorrect())
					correct++;
			counts.put("Correct", correct);
			counts.put("Incorrect", Math.max(0, scores.size() - correct));
		}
		return counts;
	}

	private String sessionIdOf(GameRoom room, String uuid) {
		if (uuid == null)
			return null;
		Player p = room.getPlayer(uuid);
		return p == null ? null : p.getSessionId();
	}

	private void broadcastRoomState(String pin) {
		ws.broadcast(playerSessionIds(pin), getRoomState(pin));
	}

	/**
	 * Catch-up for joins into a live round (refresh mid-round, second device): the
	 * current QUESTION_START, so the newcomer is not stranded on standby with no
	 * re-send coming. Silent when nothing is live.
	 */
	public void sendCurrentQuestion(String pin, String sessionId) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null || !"ACTIVE".equals(room.getStatus()))
			return;
		List<Question> questions = questionRepository.findByQuiz(room.getQuizId());
		if (questions.isEmpty() || room.getCurrentQuestionIndex() >= questions.size())
			return;
		Question q = questions.get(room.getCurrentQuestionIndex());
		long now = Instant.now().toEpochMilli();
		// Reuse the live round's clock (untimed marker for practice) so the
		// late joiner sees exactly what everyone else sees.
		int timeLimitSec = GameRoom.GameMode.PRACTICE.equals(room.getGameMode())
				? -1
				: (int) Math.max(0, (room.getCurrentQuestionEndEpochMs() - now) / 1000);
		ws.send(sessionId, new QuestionStart("QUESTION_START", toDto(q), timeLimitSec,
				room.getCurrentQuestionStartEpochMs(), now));
	}

	private void broadcastToRoom(String pin, Object message) {
		ws.broadcast(playerSessionIds(pin), message);
	}

	private List<String> playerSessionIds(String pin) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null)
			return List.of();
		List<String> ids = new java.util.ArrayList<>();
		for (Player pl : room.getPlayers()) {
			if (pl.getSessionId() != null)
				ids.add(pl.getSessionId());
		}
		for (Player sp : room.getSpectators()) {
			if (sp.getSessionId() != null)
				ids.add(sp.getSessionId());
		}
		return ids;
	}

	private GameRoom require(String pin) {
		GameRoom room = registry.get(Integer.parseInt(pin));
		if (room == null)
			throw new IllegalArgumentException("No active room for PIN " + pin);
		return room;
	}

	private QuestionDto toDto(Question q) {
		Object config = q.getConfig() == null
				? Map.of()
				: QuestionAnswers.sanitize(QuestionType.from(q.getQuestionType()), Json.readTree(q.getConfig()));
		return new QuestionDto(q.getId(), q.getQuestionType(), q.getTitle(), q.getDescription(), q.getTimeLimitSec(),
				q.getPointsBase(), q.getLanguagesAllowed(), config);
	}
}
