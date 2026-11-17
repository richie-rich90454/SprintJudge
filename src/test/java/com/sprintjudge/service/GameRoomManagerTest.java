package com.sprintjudge.service;

import com.sprintjudge.domain.dto.ErrorMessage;
import com.sprintjudge.domain.dto.GameReview;
import com.sprintjudge.domain.dto.QuestionStart;

import com.sprintjudge.domain.dto.RoundResult;
import com.sprintjudge.domain.dto.SubmissionResult;
import com.sprintjudge.domain.dto.TimerUpdate;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.util.Json;
import com.sprintjudge.service.room.RoomRegistry;
import com.sprintjudge.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRoomManagerTest {

	@Mock
	QuizRepository quizRepository;
	@Mock
	GameSessionRepository sessionRepository;
	@Mock
	QuestionRepository questionRepository;
	@Mock
	SubmissionRepository submissionRepository;
	@Mock
	ScoringEngine scoringEngine;
	@Mock
	SubmissionProcessor submissionProcessor;
	private final org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> processorProvider = new org.springframework.beans.factory.ObjectProvider<>() {
		@Override
		public SubmissionProcessor getObject() {
			return submissionProcessor;
		}
	};
	@Mock
	WebSocketSessionManager ws;
	@Mock
	EvaluationService evaluationService;
	@Mock
	AdminSettingsService settingsService;
	@Mock
	BroadcastScheduler scheduler;
	@Mock
	SubmissionWriteBuffer writeBuffer;
	@Mock
	RoundTimeoutScheduler roundTimer;
	@Mock
	org.springframework.context.ApplicationEventPublisher eventPublisher;
	@Captor
	ArgumentCaptor<java.util.Collection<String>> ids;
	@Captor
	ArgumentCaptor<Runnable> runnableCaptor;

	private GameRoomManager manager() {
		return new GameRoomManager(sessionRepository, quizRepository, questionRepository, submissionRepository,
				scoringEngine, processorProvider, ws, evaluationService, settingsService, scheduler, writeBuffer,
				roundTimer, eventPublisher);
	}

	private GameSession session(String pin) {
		return new GameSession("s1", "qz", pin, "host", "LOBBY", "STANDARD", 0, null, null, null, Instant.now());
	}

	private GameSession practiceSession(String pin) {
		return new GameSession("s1", "qz", pin, "host", "LOBBY", "PRACTICE", 0, null, null, null, Instant.now());
	}

	private GameRoom liveRoom(GameRoomManager mgr, String pin) {
		try {
			java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("registry");
			f.setAccessible(true);
			RoomRegistry reg = (RoomRegistry) f.get(mgr);
			return reg.get(Integer.parseInt(pin));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Question mcq(String id) {
		return new Question(id, "qz", "T", "D", "MCQ", null, 30, 100, Json.write(Map.of("correctIndex", 0)), 0,
				Instant.now());
	}

	/**
	 * Lifecycle tests need a live room in the manager's registry before driving
	 * rounds.
	 */
	private void seedRoom(GameRoomManager mgr) {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		lenient().when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
		mgr.join("123456", "Seed", "seed-sess", "player", null);
	}

	/**
	 * Puts the room into an ACTIVE round for the given question so submit() clears
	 * its lock gate.
	 */
	private void armRound(GameRoomManager mgr, String questionId) {
		try {
			java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("registry");
			f.setAccessible(true);
			RoomRegistry reg = (RoomRegistry) f.get(mgr);
			GameRoom room = reg.get(Integer.parseInt("123456"));
			room.setStatus("ACTIVE");
			room.setCurrentQuestionId(questionId);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ---------- join ----------

	@Test
	void joinRejectsInvalidPin() {
		when(sessionRepository.findByPin("000000")).thenReturn(Optional.empty());
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				manager().join("000000", "Alice", "sess-1", "player", null);
			}
		});
		verify(ws, never()).broadcast(any(), any());
	}

	@Test
	void joinSanitizesHostileNicknames() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Player p = manager().join("123456", "<script>x</script>", "sess", "player", null);
		assertTrue(p.getName().matches("[A-Za-z0-9 _\\-]*"));
	}

	@Test
	void practiceFirstJoinAutoStartsFirstQuestion() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(practiceSession("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Solo", "sess-1", "player", null);
		assertEquals("ACTIVE", liveRoom(mgr, "123456").getStatus());
		assertEquals("q1", liveRoom(mgr, "123456").getCurrentQuestionId());
		// The client renders off the broadcast payload: prove Q1 (untimed
		// marker -1) actually goes out on join, not just internal state.
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		QuestionStart start = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof QuestionStart) {
				start = (QuestionStart) m;
				break;
			}
		}
		if (start == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals("q1", start.getQuestion().getId());
		assertEquals(-1, start.getTimeLimitSec());
	}

	@Test
	void practiceLaterJoinDoesNotRestartRound() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(practiceSession("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Solo", "sess-1", "player", null);
		mgr.join("123456", "Late", "sess-2", "player", null);
		assertEquals("ACTIVE", liveRoom(mgr, "123456").getStatus());
		assertEquals("q1", liveRoom(mgr, "123456").getCurrentQuestionId());
		assertEquals(0, liveRoom(mgr, "123456").getCurrentQuestionIndex());
	}

	private Player joinPracticeSolo(GameRoomManager mgr, String name, String sess) {
		// NOTE: no sessionRepository stub - createRoomWithMode seeds the
		// registry directly, so join never hits the session lookup.
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		lenient().when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		lenient().when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		lenient().when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any()))
				.thenReturn(900);
		return mgr.join("123456", name, sess, "player", null);
	}

	@Test
	void practiceSubmitClosesRoundAndSchedulesAdvance() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		Player p = joinPracticeSolo(mgr, "Solo", "sess-1");
		assertEquals("ACTIVE", liveRoom(mgr, "123456").getStatus());

		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		assertEquals("REVIEW", liveRoom(mgr, "123456").getStatus());
		verify(roundTimer).schedule(eq(123456), anyLong(), any());
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("sess-1"), sent.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : sent.getAllValues()) {
				if (m instanceof SubmissionResult) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void practiceSecondSubmitIsLockedAfterAdvance() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		Player p = joinPracticeSolo(mgr, "Solo", "sess-1");
		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).send(eq("sess-1"), sent.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : sent.getAllValues()) {
				if (m instanceof com.sprintjudge.domain.dto.ErrorMessage) {
					com.sprintjudge.domain.dto.ErrorMessage e = (com.sprintjudge.domain.dto.ErrorMessage) m;
					if (e.getMessage().contains("locked")) {
						streamMatchResult = true;
						break;
					}
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void standardSubmitDoesNotAdvance() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);
		Player p = mgr.join("123456", "Solo", "sess-1", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals("ACTIVE", liveRoom(mgr, "123456").getStatus());
		// Only startQuestion's own per-question timer: no review transition.
		verify(roundTimer, times(1)).schedule(anyInt(), anyLong(), any());
	}

	@Test
	void practiceCodingSubmitAdvancesOnAccept() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(oj));
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		Player p = mgr.join("123456", "Cody", "sess-c", "player", null);
		assertEquals("ACTIVE", liveRoom(mgr, "123456").getStatus());
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));

		mgr.submit("123456", "oj1", p.getUuid(), "python",
				Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));
		ArgumentCaptor<com.sprintjudge.service.CodingOutcomeConsumer> cap = ArgumentCaptor
				.forClass(com.sprintjudge.service.CodingOutcomeConsumer.class);
		verify(submissionProcessor).processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), cap.capture());

		cap.getValue().accept(p.getUuid(), 100, true, 1, 1, null);
		assertEquals("REVIEW", liveRoom(mgr, "123456").getStatus());
		verify(roundTimer).schedule(eq(123456), anyLong(), any());
	}

	@Test
	void practiceCodingBusyRejectKeepsRoundOpen() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(oj));
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		Player p = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(false));

		mgr.submit("123456", "oj1", p.getUuid(), "python",
				Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));

		assertEquals("ACTIVE", liveRoom(mgr, "123456").getStatus());
		verify(roundTimer, never()).schedule(anyInt(), anyLong(), any());
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).send(eq("sess-c"), sent.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : sent.getAllValues()) {
				if (m instanceof com.sprintjudge.domain.dto.ErrorMessage) {
					com.sprintjudge.domain.dto.ErrorMessage e = (com.sprintjudge.domain.dto.ErrorMessage) m;
					if (e.getMessage().contains("busy")) {
						streamMatchResult = true;
						break;
					}
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void catchUpSendsLiveQuestionToLateJoiner() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");

		mgr.sendCurrentQuestion("123456", "late-sess");

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("late-sess"), sent.capture());
		assertTrue(sent.getValue() instanceof QuestionStart
				&& ((QuestionStart) sent.getValue()).getQuestion().getId().equals("q1"));
	}

	@Test
	void catchUpSilentWhenNothingLive() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);

		mgr.sendCurrentQuestion("123456", "late-sess");
		mgr.sendCurrentQuestion("999999", "late-sess");

		verify(ws, never()).send(eq("late-sess"), any());
	}

	@Test
	void catchUpSilentPastLastQuestion() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		liveRoom(mgr, "123456").setCurrentQuestionIndex(7);

		mgr.sendCurrentQuestion("123456", "late-sess");

		verify(ws, never()).send(eq("late-sess"), any());
	}

	@Test
	void catchUpSilentWhenBankEmptiedMidRound() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
		liveRoom(mgr, "123456").setStatus("ACTIVE");

		mgr.sendCurrentQuestion("123456", "late-sess");

		verify(ws, never()).send(eq("late-sess"), any());
	}

	@Test
	void catchUpPracticeIsUntimed() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		joinPracticeSolo(mgr, "Solo", "sess-1");

		mgr.sendCurrentQuestion("123456", "late-sess");

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("late-sess"), sent.capture());
		assertTrue(
				sent.getValue() instanceof QuestionStart && ((QuestionStart) sent.getValue()).getTimeLimitSec() == -1);
	}

	@Test
	void joinRecreatesRoomWhenEvictedFromRegistry() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player p = mgr.join("123456", "Alice", "sess-1", "player", null);
		assertNotNull(p);
		assertEquals(1, mgr.activeRooms());
	}

	@Test
	void joinToEndedGameThrows() {
		GameSession ended = new GameSession("s1", "qz", "123456", "host", "ENDED", "STANDARD", 0, null, null, null,
				Instant.now());
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(ended));
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				manager().join("123456", "Alice", "sess-1", "player", null);
			}
		});
	}

	@Test
	void joinWithOnlyInvalidCharsFallsBackToPlayer() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = manager().join("123456", "###", "sess-1", "player", null);
		assertEquals("Player", p.getName());
	}

	@Test
	void secondHostIsRejected() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Host", "sess-h", "host", null);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.join("123456", "Impostor", "sess-i", "host", null);
			}
		});
	}

	/** Regression: a join must arm the coalescer so its score-0 delta ships. */
	@Test
	void joinMarksLeaderboardDirty() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		manager().join("123456", "Alice", "sess-a", "player", null);
		verify(scheduler).markDirty(eq(123456), any());
	}

	/**
	 * Regression: the host holds a roster seat but never enters the scored board.
	 */
	@Test
	void hostJoinExcludedFromLeaderboard() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sess-h", "host", null);
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-a", "player", null);

		java.util.List<com.sprintjudge.domain.dto.LeaderboardEntry> lb = roomOf(mgr).getLeaderboard();
		assertEquals(1, lb.size());
		assertEquals(player.getUuid(), lb.get(0).getUuid());
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.service.Player p : roomOf(mgr).getPlayers()) {
				if (p.getUuid().equals(host.getUuid())) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void roomRejectsPlayersBeyondCapacity() throws Exception {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("maxPlayers");
		f.setAccessible(true);
		f.setInt(mgr, 3);
		for (int i = 0; i < 3; i++) {
			mgr.join("123456", "P" + i, "s" + i, "player", null);
		}
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.join("123456", "Extra", "s4", "player", null);
			}
		});
	}

	// ---------- selection submit ----------

	@Test
	void submitScoresSelectionAndSaves() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);

		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved = ArgumentCaptor
				.forClass(com.sprintjudge.domain.models.Submission.class);
		verify(writeBuffer).offer(saved.capture());
		assertEquals(900, saved.getValue().getScoreEarned());
		assertTrue(saved.getValue().isCorrect());

		// submit does not broadcast ROOM_STATE immediately; the leaderboard delta is
		// coalesced.
		verify(ws, never()).broadcast(any(), any());
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		// STANDARD delays the public board: only the join armed a flush. The
		// score mutation still sits in the ledger, so draining the join task
		// ships it with the review-time snapshot.
		verify(scheduler, times(1)).markDirty(eq(123456), task.capture()); // join only
		task.getAllValues().get(0).run();

		ArgumentCaptor<java.util.Collection<String>> ids = this.ids;
		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(ws).broadcastRaw(ids.capture(), payload.capture());
		// join(seq=1) + score mutation(seq=2) -> the drained delta carries seq 2.
		assertTrue(payload.getValue().contains("LEADERBOARD_DELTA"));
		assertTrue(payload.getValue().contains("\"seq\":2"));
		assertTrue(payload.getValue().contains("\"score\":900"));
		assertTrue(ids.getValue().contains("sess-1"));
	}

	@Test
	void wrongSelectionSavesZeroWithoutBoardFlush() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);

		com.sprintjudge.service.Player player = mgr.join("123456", "Bob", "sess-b", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":3}"));

		ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved = ArgumentCaptor
				.forClass(com.sprintjudge.domain.models.Submission.class);
		verify(writeBuffer).offer(saved.capture());
		assertEquals(0, saved.getValue().getScoreEarned());
		assertEquals(false, saved.getValue().isCorrect());
		// Fraction flows into the engine, which hard-zeroes it internally.
		verify(scoringEngine).scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any());
		// STANDARD delays the public board mid-round: only the join armed a flush.
		verify(scheduler, times(1)).markDirty(eq(123456), any());
	}

	@Test
	void partialCreditScalesTheRawSpeedScore() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		String multi = Json.write(Map.of("correctIndices", List.of(0, 1)));
		when(questionRepository.findById("m1")).thenReturn(Optional
				.of(new Question("m1", "qz", "M", "D", "MULTIPLE_SELECT", null, 30, 100, multi, 0, Instant.now())));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.75);

		when(scoringEngine.scoreSelection(eq(0.75), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(800);

		com.sprintjudge.service.Player player = mgr.join("123456", "Cara", "sess-p", "player", null);
		armRound(mgr, "m1");
		mgr.submit("123456", "m1", player.getUuid(), "python", Json.readTree("{\"selectedIndices\":[0]}"));

		ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved = ArgumentCaptor
				.forClass(com.sprintjudge.domain.models.Submission.class);
		verify(writeBuffer).offer(saved.capture());
		assertEquals(800, saved.getValue().getScoreEarned());
		assertEquals(false, saved.getValue().isCorrect());
	}

	@Test
	void codingSubmitsRouteToProcessorNotSelectionPath() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
		armRound(mgr, "oj1");

		mgr.submit("123456", "oj1", player.getUuid(), "python",
				Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));

		verify(submissionProcessor).processCoding(eq("s1"), eq("123456"), eq("oj1"), eq("Cody"), eq(player.getUuid()),
				eq("python"), eq("print(1)"), anyInt(), any(), anyLong(), any());
		verify(writeBuffer, never()).offer(any());
		verify(ws, never()).send(anyString(), any());
	}

	@Test
	void saturatedJudgeAnswersWithFriendlyRetry() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_PATCH", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Pat", "sess-j", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(false));
		armRound(mgr, "oj1");

		mgr.submit("123456", "oj1", player.getUuid(), "python",
				Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq(player.getSessionId()), sent.capture());
		assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).getMessage()
				.contains("Judge queue is busy"));
	}

	// ---------- question lifecycle ----------

	/**
	 * Regression: the first "Start round" must begin at question 0, not skip it.
	 */
	@Test
	void nextQuestionFromLobbyStartsFirstQuestion() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));

		mgr.nextQuestion("123456");

		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		QuestionStart start = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof QuestionStart) {
				start = (QuestionStart) m;
				break;
			}
		}
		if (start == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals("q1", start.getQuestion().getId());
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus());
		verify(sessionRepository).updateStatus("s1", "ACTIVE");
	}

	@Test
	void startQuestionArmsTimerAndBroadcastsQuestionStart() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));

		mgr.startQuestion("123456");

		verify(sessionRepository).updateStatus("s1", "ACTIVE");
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : msg.getAllValues()) {
				if (m instanceof QuestionStart) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void startQuestionWithoutQuizQuestionsFailsCleanly() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.startQuestion("123456");
			}
		});
	}

	@Test
	void nextQuestionPastTheLastEndsGameAndEvictsRoom() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));

		mgr.startQuestion("123456");
		mgr.nextQuestion("123456");

		verify(sessionRepository).updateStatus("s1", "ENDED");
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.getRoomState("123456");
			}
		});
	}

	@Test
	void extendTimerBroadcastsUpdatedDeadline() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");

		mgr.extendTimer("123456", 30);

		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		TimerUpdate update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof TimerUpdate) {
				update = (TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(30, update.getExtendSec());
		assertTrue(update.getNewEndEpochMs() > System.currentTimeMillis());
	}

	@Test
	void forceSubmitFlushesBufferThenRevealsRoundResult() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(ws);

		mgr.forceSubmit("123456");

		verify(writeBuffer).flush(); // accuracy boundary
		verify(sessionRepository).updateStatus("s1", "REVIEW");
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : msg.getAllValues()) {
				if (m instanceof RoundResult && "ROUND_RESULT".equals(((RoundResult) m).getType())) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void kickRemovesPlayerAndNotifiesTheirSession() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = mgr.join("123456", "Bad", "sess-k", "player", null);
		org.mockito.Mockito.clearInvocations(ws);

		mgr.kickPlayer("123456", p.getUuid());

		verify(ws).send(eq("sess-k"), any());
		assertEquals(0, mgr.getRoomState("123456").getPlayers().size());
	}

	@Test
	void leaveDropsDisconnectedPlayer() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = mgr.join("123456", "Ghost", "sess-g", "player", null);
		org.mockito.Mockito.clearInvocations(ws);

		mgr.leave("123456", p.getUuid());

		com.sprintjudge.domain.dto.RoomState state = mgr.getRoomState("123456");
		assertEquals(1, state.getPlayers().size());
		{
			boolean streamNoneResult = true;
			for (com.sprintjudge.domain.dto.RoomState.PlayerInfo pi : state.getPlayers()) {
				if (pi.isConnected()) {
					streamNoneResult = false;
					break;
				}
			}
			assertTrue(streamNoneResult);
		}
	}

	// ---------- creation ----------

	@Test
	void createRoomGeneratesSixDigitPinAndPersistsLobby() {
		when(quizRepository.findById("qz")).thenReturn(
				Optional.of(new com.sprintjudge.domain.models.Quiz("qz", "T", "", null, Instant.now(), false)));
		lenient().when(sessionRepository.findByPin(anyString())).thenReturn(Optional.empty());
		when(sessionRepository.create(eq("qz"), eq("host-1"), anyString(), eq(null), anyString()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						return new GameSession("gen", "qz", inv.getArgument(2), inv.getArgument(1), "LOBBY", "STANDARD",
								0, null, null, null, Instant.now());
					}
				});

		GameSession created = manager().createRoom("qz", "host-1");

		assertEquals("LOBBY", created.getStatus());
		assertTrue(created.getPinCode().matches("\\d{6}"));
	}

	@Test
	void createRoomReclaimsEndedPin() {
		when(quizRepository.findById("qz")).thenReturn(
				Optional.of(new com.sprintjudge.domain.models.Quiz("qz", "T", "", null, Instant.now(), false)));
		GameSession ended = new GameSession("old", "qz", "123456", "host", "ENDED", "STANDARD", 0, null, null, null,
				Instant.now());
		when(sessionRepository.findByPin(anyString())).thenReturn(Optional.of(ended)).thenReturn(Optional.empty());
		when(sessionRepository.create(eq("qz"), eq("host-1"), anyString(), eq(null), anyString()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						return new GameSession("gen", "qz", inv.getArgument(2), inv.getArgument(1), "LOBBY", "STANDARD",
								0, null, null, null, Instant.now());
					}
				});

		GameSession created = manager().createRoom("qz", "host-1");

		assertTrue(created.getPinCode().matches("\\d{6}"));
		verify(sessionRepository).deleteByPin(anyString());
	}

	// ---------- state shape ----------

	@Test
	void getRoomStateExposesStatusCountAndPlayers() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("a"), mcq("b")));
		mgr.join("123456", "A", "sa", "player", null);
		mgr.join("123456", "B", "sb", "player", null);

		com.sprintjudge.domain.dto.RoomState state = mgr.getRoomState("123456");

		assertEquals("ROOM_STATE", state.getType());
		assertEquals("LOBBY", state.getStatus());
		assertEquals(2, state.getQuestionCount());
		assertEquals(2, state.getPlayers().size());
	}

	@Test
	void submitForUnknownPlayerIsIgnored() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));

		mgr.submit("123456", "q1", "ghost-uuid", "python", Json.readTree("{\"selectedIndex\":0}"));

		verify(writeBuffer, never()).offer(any());
	}

	@Test
	void resyncSendsAuthoritativeSnapshotToRequester() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = mgr.join("123456", "Sync", "sess-r", "player", null);
		org.mockito.Mockito.clearInvocations(ws);

		mgr.sendFullLeaderboard("123456", p.getSessionId());

		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(ws).sendRaw(eq("sess-r"), payload.capture());
		assertTrue(payload.getValue().contains("LEADERBOARD_DELTA"));
		assertTrue(payload.getValue().contains("\"resync\":true"));
		assertTrue(payload.getValue().contains("Sync"));
	}

	// ---------- helpers for the new coverage tests ----------

	private RoomRegistry registryOf(GameRoomManager mgr) {
		try {
			java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("registry");
			f.setAccessible(true);
			return (RoomRegistry) f.get(mgr);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private GameRoom roomOf(GameRoomManager mgr) {
		return registryOf(mgr).get(123456);
	}

	private void setField(Object obj, String name, Object value) {
		try {
			java.lang.reflect.Field f = obj.getClass().getDeclaredField(name);
			f.setAccessible(true);
			f.set(obj, value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void createRoomWithMode(GameRoomManager mgr, String mode) {
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, mode));
	}

	// ---------- join: room-recovery paths ----------

	@Test
	void joinRejectsEndedRoom() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(new GameSession("s1", "qz", "123456", "host",
				"ENDED", "STANDARD", 0, null, null, null, Instant.now())));
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				manager().join("123456", "Alice", "sess", "player", null);
			}
		});
	}

	@Test
	void joinCreatesRoomWhenAbsentButSessionExists() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		Player p = mgr.join("123456", "Alice", "sess-a", "player", null);
		assertNotNull(p);
		assertDoesNotThrow(new org.junit.jupiter.api.function.ThrowingSupplier<com.sprintjudge.domain.dto.RoomState>() {
			@Override
			public com.sprintjudge.domain.dto.RoomState get() throws java.lang.Throwable {
				return mgr.getRoomState("123456");
			}
		});
	}

	@Test
	void joinReclaimsDisconnectedSeatByToken() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player p = mgr.join("123456", "Alice", "sess-a", "player", null);
		roomOf(mgr).softRemove(p.getUuid()); // simulate disconnect
		com.sprintjudge.service.Player reclaimed = mgr.join("123456", "Alice", "sess-b", "player", p.getToken());
		assertEquals(p.getUuid(), reclaimed.getUuid());
	}

	@Test
	void joinWithBadTokenFallsThroughToFreshSeat() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sess-a", "player", null);
		Player p2 = mgr.join("123456", "Bob", "sess-b", "player", "not-a-real-token");
		assertNotNull(p2);
	}

	@Test
	void joinEmptySanitizedNameFallsBackToPlayer() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Player p = manager().join("123456", "###", "sess", "player", null);
		assertEquals("Player", p.getName());
	}

	// ---------- submit: lock / missing / limit branches ----------

	@Test
	void submitLockedRoundSendsErrorAndStops() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		// status is LOBBY (round not armed) -> lock gate fires
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(anyString(), sent.capture());
		assertTrue(
				((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).getMessage().contains("Round is locked"));
	}

	@Test
	void submitMissingQuestionIsIgnored() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");
		// questionRepository.findById("q1") defaults to empty -> q == null -> error to
		// sender
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(writeBuffer, never()).offer(any());
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(anyString(), sent.capture());
		assertTrue(
				((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).getMessage().contains("Unknown question"));
	}

	@Test
	void hostSubmitRejectedWithoutScoring() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sess-h", "host", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", host.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(writeBuffer, never()).offer(any());
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("sess-h"), sent.capture());
		assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).getMessage()
				.contains("Hosts can't submit"));
	}

	@Test
	void submitFromDepartedPlayerBurnsNoAttempt() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");
		mgr.leave("123456", player.getUuid());
		org.mockito.Mockito.clearInvocations(ws);
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(writeBuffer, never()).offer(any());
		assertEquals(0, roomOf(mgr).attemptCount("q1", player.getUuid()));
	}

	@Test
	void submitAttemptLimitSendsError() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");
		// Pre-fill the per-(question,player) attempt counter to the cap.
		try {
			java.lang.reflect.Field af = GameRoom.class.getDeclaredField("attempts");
			af.setAccessible(true);
			@SuppressWarnings("unchecked")
			java.util.concurrent.ConcurrentHashMap<String, Integer> map = (java.util.concurrent.ConcurrentHashMap<String, Integer>) af
					.get(roomOf(mgr));
			map.put("q1 " + player.getUuid(), 50);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(anyString(), sent.capture());
		assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).getMessage()
				.contains("Attempt limit reached"));
	}

	@Test
	void twoConsecutiveCorrectAnswersTriggerStreakBonus() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved = ArgumentCaptor
				.forClass(com.sprintjudge.domain.models.Submission.class);
		verify(writeBuffer, org.mockito.Mockito.times(2)).offer(saved.capture());
		assertEquals(900, saved.getAllValues().get(0).getScoreEarned()); // no streak yet
		assertEquals(990, saved.getAllValues().get(1).getScoreEarned()); // +10% streak bonus
	}

	@Test
	void standardSelectionSubmitSendsInstantFeedbackAndDelaysBoard() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq(player.getSessionId()), sent.capture());
		assertTrue(sent.getValue() instanceof SubmissionResult && ((SubmissionResult) sent.getValue()).getScore() == 900
				&& ((SubmissionResult) sent.getValue()).isAllPassed());
		// Host-led STANDARD: the public board stays frozen (join armed the only flush).
		verify(scheduler, times(1)).markDirty(eq(123456), any());
	}

	@Test
	void practiceSelectionSubmitKeepsLiveBoard() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq(player.getSessionId()), sent.capture());
		assertTrue(sent.getValue() instanceof SubmissionResult);
		// PRACTICE keeps the historical live board: join + submit both arm a flush.
		verify(scheduler, times(2)).markDirty(eq(123456), any());
	}

	// ---------- submit: coding language gating ----------

	@Test
	void codingRejectsDisallowedLanguage() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", java.util.List.of("java"), 60, 500, "{}", 0,
				Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"print(1)\"}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq(player.getSessionId()), sent.capture());
		assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).getMessage()
				.contains("Language not allowed"));
		verify(submissionProcessor, never()).processCoding(anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any());
	}

	@Test
	void codingAllowsMatchingLanguage() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", java.util.List.of("python"), 60, 500, "{}", 0,
				Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"print(1)\"}"));
		verify(submissionProcessor).processCoding(eq("s1"), eq("123456"), eq("oj1"), eq("Cody"), eq(player.getUuid()),
				eq("python"), eq("print(1)"), anyInt(), any(), anyLong(), any());
	}

	@Test
	void codingWithNullResponseUsesEmptySource() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", null);
		verify(submissionProcessor).processCoding(eq("s1"), eq("123456"), eq("oj1"), eq("Cody"), eq(player.getUuid()),
				eq("python"), eq(""), anyInt(), any(), anyLong(), any());
	}

	// ---------- host / leave / kick branches ----------

	@Test
	void leaveHostClearsHostUuid() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sess-h", "host", null);
		mgr.leave("123456", host.getUuid());
		assertNull(roomOf(mgr).getHostUuid());
	}

	@Test
	void kickHostAlsoClearsHostUuid() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sess-h", "host", null);
		mgr.kickPlayer("123456", host.getUuid());
		assertNull(roomOf(mgr).getHostUuid());
	}

	@Test
	void kickUnknownPlayerIsNoOp() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		mgr.kickPlayer("123456", "ghost-uuid");
		verify(ws, never()).send(eq("ghost-uuid"), any());
	}

	// ---------- extendTimer non-active branch ----------

	@Test
	void extendTimerIgnoredWhenNotActive() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		mgr.extendTimer("123456", 30);
		verify(roundTimer, never()).schedule(anyInt(), anyLong(), any());
	}

	// ---------- endGame / sweep / null-room branches ----------

	@Test
	void endGameOnNonexistentRoomIsNoOp() {
		GameRoomManager mgr = manager();
		mgr.endGame("000000");
		verify(ws, never()).broadcast(any(), any());
		verify(sessionRepository, never()).updateStatus(anyString(), anyString());
	}

	@Test
	void sweepRemovesIdleLobbyRoom() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player p = mgr.join("123456", "A", "sa", "player", null);
		roomOf(mgr).softRemove(p.getUuid()); // disconnect -> connectedCount 0
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		assertEquals(1, mgr.activeRooms());
		mgr.sweepIdleRooms();
		assertEquals(0, mgr.activeRooms());
	}

	@Test
	void sweepKeepsActiveRooms() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456"); // ACTIVE, not idle-eligible
		mgr.sweepIdleRooms();
		assertDoesNotThrow(new org.junit.jupiter.api.function.ThrowingSupplier<com.sprintjudge.domain.dto.RoomState>() {
			@Override
			public com.sprintjudge.domain.dto.RoomState get() throws java.lang.Throwable {
				return mgr.getRoomState("123456");
			}
		});
	}

	@Test
	void broadcastLeaderboardForMissingRoomReturnsWithoutSend() {
		GameRoomManager mgr = manager();
		mgr.broadcastLeaderboard("000000"); // key 0, no room
		verify(scheduler).markDirty(eq(0), runnableCaptor.capture());
		runnableCaptor.getValue().run(); // flushLeaderboardDelta(0) -> room null
		verify(ws, never()).broadcastRaw(any(), any());
	}

	@Test
	void sendFullLeaderboardEmptyBoardSendsNoResync() {
		when(quizRepository.findById("qz"))
				.thenReturn(Optional.of(new Quiz("qz", "T", "", null, Instant.now(), false)));
		lenient().when(sessionRepository.findByPin(anyString())).thenReturn(Optional.empty());
		when(sessionRepository.create(eq("qz"), eq("host"), anyString(), eq(null), anyString()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						return new GameSession("gen", "qz", inv.getArgument(2), "host", "LOBBY", "STANDARD", 0, null,
								null, null, Instant.now());
					}
				});
		GameRoomManager mgr = manager();
		GameSession created = mgr.createRoom("qz", "host");
		mgr.sendFullLeaderboard(created.getPinCode(), "sess");
		verify(ws, never()).sendRaw(anyString(), anyString());
	}

	// ---------- onTimerExpired (driven through the scheduled Runnable) ----------

	@Test
	void onTimerExpiredDoesNothingWhileClockIsInTheFuture() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		verify(roundTimer).schedule(eq(123456), anyLong(), runnableCaptor.capture());
		runnableCaptor.getValue().run(); // now < end -> returns early
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus());
	}

	@Test
	void onTimerExpiredNoOpWhenRoomNotActive() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		verify(roundTimer).schedule(eq(123456), anyLong(), runnableCaptor.capture());
		setField(roomOf(mgr), "status", "REVIEW");
		runnableCaptor.getValue().run(); // status != ACTIVE -> returns
		assertEquals("REVIEW", mgr.getRoomState("123456").getStatus());
	}

	@Test
	void onTimerExpiredTransitionsToReviewWhenGenuinelyExpired() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		verify(roundTimer).schedule(eq(123456), anyLong(), runnableCaptor.capture());
		setField(roomOf(mgr), "currentQuestionEndEpochMs", System.currentTimeMillis() - 10_000);
		runnableCaptor.getValue().run(); // now >= end -> async transition
		// Timer expiry hands blocking DB work to the sweeper; await the transition.
		long deadline = System.currentTimeMillis() + 5000;
		while (!"REVIEW".equals(roomOf(mgr).getStatus()) && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		assertEquals("REVIEW", mgr.getRoomState("123456").getStatus());
		verify(sessionRepository, org.mockito.Mockito.timeout(5000)).updateStatus("s1", "REVIEW");
	}

	@Test
	void onTimerExpiredNoOpWhenRoomWasRemoved() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		verify(roundTimer).schedule(eq(123456), anyLong(), runnableCaptor.capture());
		registryOf(mgr).remove(123456); // room gone before fire
		runnableCaptor.getValue().run(); // registry.get -> null -> return
		verify(sessionRepository, never()).updateStatus(anyString(), eq("REVIEW"));
	}

	// ---------- createRoom branches ----------

	@Test
	void createRoomThrowsWhenQuizNotFound() {
		when(quizRepository.findById("missing")).thenReturn(Optional.empty());
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				manager().createRoom("missing", "host");
			}
		});
	}

	/**
	 * Forces the PIN-collision retry loop: first findByPin collides, second is
	 * free.
	 */
	@Test
	void createRoomRetriesOnPinCollision() {
		when(quizRepository.findById("qz"))
				.thenReturn(Optional.of(new Quiz("qz", "T", "", null, Instant.now(), false)));
		when(sessionRepository.findByPin(anyString())).thenReturn(Optional.of(session("123456"))) // collide -> retry
				.thenReturn(Optional.empty()); // free -> exit
		when(sessionRepository.create(eq("qz"), eq("host"), anyString(), eq(null), anyString()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						return new GameSession("gen", "qz", inv.getArgument(2), "host", "LOBBY", "STANDARD", 0, null,
								null, null, Instant.now());
					}
				});

		GameSession created = manager().createRoom("qz", "host");

		assertEquals("LOBBY", created.getStatus());
		assertTrue(created.getPinCode().matches("\\d{6}"));
		verify(sessionRepository, atLeast(2)).findByPin(anyString()); // loop re-ran
	}

	// ---------- join: host-at-capacity guard ----------

	@Test
	void joinHostRejectedWhenRoomAtCapacity() throws Exception {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("maxPlayers");
		f.setAccessible(true);
		f.setInt(mgr, 1);
		mgr.join("123456", "P", "s0", "player", null); // fills the single slot
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.join("123456", "Host", "sh", "host", null);
			}
		});
	}

	// ---------- leave: missing-room guard ----------

	@Test
	void leaveOnMissingRoomIsNoOp() {
		manager().leave("000000", "x");
		verify(ws, never()).broadcast(any(), any());
		verify(ws, never()).broadcastRaw(any(), any());
	}

	// ---------- submit: active-but-wrong-question lock + null-player error send
	// ----------

	@Test
	void submitActiveButWrongQuestionIdIsLocked() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1"); // status ACTIVE, currentQuestionId = q1
		mgr.submit("123456", "q2", "ghost-uuid", "python", Json.readTree("{\"selectedIndex\":0}")); // wrong qid,
																									// unknown player
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(any(), sent.capture());
		assertTrue(((ErrorMessage) sent.getValue()).getMessage().contains("Round is locked"));
		verify(writeBuffer, never()).offer(any());
	}

	// ---------- submit coding: unknown player no-op ----------

	@Test
	void submitCodingUnknownPlayerIsIgnored() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		mgr.join("123456", "Cody", "sess-c", "player", null);
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", "ghost-uuid", "python", Json.readTree("{\"source\":\"x\"}")); // no such player ->
																									// return early
		verify(submissionProcessor, never()).processCoding(anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any());
		verify(ws, never()).send(anyString(), any());
	}

	// ---------- submit coding: handler applies score + notifies ----------

	@Test
	void submitCodingHandlerAppliesScoreAndNotifies() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						{
							CodingOutcomeConsumer h = inv.getArgument(10);
							h.accept(player.getUuid(), 500, true, 5, 5, null); // correct path -> streak bonus 0, score
																				// 500
							h.accept(player.getUuid(), 100, false, 2, 5, null); // wrong path -> resetStreak, score 100
							return java.util.concurrent.CompletableFuture.completedFuture(true);
						}
					}
				});
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.times(2)).send(eq(player.getSessionId()), sent.capture());
		java.util.List<Object> all = sent.getAllValues();
		{
			boolean streamMatchResult = false;
			for (java.lang.Object o : all) {
				if (o instanceof SubmissionResult && ((SubmissionResult) o).getScore() == 500) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		{
			boolean streamMatchResult = false;
			for (java.lang.Object o : all) {
				if (o instanceof SubmissionResult && ((SubmissionResult) o).getScore() == 100
						&& !((SubmissionResult) o).isAllPassed()) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		// STANDARD delays the public board mid-round (join armed the only flush).
		verify(scheduler, times(1)).markDirty(eq(123456), any());
	}

	// ---------- startQuestion when index already past the last question ----------

	@Test
	void startQuestionAtEndEndsGameImmediately() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		setField(roomOf(mgr), "currentQuestionIndex", 1); // already past size 1
		mgr.startQuestion("123456");
		verify(sessionRepository).updateStatus("s1", "ENDED");
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.getRoomState("123456");
			}
		});
	}

	// ---------- private-method branches reached via reflection ----------

	@Test
	void sendRoundResultMissingRoomReturns() throws Exception {
		GameRoomManager mgr = manager();
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("sendRoundResult", String.class,
				boolean.class);
		m.setAccessible(true);
		m.invoke(mgr, "000000", true); // registry miss -> return
		verify(ws, never()).broadcast(any(), any());
	}

	@Test
	void sendRoundResultPastQuestionIndexReturns() throws Exception {
		GameRoomManager mgr = manager();
		seedRoom(mgr); // findByQuiz("qz") -> empty list via lenient stub
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("sendRoundResult", String.class,
				boolean.class);
		m.setAccessible(true);
		m.invoke(mgr, "123456", false); // idx 0 >= size 0 -> return
		verify(ws, never()).broadcast(any(), any());
	}

	@Test
	void sendRoundResultNonRevealedBuildsRoundResult() throws Exception {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		com.sprintjudge.service.Player p = mgr.join("123456", "A", "sa", "player", null);
		setField(roomOf(mgr), "status", "REVIEW");
		roomOf(mgr).setCurrentQuestionIndex(0);
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("sendRoundResult", String.class,
				boolean.class);
		m.setAccessible(true);
		m.invoke(mgr, "123456", false); // non-revealed -> answer null, body runs
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		RoundResult rr = null;
		for (java.lang.Object x : msg.getAllValues()) {
			if (x instanceof RoundResult) {
				rr = (RoundResult) x;
				break;
			}
		}
		if (rr == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(false, rr.isRevealed());
		assertNull(rr.getCorrectAnswer());
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.RoundResult.PlayerScore s : rr.getScores()) {
				if (s.getUuid().equals(p.getUuid())) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void deltaJsonNullBatchReturnsNull() throws Exception {
		GameRoomManager mgr = manager();
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("deltaJson",
				com.sprintjudge.service.leaderboard.DeltaLedger.Batch.class);
		m.setAccessible(true);
		assertNull(m.invoke(mgr, (Object) null));
	}

	@Test
	void sendFullLeaderboardMissingRoomNoSend() {
		GameRoomManager mgr = manager();
		mgr.sendFullLeaderboard("000000", "sess");
		verify(ws, never()).sendRaw(anyString(), anyString());
	}

	@Test
	void playerSessionIdsMissingRoomReturnsEmpty() throws Exception {
		GameRoomManager mgr = manager();
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("playerSessionIds", String.class);
		m.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.List<String> ids2 = (java.util.List<String>) m.invoke(mgr, "000000");
		assertTrue(ids2.isEmpty());
	}

	@Test
	void sessionIdOfUnknownPlayerReturnsNull() throws Exception {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "A", "sa", "player", null);
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("sessionIdOf", GameRoom.class,
				String.class);
		m.setAccessible(true);
		assertNull(m.invoke(mgr, roomOf(mgr), "ghost-uuid"));
	}

	@Test
	void startAndStopSweepRun() throws Exception {
		GameRoomManager mgr = manager();
		java.lang.reflect.Method start = GameRoomManager.class.getDeclaredMethod("startSweep");
		start.setAccessible(true);
		start.invoke(mgr);
		java.lang.reflect.Method stop = GameRoomManager.class.getDeclaredMethod("stopSweep");
		stop.setAccessible(true);
		stop.invoke(mgr);
	}

	// ---------- extra branch coverage ----------

	/**
	 * Drives the Runnable scheduled by extendTimer so its lambda body
	 * (onTimerExpired) runs.
	 */
	@Test
	void extendTimerSchedulesAndFiresExpiry() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456"); // ACTIVE
		mgr.extendTimer("123456", 30);
		verify(roundTimer, times(2)).schedule(eq(123456), anyLong(), runnableCaptor.capture());
		runnableCaptor.getValue().run(); // fire the timer's onTimerExpired lambda
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus()); // now < end -> no-op
	}

	/**
	 * toDto must handle a question whose config is null (falls back to Map.of()).
	 */
	@Test
	void startQuestionWithNullConfigBuildsDto() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		Question q = new Question("q1", "qz", "T", "D", "MCQ", null, 30, 100, null, 0, Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q));
		mgr.startQuestion("123456");
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : msg.getAllValues()) {
				if (m instanceof QuestionStart) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	/** A LOBBY room with a still-connected player must survive the idle sweep. */
	@Test
	void sweepKeepsLobbyRoomWithConnectedPlayer() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "A", "sa", "player", null); // connected -> connectedCount != 0
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(1, mgr.activeRooms()); // not removed
	}

	/** Empty (non-null) languagesAllowed list skips the language gate entirely. */
	@Test
	void codingWithEmptyLanguageListIsAllowed() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", java.util.List.of(), 60, 500, "{}", 0,
				Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"print(1)\"}"));
		verify(submissionProcessor).processCoding(eq("s1"), eq("123456"), eq("oj1"), eq("Cody"), eq(player.getUuid()),
				eq("python"), eq("print(1)"), anyInt(), any(), anyLong(), any());
		verify(ws, never()).send(anyString(), any());
	}

	/** A player who scored in the round must appear as correct in the reveal. */
	@Test
	void roundResultReportsCorrectFlagForScoringPlayer() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456"); // -> transitionToReview -> sendRoundResult(true)
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		RoundResult rr = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof RoundResult) {
				rr = (RoundResult) m;
				break;
			}
		}
		if (rr == null)
			throw new java.util.NoSuchElementException("No value present");
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.RoundResult.PlayerScore s : rr.getScores()) {
				if (s.getUuid().equals(player.getUuid()) && s.isCorrect()) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	/**
	 * A LOBBY room whose players disconnected but have not yet idled out survives
	 * the sweep.
	 */
	@Test
	void sweepKeepsFreshLobbyWithDisconnectedPlayer() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = mgr.join("123456", "A", "sa", "player", null);
		roomOf(mgr).softRemove(p.getUuid()); // connectedCount() == 0
		mgr.sweepIdleRooms(); // idleMs still < TTL -> keep
		assertEquals(1, mgr.activeRooms());
	}

	// ==================== PRACTICE MODE ====================

	@Test
	void createRoomWithPracticeMode() {
		when(quizRepository.findById("qz"))
				.thenReturn(Optional.of(new Quiz("qz", "T", "", null, Instant.now(), false)));
		lenient().when(sessionRepository.findByPin(anyString())).thenReturn(Optional.empty());
		when(sessionRepository.create(eq("qz"), eq("host"), anyString(), eq(null), anyString()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						return new GameSession("gen", "qz", inv.getArgument(2), "host", "LOBBY", "STANDARD", 0, null,
								null, null, Instant.now());
					}
				});

		GameSession created = manager().createRoom("qz", "host", GameRoom.GameMode.PRACTICE);

		assertEquals("LOBBY", created.getStatus());
		assertTrue(created.getPinCode().matches("\\d{6}"));
	}

	@Test
	void practiceStartQuestionDoesNotScheduleRoundTimer() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		mgr.join("123456", "Host", "sess-h", "host", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));

		mgr.startQuestion("123456");

		verify(roundTimer, never()).schedule(anyInt(), anyLong(), any());
		verify(ws, atLeastOnce()).broadcast(any(), any());
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus());
	}

	@Test
	void practiceSubmitSendsImmediateSubmissionResult() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");

		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		// PRACTICE sends immediate feedback to the player
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("sess-1"), sent.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : sent.getAllValues()) {
				if (m instanceof SubmissionResult && ((SubmissionResult) m).getScore() == 900) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void practiceAutoAdvanceAfterReview() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		mgr.join("123456", "Host", "sess-h", "host", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		lenient().when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(anyDouble(), anyLong(), anyLong(), anyInt(), anyInt(), any()))
				.thenReturn(900);

		mgr.startQuestion("123456");
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", mgr.join("123456", "P", "s1", "player", null).getUuid(), "python",
				Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456"); // -> transitionToReview -> schedules auto-advance

		// PRACTICE: startQuestion does NOT schedule round timer, only auto-advance does
		// (1 call)
		verify(roundTimer, times(1)).schedule(eq(123456), anyLong(), runnableCaptor.capture());
		Runnable autoAdvance = runnableCaptor.getValue();
		autoAdvance.run();
		// After auto-advance, should have moved to question 2
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus());
	}

	// ==================== EXAM MODE ====================

	@Test
	void createRoomWithExamMode() {
		when(quizRepository.findById("qz"))
				.thenReturn(Optional.of(new Quiz("qz", "T", "", null, Instant.now(), false)));
		lenient().when(sessionRepository.findByPin(anyString())).thenReturn(Optional.empty());
		when(sessionRepository.create(eq("qz"), eq("host"), anyString(), eq(null), anyString()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						return new GameSession("gen", "qz", inv.getArgument(2), "host", "LOBBY", "STANDARD", 0, null,
								null, null, Instant.now());
					}
				});

		GameSession created = manager().createRoom("qz", "host", GameRoom.GameMode.EXAM);

		assertEquals("LOBBY", created.getStatus());
	}

	@Test
	void examStartQuestionUsesTotalEndEpochMs() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.EXAM);
		mgr.join("123456", "Host", "sess-h", "host", null);
		long totalEnd = System.currentTimeMillis() + 300_000;
		roomOf(mgr).setTotalEndEpochMs(totalEnd);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));

		mgr.startQuestion("123456");

		// EXAM uses totalEndEpochMs, not per-question timer
		verify(roundTimer).schedule(eq(123456), eq(totalEnd), any());
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus());
	}

	@Test
	void examSubmitSendsImmediateFeedback() {
		GameRoomManager mgr = manager();
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

		createRoomWithMode(mgr, GameRoom.GameMode.EXAM);
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		armRound(mgr, "q1");

		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

		// EXAM sends immediate feedback
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("sess-1"), sent.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : sent.getAllValues()) {
				if (m instanceof SubmissionResult && ((SubmissionResult) m).getScore() == 900) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void examTimerExpiredEndsGame() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.EXAM);
		long totalEnd = System.currentTimeMillis() + 300_000;
		roomOf(mgr).setTotalEndEpochMs(totalEnd);
		mgr.startQuestion("123456");
		verify(roundTimer).schedule(eq(123456), eq(totalEnd), runnableCaptor.capture());

		// Set end time in the past so the timer fires
		setField(roomOf(mgr), "currentQuestionEndEpochMs", System.currentTimeMillis() - 10_000);
		runnableCaptor.getValue().run(); // onTimerExpired -> EXAM path -> async endGame

		// endGame removes the room from the registry (await the sweeper handoff)
		verify(sessionRepository, org.mockito.Mockito.timeout(5000)).updateStatus("s1", "ENDED");
		long deadline = System.currentTimeMillis() + 5000;
		while (true) {
			try {
				mgr.getRoomState("123456");
			} catch (IllegalArgumentException expected) {
				break;
			}
			if (System.currentTimeMillis() >= deadline)
				break;
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.getRoomState("123456");
			}
		});
	}

	// ==================== TEAM MODE ====================

	@Test
	void createTeamCreatesTeam() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "Host", "sess-h", "host", null);

		GameRoom.Team team = mgr.createTeam("123456", "Alpha");

		assertNotNull(team);
		assertEquals("Alpha", team.getName());
		assertTrue(team.getId().startsWith("team-"));
		assertTrue(team.getMemberUuids().isEmpty());
		assertEquals(0, team.getScore());
	}

	@Test
	void joinTeamAddsPlayerToTeam() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-a", "player", null);
		GameRoom.Team team = mgr.createTeam("123456", "Alpha");

		GameRoom.Team updated = mgr.joinTeam("123456", team.getId(), player.getUuid());

		assertTrue(updated.getMemberUuids().contains(player.getUuid()));
	}

	@Test
	void applyTeamScoreAggregatesScores() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "Host", "sess-h", "host", null);
		GameRoom.Team team = mgr.createTeam("123456", "Alpha");

		long result1 = roomOf(mgr).applyTeamScore(team.getId(), 500);
		long result2 = roomOf(mgr).applyTeamScore(team.getId(), 300);

		assertEquals(500, result1);
		assertEquals(800, result2);
	}

	@Test
	void teamLeaderboardShowsTeams() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p1 = mgr.join("123456", "Alice", "s1", "player", null);
		com.sprintjudge.service.Player p2 = mgr.join("123456", "Bob", "s2", "player", null);
		GameRoom.Team team1 = mgr.createTeam("123456", "Alpha");
		GameRoom.Team team2 = mgr.createTeam("123456", "Beta");
		mgr.joinTeam("123456", team1.getId(), p1.getUuid());
		mgr.joinTeam("123456", team2.getId(), p2.getUuid());
		roomOf(mgr).applyTeamScore(team1.getId(), 1000);
		roomOf(mgr).applyTeamScore(team2.getId(), 500);

		java.util.List<GameRoom.Team> teams = mgr.getTeams("123456");

		assertEquals(2, teams.size());
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.service.GameRoom.Team t : teams) {
				if ("Alpha".equals(t.getName()) && t.getScore() == 1000) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.service.GameRoom.Team t : teams) {
				if ("Beta".equals(t.getName()) && t.getScore() == 500) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void teamIdOfReturnsTeamForPlayer() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "s1", "player", null);
		GameRoom.Team team = mgr.createTeam("123456", "Alpha");
		mgr.joinTeam("123456", team.getId(), player.getUuid());

		assertEquals(team.getId(), roomOf(mgr).teamIdOf(player.getUuid()));
	}

	// ==================== BATTLE MODE ====================

	@Test
	void startBattleCreatesBracket() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		lenient().when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "Alice", "s1", "player", null);
		mgr.join("123456", "Bob", "s2", "player", null);

		mgr.startBattle("123456");

		java.util.List<GameRoom.BattleMatch> matches = mgr.getBattleMatches("123456");
		assertEquals(1, matches.size());
		assertEquals("q1", matches.get(0).getQuestionId());
	}

	@Test
	void startBattleRecordsBattleMatch() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		lenient().when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		com.sprintjudge.service.Player p1 = mgr.join("123456", "Alice", "s1", "player", null);
		com.sprintjudge.service.Player p2 = mgr.join("123456", "Bob", "s2", "player", null);

		mgr.startBattle("123456");

		java.util.List<GameRoom.BattleMatch> matches = mgr.getBattleMatches("123456");
		GameRoom.BattleMatch m = matches.get(0);
		// Both players should be in the match (order may vary due to shuffle)
		assertTrue(m.getP1Uuid().equals(p1.getUuid()) || m.getP1Uuid().equals(p2.getUuid()));
		assertTrue(m.getP2Uuid().equals(p1.getUuid()) || m.getP2Uuid().equals(p2.getUuid()));
	}

	@Test
	void startBattleBracketIsAccessible() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		lenient().when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "Alice", "s1", "player", null);
		mgr.join("123456", "Bob", "s2", "player", null);

		mgr.startBattle("123456");

		java.util.List<GameRoom.BattleRound> bracket = mgr.getBracket("123456");
		assertEquals(1, bracket.size());
		assertNotNull(bracket.get(0));
		assertEquals(1, bracket.get(0).getPairs().size());
	}

	@Test
	void startBattleNeedsAtLeastTwoPlayers() {
		GameRoomManager mgr = manager();
		lenient().when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "Alice", "s1", "player", null);

		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.startBattle("123456");
			}
		});
	}

	@Test
	void startBattleWithFourPlayersCreatesTwoMatches() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		lenient().when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "P1", "s1", "player", null);
		mgr.join("123456", "P2", "s2", "player", null);
		mgr.join("123456", "P3", "s3", "player", null);
		mgr.join("123456", "P4", "s4", "player", null);

		mgr.startBattle("123456");

		java.util.List<GameRoom.BattleMatch> matches = mgr.getBattleMatches("123456");
		assertEquals(2, matches.size());
		assertEquals(2, mgr.getBracket("123456").get(0).getPairs().size());
	}

	// ==================== AUTO_PILOT MODE ====================

	@Test
	void autoPilotAutoAdvanceTiming() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.AUTO_PILOT);
		mgr.join("123456", "Host", "sess-h", "host", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		lenient().when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

		mgr.startQuestion("123456");
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", mgr.join("123456", "P", "s1", "player", null).getUuid(), "python",
				Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");

		// AUTO_PILOT: startQuestion schedules per-question timer (1),
		// transitionToReview schedules auto-advance (2)
		verify(roundTimer, times(2)).schedule(eq(123456), anyLong(), runnableCaptor.capture());
		Runnable autoAdvance = runnableCaptor.getAllValues().get(runnableCaptor.getAllValues().size() - 1);
		autoAdvance.run();
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus());
	}

	@Test
	void autoPilotStartQuestionUsesPerQuestionTimer() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.AUTO_PILOT);
		mgr.join("123456", "Host", "sess-h", "host", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));

		mgr.startQuestion("123456");

		// AUTO_PILOT uses per-question timer like STANDARD
		verify(roundTimer).schedule(eq(123456), anyLong(), any());
	}

	// ==================== HOT-PATH GAP COVERAGE ====================

	@Test
	void startQuestionWhileActiveIsNoOp() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(sessionRepository, ws, roundTimer);
		mgr.startQuestion("123456");
		verify(sessionRepository, never()).updateStatus(anyString(), anyString());
		verify(roundTimer, never()).schedule(anyInt(), anyLong(), any());
	}

	@Test
	void startQuestionFromReviewReactivatesSameRound() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", mgr.getRoomState("123456").getStatus());
		org.mockito.Mockito.clearInvocations(ws);
		mgr.startQuestion("123456");
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : msg.getAllValues()) {
				if (m instanceof QuestionStart) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void nextQuestionFromActiveClosesRoundThenAdvances() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(sessionRepository);
		mgr.nextQuestion("123456");
		verify(sessionRepository).updateStatus("s1", "REVIEW");
		verify(sessionRepository).updateStatus("s1", "ACTIVE");
		assertEquals("ACTIVE", mgr.getRoomState("123456").getStatus());
		assertEquals("q2", roomOf(mgr).getCurrentQuestionId());
	}

	@Test
	void nextQuestionFromLobbyKeepsIndexAtZero() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		mgr.nextQuestion("123456");
		assertEquals(0, roomOf(mgr).getCurrentQuestionIndex());
		assertEquals("q1", roomOf(mgr).getCurrentQuestionId());
	}

	@Test
	void nextQuestionFromReviewIncrementsIndex() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		assertEquals(1, roomOf(mgr).getCurrentQuestionIndex());
		assertEquals("q2", roomOf(mgr).getCurrentQuestionId());
	}

	@Test
	void forceSubmitIgnoredWhenLobby() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		mgr.forceSubmit("123456");
		verify(roundTimer, never()).cancel(anyInt());
		verify(sessionRepository, never()).updateStatus(anyString(), eq("REVIEW"));
	}

	@Test
	void forceSubmitIgnoredWhenReview() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		org.mockito.Mockito.clearInvocations(roundTimer, sessionRepository);
		mgr.forceSubmit("123456");
		verify(roundTimer, never()).cancel(anyInt());
		verify(sessionRepository, never()).updateStatus(anyString(), eq("REVIEW"));
	}

	@Test
	void endGameTwiceSecondIsNoOp() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		mgr.endGame("123456");
		org.mockito.Mockito.clearInvocations(sessionRepository, ws);
		mgr.endGame("123456");
		verify(sessionRepository, never()).updateStatus(anyString(), anyString());
		verify(ws, never()).broadcast(any(), any());
	}

	@Test
	void extendTimerCapsTotalGrowthAt300s() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		long base = roomOf(mgr).getQuestionEndBaseEpochMs();
		long cur = roomOf(mgr).getCurrentQuestionEndEpochMs();
		mgr.extendTimer("123456", 10_000);
		long after = roomOf(mgr).getCurrentQuestionEndEpochMs();
		assertEquals(base + 300_000, after);
		assertTrue(after - cur < 10_000_000L);
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		TimerUpdate tu = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof TimerUpdate) {
				tu = (TimerUpdate) m;
				break;
			}
		}
		if (tu == null)
			throw new java.util.NoSuchElementException("No value present");
		assertTrue(tu.getExtendSec() < 10_000);
	}

	@Test
	void extendTimerFallsBackToCurrentWhenBaseUnset() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		setField(roomOf(mgr), "questionEndBaseEpochMs", 0L);
		long cur = roomOf(mgr).getCurrentQuestionEndEpochMs();
		mgr.extendTimer("123456", 20);
		assertEquals(cur + 20_000, roomOf(mgr).getCurrentQuestionEndEpochMs());
	}

	@Test
	void extendTimerNegativeShrinksDeadline() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		long cur = roomOf(mgr).getCurrentQuestionEndEpochMs();
		mgr.extendTimer("123456", -10);
		assertEquals(cur - 10_000, roomOf(mgr).getCurrentQuestionEndEpochMs());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		TimerUpdate tu = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof TimerUpdate) {
				tu = (TimerUpdate) m;
				break;
			}
		}
		if (tu == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(-10, tu.getExtendSec());
	}

	@Test
	void extendTimerAtMaxValueDoesNotOverflow() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		setField(roomOf(mgr), "questionEndBaseEpochMs", Long.MAX_VALUE);
		setField(roomOf(mgr), "currentQuestionEndEpochMs", Long.MAX_VALUE);
		mgr.extendTimer("123456", 30);
		assertEquals(Long.MAX_VALUE, roomOf(mgr).getCurrentQuestionEndEpochMs());
	}

	@Test
	void kickClosesKickedSession() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = mgr.join("123456", "Bad", "sess-k", "player", null);
		mgr.kickPlayer("123456", p.getUuid());
		verify(ws).close("sess-k");
	}

	@Test
	void kickUnknownStillMarksLeaderboardDirty() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "A", "sa", "player", null);
		org.mockito.Mockito.clearInvocations(scheduler);
		mgr.kickPlayer("123456", "ghost");
		verify(scheduler).markDirty(eq(123456), any());
		verify(ws, never()).close(anyString());
	}

	@Test
	void leaveUnknownPlayerKeepsRoomIntact() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = mgr.join("123456", "A", "sa", "player", null);
		mgr.leave("123456", "ghost-uuid");
		assertEquals(1, mgr.getRoomState("123456").getPlayers().size());
		assertTrue(roomOf(mgr).getPlayer(p.getUuid()).isConnected());
	}

	@Test
	void leaveMissingPlayerPublishesEventAnyway() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "A", "sa", "player", null);
		org.mockito.Mockito.clearInvocations(eventPublisher);
		mgr.leave("123456", "ghost");
		verify(eventPublisher).publishEvent(any(com.sprintjudge.service.event.GameEvent.PlayerLeft.class));
	}

	@Test
	void battleExcludesHostFromPairing() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sh", "host", null);
		com.sprintjudge.service.Player p1 = mgr.join("123456", "A", "s1", "player", null);
		com.sprintjudge.service.Player p2 = mgr.join("123456", "B", "s2", "player", null);
		mgr.startBattle("123456");
		java.util.List<com.sprintjudge.service.GameRoom.BattleMatch> matches = mgr.getBattleMatches("123456");
		assertEquals(1, matches.size());
		String pair = matches.get(0).getP1Uuid() + matches.get(0).getP2Uuid();
		assertTrue(pair.contains(p1.getUuid()) && pair.contains(p2.getUuid()));
		assertTrue(!matches.get(0).getP1Uuid().equals(host.getUuid())
				&& !matches.get(0).getP2Uuid().equals(host.getUuid()));
	}

	@Test
	void battleWithHostOnlyPlusTwoExcludesHost() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "H", "sh", "host", null);
		mgr.join("123456", "A", "s1", "player", null);
		mgr.join("123456", "B", "s2", "player", null);
		mgr.join("123456", "C", "s3", "player", null);
		mgr.startBattle("123456");
		assertEquals(1, mgr.getBattleMatches("123456").size());
	}

	@Test
	void battleRestartClearsStaleMatches() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "A", "s1", "player", null);
		mgr.join("123456", "B", "s2", "player", null);
		mgr.startBattle("123456");
		assertEquals(1, mgr.getBattleMatches("123456").size());
		setField(roomOf(mgr), "status", "LOBBY");
		mgr.startBattle("123456");
		assertEquals(1, mgr.getBattleMatches("123456").size());
	}

	@Test
	void battleFromActiveThrows() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "A", "s1", "player", null);
		mgr.join("123456", "B", "s2", "player", null);
		mgr.startBattle("123456");
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.startBattle("123456");
			}
		});
	}

	@Test
	void battleIndexOutOfRangeThrows() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "A", "s1", "player", null);
		mgr.join("123456", "B", "s2", "player", null);
		setField(roomOf(mgr), "currentQuestionIndex", 7);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.startBattle("123456");
			}
		});
	}

	@Test
	void battleEmptyQuizThrows() {
		GameRoomManager mgr = manager();
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
		createRoomWithMode(mgr, GameRoom.GameMode.BATTLE);
		mgr.join("123456", "A", "s1", "player", null);
		mgr.join("123456", "B", "s2", "player", null);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.startBattle("123456");
			}
		});
	}

	@Test
	void examStartFallsBackToSummedLimitsWhenTotalUnset() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.EXAM);
		mgr.join("123456", "H", "sh", "host", null);
		Question a = new Question("qa", "qz", "A", "D", "MCQ", null, 30, 100, Json.write(Map.of("correctIndex", 0)), 0,
				Instant.now());
		Question b = new Question("qb", "qz", "B", "D", "MCQ", null, 45, 100, Json.write(Map.of("correctIndex", 0)), 1,
				Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(a, b));
		long before = System.currentTimeMillis();
		mgr.startQuestion("123456");
		ArgumentCaptor<Long> endCap = ArgumentCaptor.forClass(Long.class);
		verify(roundTimer).schedule(eq(123456), endCap.capture(), any());
		long scheduled = endCap.getValue();
		assertTrue(scheduled - before >= 74_000 && scheduled - before <= 76_000);
	}

	@Test
	void practiceStartUsesMaxEndAndMinusOne() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.PRACTICE);
		mgr.join("123456", "H", "sh", "host", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		assertEquals(Long.MAX_VALUE, roomOf(mgr).getCurrentQuestionEndEpochMs());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		QuestionStart qs = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof QuestionStart) {
				qs = (QuestionStart) m;
				break;
			}
		}
		if (qs == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(-1, qs.getTimeLimitSec());
	}

	@Test
	void examSubmitKeepsLiveBoardArmed() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.EXAM);
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(700);
		com.sprintjudge.service.Player player = mgr.join("123456", "E", "se", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(scheduler, times(2)).markDirty(eq(123456), any());
	}

	@Test
	void reviewFlushFailureStillReveals() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		org.mockito.Mockito.doThrow(new RuntimeException("flush down")).when(writeBuffer).flush();
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", mgr.getRoomState("123456").getStatus());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : msg.getAllValues()) {
				if (m instanceof RoundResult) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void examReviewSuppressesLeaderboardBroadcast() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.EXAM);
		mgr.join("123456", "H", "sh", "host", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(ws);
		mgr.forceSubmit("123456");
		verify(ws, never()).broadcastRaw(any(), anyString());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : msg.getAllValues()) {
				if (m instanceof RoundResult) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void standardReviewFlushesLeaderboardDelta() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(ws);
		mgr.forceSubmit("123456");
		verify(ws, atLeastOnce()).broadcastRaw(any(), anyString());
	}

	@Test
	void endGameFlushFailureStillEvictsAndReveals() {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		lenient().when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		mgr.startQuestion("123456");
		org.mockito.Mockito.doThrow(new RuntimeException("flush down")).when(writeBuffer).flush();
		mgr.endGame("123456");
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.getRoomState("123456");
			}
		});
		verify(sessionRepository).updateStatus("s1", "ENDED");
	}

	@Test
	void buildReviewExcludesHostFromPlayerStats() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sh", "host", null);
		com.sprintjudge.service.Player p1 = mgr.join("123456", "A", "s1", "player", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		com.sprintjudge.domain.models.Submission sub = new com.sprintjudge.domain.models.Submission("sub1", "s1", "q1",
				"A", p1.getUuid(), "{}", 900, true, null, 1, Instant.now());
		when(submissionRepository.findBySession("s1")).thenReturn(List.of(sub));
		mgr.endGame("123456");
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		GameReview review = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof GameReview) {
				review = (GameReview) m;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		{
			boolean streamNoneResult = true;
			for (com.sprintjudge.domain.dto.GameReview.PlayerReview pr : review.getPlayers()) {
				if (pr.getPlayerUuid().equals(host.getUuid())) {
					streamNoneResult = false;
					break;
				}
			}
			assertTrue(streamNoneResult);
		}
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.GameReview.PlayerReview pr : review.getPlayers()) {
				if (pr.getPlayerUuid().equals(p1.getUuid())) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		assertEquals(1, review.getClassStats().getTotalPlayers());
	}

	@Test
	void buildReviewWithHostOnlyProducesZeroContestants() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "Host", "sh", "host", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		mgr.endGame("123456");
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		GameReview review = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof GameReview) {
				review = (GameReview) m;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(0, review.getClassStats().getTotalPlayers());
		assertTrue(review.getPlayers().isEmpty());
	}

	@Test
	void flushLeaderboardEmptyBoardStaysSilent() {
		GameRoomManager mgr = manager();
		createRoomWithMode(mgr, GameRoom.GameMode.STANDARD);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.flushLeaderboardDelta("123456");
		verify(ws, never()).broadcastRaw(any(), anyString());
	}

	@Test
	void flushLeaderboardHealsWithFullBatchOnSecondFlush() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "A", "sa", "player", null);
		mgr.flushLeaderboardDelta("123456");
		org.mockito.Mockito.clearInvocations(ws);
		mgr.flushLeaderboardDelta("123456");
		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(ws).broadcastRaw(any(), payload.capture());
		assertTrue(payload.getValue().contains("\"resync\":true"));
	}

	@Test
	void deltaJsonResyncEmptyReturnsNull() throws Exception {
		GameRoomManager mgr = manager();
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("deltaJson",
				com.sprintjudge.service.leaderboard.DeltaLedger.Batch.class);
		m.setAccessible(true);
		com.sprintjudge.service.leaderboard.DeltaLedger.Batch empty = new com.sprintjudge.service.leaderboard.DeltaLedger.Batch(
				5L, true, List.of());
		assertNull(m.invoke(mgr, empty));
	}

	@Test
	void deltaJsonBuildsEntriesForNonEmpty() throws Exception {
		GameRoomManager mgr = manager();
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("deltaJson",
				com.sprintjudge.service.leaderboard.DeltaLedger.Batch.class);
		m.setAccessible(true);
		com.sprintjudge.service.leaderboard.DeltaLedger.Batch batch = new com.sprintjudge.service.leaderboard.DeltaLedger.Batch(
				2L, false, List.of(new com.sprintjudge.service.leaderboard.DeltaLedger.Delta("u1", "A", 900, 1)));
		String json = (String) m.invoke(mgr, batch);
		assertTrue(json.contains("LEADERBOARD_DELTA"));
		assertTrue(json.contains("u1"));
		assertTrue(json.contains("900"));
	}

	@Test
	void sendRoundResultExcludesHostFromScores() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sh", "host", null);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(500);
		com.sprintjudge.service.Player p = mgr.join("123456", "A", "sa", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		org.mockito.Mockito.clearInvocations(ws);
		mgr.forceSubmit("123456");
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
		RoundResult rr = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof RoundResult) {
				rr = (RoundResult) m;
				break;
			}
		}
		if (rr == null)
			throw new java.util.NoSuchElementException("No value present");
		{
			boolean streamNoneResult = true;
			for (com.sprintjudge.domain.dto.RoundResult.PlayerScore s : rr.getScores()) {
				if (s.getUuid().equals(host.getUuid())) {
					streamNoneResult = false;
					break;
				}
			}
			assertTrue(streamNoneResult);
		}
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.RoundResult.PlayerScore s : rr.getScores()) {
				if (s.getUuid().equals(p.getUuid())) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void wrongThenCorrectResetsStreakWithoutBonus() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0, 1.0);
		when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(800);
		com.sprintjudge.service.Player player = mgr.join("123456", "A", "sa", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved = ArgumentCaptor
				.forClass(com.sprintjudge.domain.models.Submission.class);
		verify(writeBuffer, times(2)).offer(saved.capture());
		assertEquals(0, saved.getAllValues().get(0).getScoreEarned());
		assertEquals(800, saved.getAllValues().get(1).getScoreEarned());
	}

	@Test
	void streakBonusCapsAtFiveSteps() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		com.sprintjudge.service.Player player = mgr.join("123456", "A", "sa", "player", null);
		armRound(mgr, "q1");
		for (int i = 0; i < 8; i++) {
			mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		}
		ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved = ArgumentCaptor
				.forClass(com.sprintjudge.domain.models.Submission.class);
		verify(writeBuffer, times(8)).offer(saved.capture());
		assertEquals(100, saved.getAllValues().get(0).getScoreEarned());
		assertEquals(110, saved.getAllValues().get(1).getScoreEarned());
		assertEquals(150, saved.getAllValues().get(5).getScoreEarned());
		assertEquals(150, saved.getAllValues().get(6).getScoreEarned());
		assertEquals(150, saved.getAllValues().get(7).getScoreEarned());
	}

	@Test
	void submitActiveWrongQuestionForKnownPlayerTargetsSession() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		com.sprintjudge.service.Player player = mgr.join("123456", "A", "sa", "player", null);
		armRound(mgr, "q1");
		mgr.submit("123456", "q2", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("sa"), sent.capture());
		assertTrue(((ErrorMessage) sent.getValue()).getMessage().contains("Round is locked"));
		verify(writeBuffer, never()).offer(any());
	}

	@Test
	void codingRejectedRefundsAttemptAndSendsZeroResult() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						{
							CodingOutcomeConsumer h = inv.getArgument(10);
							h.rejected(player.getUuid());
							return java.util.concurrent.CompletableFuture.completedFuture(true);
						}
					}
				});
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		assertEquals(0, roomOf(mgr).attemptCount("oj1", player.getUuid()));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("sess-c"), sent.capture());
		assertTrue(sent.getValue() instanceof SubmissionResult && ((SubmissionResult) sent.getValue()).getScore() == 0
				&& !((SubmissionResult) sent.getValue()).isAllPassed());
	}

	@Test
	void codingAcceptWithFeedbackSendsFeedbackAndKeepsAttempt() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						{
							CodingOutcomeConsumer h = inv.getArgument(10);
							h.accept(player.getUuid(), 400, false, 3, 5, "fix edge cases");
							return java.util.concurrent.CompletableFuture.completedFuture(true);
						}
					}
				});
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		assertEquals(1, roomOf(mgr).attemptCount("oj1", player.getUuid()));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("sess-c"), sent.capture());
		assertTrue(sent.getValue() instanceof SubmissionResult && ((SubmissionResult) sent.getValue()).getScore() == 400
				&& "fix edge cases".equals(((SubmissionResult) sent.getValue()).getAiFeedback()));
	}

	@Test
	void codingBusyFalseRefundsSoRetryKeepsZeroAttempts() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(false));
		armRound(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		assertEquals(0, roomOf(mgr).attemptCount("oj1", player.getUuid()));
	}

	@Test
	void sweepEvictsAbandonedActiveRoom() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = mgr.join("123456", "A", "sa", "player", null);
		lenient().when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		roomOf(mgr).setStatus("ACTIVE");
		roomOf(mgr).softRemove(p.getUuid());
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(0, mgr.activeRooms());
		verify(sessionRepository).updateStatus("s1", "ENDED");
	}

	@Test
	void createTeamBlankNameThrows() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "H", "sh", "host", null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.createTeam("123456", "  ");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.createTeam("123456", null);
			}
		});
	}

	@Test
	void joinTeamBlankIdsThrow() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.service.Player p = mgr.join("123456", "A", "sa", "player", null);
		com.sprintjudge.service.GameRoom.Team team = mgr.createTeam("123456", "Alpha");
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", "  ", p.getUuid());
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", team.getId(), "  ");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", null, p.getUuid());
			}
		});
	}

	@Test
	void joinTeamUnknownPlayerThrows() {
		GameRoomManager mgr = manager();
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		mgr.join("123456", "A", "sa", "player", null);
		com.sprintjudge.service.GameRoom.Team team = mgr.createTeam("123456", "Alpha");
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", team.getId(), "ghost");
			}
		});
	}
}
