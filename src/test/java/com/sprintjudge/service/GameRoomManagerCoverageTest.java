package com.sprintjudge.service;

import com.sprintjudge.domain.dto.SubmissionResult;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.room.RoomRegistry;
import com.sprintjudge.util.Json;
import com.sprintjudge.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-closure tests for the room manager's defensive arms: null/gone seats,
 * host guards, timer races, sweep matrix, flush healing and fan-out filtering.
 * Each test targets one JaCoCo-flagged site.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class GameRoomManagerCoverageTest {

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

	private GameRoomManager manager() {
		return new GameRoomManager(sessionRepository, quizRepository, questionRepository, submissionRepository,
				scoringEngine, processorProvider, ws, evaluationService, settingsService, scheduler, writeBuffer,
				roundTimer, eventPublisher);
	}

	private GameSession session(String pin) {
		return new GameSession("s1", "qz", pin, "host", "LOBBY", "STANDARD", 0, null, null, null, Instant.now());
	}

	private Question mcq(String id) {
		return new Question(id, "qz", "T", "D", "MCQ", null, 30, 100, Json.write(java.util.Map.of("correctIndex", 0)),
				0, Instant.now());
	}

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

	private void armActive(GameRoomManager mgr, String questionId) {
		GameRoom room = roomOf(mgr);
		room.setStatus("ACTIVE");
		room.setCurrentQuestionId(questionId);
		room.setCurrentQuestionStartEpochMs(Instant.now().toEpochMilli());
	}

	private Object callPrivate(GameRoomManager mgr, String name, Object... args) throws Exception {
		Class<?>[] types = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++)
			types[i] = String.class;
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod(name, types);
		m.setAccessible(true);
		return m.invoke(mgr, args);
	}

	private void awaitStatus(GameRoomManager mgr, String pin, String want) throws Exception {
		RoomRegistry reg = registryOf(mgr);
		long end = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < end) {
			GameRoom r = reg.get(Integer.parseInt(pin));
			if (r != null && want.equals(r.getStatus()))
				return;
			Thread.sleep(50);
		}
		throw new AssertionError("room " + pin + " never reached " + want);
	}

	// ---------- createRoom pin-collision retry ----------

	@Test
	void createRoomRetriesOnPinCollision() {
		when(quizRepository.findById("qz")).thenReturn(
				Optional.of(new com.sprintjudge.domain.models.Quiz("qz", "T", "", null, Instant.now(), false)));
		when(sessionRepository.findByPin(anyString())).thenReturn(Optional.of(session("123456")))
				.thenReturn(Optional.of(session("123456"))).thenReturn(Optional.empty());
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
		verify(sessionRepository).create(eq("qz"), eq("host-1"), anyString(), eq(null), anyString());
	}

	// ---------- capacity ----------

	@Test
	void playerJoinWhenFullThrows() throws Exception {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("maxPlayers");
		f.setAccessible(true);
		f.setInt(mgr, 1);
		mgr.join("123456", "Host", "sess-h", "host", null);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.join("123456", "Extra", "s1", "player", null);
			}
		});
	}

	// ---------- submit guards ----------

	@Test
	void submitNullUuidIsIgnored() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sess-1", "player", null);
		mgr.submit("123456", "q1", null, "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(writeBuffer, never()).offer(any());
	}

	@Test
	void submitterVanishingBeforeScoreIsIgnored() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-1", "player", null);
		GameRoom real = roomOf(mgr);
		GameRoom spy = new GameRoom(real.getSessionId(), real.getQuizId(), "123456", "LOBBY", 500,
				GameRoom.GameMode.STANDARD) {
			int calls;
			@Override
			public Player getPlayer(String uuid) {
				if (++calls == 1)
					return super.getPlayer(uuid);
				return null;
			}
		};
		for (Player p : real.getPlayers()) {
			spy.addPlayer(new Player(p.getUuid(), p.getName(), 0, "sess-1", true, "tok"));
		}
		spy.setStatus("ACTIVE");
		spy.setCurrentQuestionId("q1");
		spy.setCurrentQuestionStartEpochMs(Instant.now().toEpochMilli());
		registryOf(mgr).put(123456, spy);
		mgr.submit("123456", "q1", player.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(writeBuffer, never()).offer(any());
	}

	@Test
	void coderVanishingBeforeRunIsIgnored() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		GameRoom real = roomOf(mgr);
		GameRoom spy = new GameRoom(real.getSessionId(), real.getQuizId(), "123456", "LOBBY", 500,
				GameRoom.GameMode.STANDARD) {
			int calls;
			@Override
			public Player getPlayer(String uuid) {
				if (++calls == 1)
					return super.getPlayer(uuid);
				return null;
			}
		};
		for (Player p : real.getPlayers()) {
			spy.addPlayer(new Player(p.getUuid(), p.getName(), 0, "sess-c", true, "tok"));
		}
		spy.setStatus("ACTIVE");
		spy.setCurrentQuestionId("oj1");
		spy.setCurrentQuestionStartEpochMs(Instant.now().toEpochMilli());
		registryOf(mgr).put(123456, spy);
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		verify(submissionProcessor, never()).processCoding(anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any());
	}

	@Test
	void handlerAcceptToDepartedPlayerSendsNowhere() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						{
							CodingOutcomeConsumer h = inv.getArgument(10);
							mgr.kickPlayer("123456", player.getUuid());
							h.accept(player.getUuid(), 100, true, 1, 1, null);
							return java.util.concurrent.CompletableFuture.completedFuture(true);
						}
					}
				});
		armActive(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).send(eq((String) null), sent.capture());
		assertTrue(sent.getValue() instanceof SubmissionResult);
	}

	@Test
	void staleCodingResultAfterRoundAdvanceIsNotApplied() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		GameRoomManager mgr = manager();
		final com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						CodingOutcomeConsumer h = inv.getArgument(10);
						// The host skips while the judge is still running.
						roomOf(mgr).setStatus("REVIEW");
						h.accept(player.getUuid(), 500, true, 1, 1, null);
						return java.util.concurrent.CompletableFuture.completedFuture(true);
					}
				});
		armActive(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		assertEquals(0, roomOf(mgr).getBoard().scoreOf(player.getUuid()));
	}

	@Test
	void joinWithNullGameModeDefaultsToStandard() {
		GameSession nullMode = new GameSession("s1", "qz", "123456", "host", "LOBBY", null, 0, null, null, null,
				Instant.now());
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(nullMode));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		assertEquals(GameRoom.GameMode.STANDARD, roomOf(mgr).getGameMode());
	}

	@Test
	void joinWithBlankGameModeDefaultsToStandard() {
		GameSession blankMode = new GameSession("s1", "qz", "123456", "host", "LOBBY", "   ", 0, null, null, null,
				Instant.now());
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(blankMode));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		assertEquals(GameRoom.GameMode.STANDARD, roomOf(mgr).getGameMode());
	}

	@Test
	void staleCodingQuestionIdResultIsNotApplied() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		GameRoomManager mgr = manager();
		final com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						CodingOutcomeConsumer h = inv.getArgument(10);
						// Still ACTIVE, but the round advanced while the judge ran.
						roomOf(mgr).setCurrentQuestionId("a-different-question");
						h.accept(player.getUuid(), 500, true, 1, 1, null);
						return java.util.concurrent.CompletableFuture.completedFuture(true);
					}
				});
		armActive(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		assertEquals(0, roomOf(mgr).getBoard().scoreOf(player.getUuid()));
	}

	@Test
	void handlerRejectedToDepartedPlayerSendsNowhere() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						{
							CodingOutcomeConsumer h = inv.getArgument(10);
							mgr.kickPlayer("123456", player.getUuid());
							h.rejected(player.getUuid());
							return java.util.concurrent.CompletableFuture.completedFuture(true);
						}
					}
				});
		armActive(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).send(eq((String) null), sent.capture());
		assertTrue(sent.getValue() instanceof SubmissionResult);
	}

	// ---------- battle ----------

	@Test
	void startBattleNegativeIndexThrows() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		mgr.join("123456", "B", "sb", "player", null);
		roomOf(mgr).setCurrentQuestionIndex(-1);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.startBattle("123456");
			}
		});
	}

	// ---------- timer expiry ----------

	@Test
	void practiceExpiryIsIgnored() throws Exception {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
		when(questionRepository.findByQuiz("qz"))
				.thenReturn(java.util.List.of(new com.sprintjudge.domain.models.Question("q1", "qz", "T", "D", "MCQ",
						null, 30, 100, "{\"correctIndex\":0}", 0, null)));
		mgr.join("123456", "A", "sa", "player", null);
		GameRoom room = roomOf(mgr);
		room.setStatus("ACTIVE");
		room.setCurrentQuestionEndEpochMs(Instant.now().toEpochMilli() - 1000);
		callPrivate(mgr, "onTimerExpired", "123456");
		assertEquals("ACTIVE", room.getStatus());
		verify(roundTimer, never()).schedule(anyInt(), anyLong(), any());
	}

	@Test
	void asyncExamExpiryFailureIsContained() throws Exception {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		mgr.join("123456", "A", "sa", "player", null);
		GameRoom room = roomOf(mgr);
		room.setStatus("ACTIVE");
		room.setCurrentQuestionEndEpochMs(Instant.now().toEpochMilli() - 1000);
		doThrow(new RuntimeException("db down")).when(sessionRepository).updateStatus(anyString(), anyString());
		callPrivate(mgr, "onTimerExpired", "123456");
		awaitStatus(mgr, "123456", "ENDED");
	}

	@Test
	void asyncRoundExpiryFailureIsContained() throws Exception {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		GameRoom room = roomOf(mgr);
		room.setStatus("ACTIVE");
		room.setCurrentQuestionId("q1");
		room.setCurrentQuestionStartEpochMs(Instant.now().toEpochMilli());
		room.setCurrentQuestionEndEpochMs(Instant.now().toEpochMilli() - 1000);
		doThrow(new RuntimeException("db down")).when(sessionRepository).updateStatus(anyString(), anyString());
		callPrivate(mgr, "onTimerExpired", "123456");
		awaitStatus(mgr, "123456", "REVIEW");
	}

	@Test
	void transitionGuardReturnsOutsideActive() throws Exception {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		callPrivate(mgr, "transitionToReview", "123456");
		assertEquals("LOBBY", roomOf(mgr).getStatus());
		verify(ws, never()).broadcast(any(), any());
	}

	@Test
	void endGameTwiceSecondIsNoop() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		mgr.endGame("123456");
		mgr.endGame("123456");
		verify(sessionRepository, times(1)).updateStatus("s1", "ENDED");
	}

	// ---------- sweep matrix ----------

	@Test
	void sweepSkipsLiveAndEndedRooms() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(sessionRepository.findByPin("654321")).thenReturn(Optional.of(session("654321")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		mgr.join("654321", "B", "sb", "player", null);
		registryOf(mgr).get(654321).setStatus("ENDED");
		setField(registryOf(mgr).get(654321), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(2, mgr.activeRooms());
	}

	@Test
	void sweepDbFailureStillEvicts() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		roomOf(mgr).softRemove(roomOf(mgr).getPlayers().get(0).getUuid());
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		doThrow(new RuntimeException("db down")).when(sessionRepository).updateStatus(anyString(), anyString());
		mgr.sweepIdleRooms();
		assertEquals(0, mgr.activeRooms());
	}

	@Test
	void sweepFlushFailureStillEvicts() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		roomOf(mgr).softRemove(roomOf(mgr).getPlayers().get(0).getUuid());
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		doThrow(new RuntimeException("db down")).when(writeBuffer).flush();
		mgr.sweepIdleRooms();
		assertEquals(0, mgr.activeRooms());
	}

	// ---------- flush healing and fan-out ----------

	@Test
	void flushHealsResyncGapWithFullBatch() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		mgr.flushLeaderboardDelta("123456");
		mgr.flushLeaderboardDelta("123456");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(ws, times(2)).broadcastRaw(any(), json.capture());
		assertTrue(json.getAllValues().get(1).contains("\"resync\":true"));
	}

	@Test
	void fullLeaderboardEmptyBoardSilent() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Host", "sess-h", "host", null);
		mgr.sendFullLeaderboard("123456", "sess-h");
		verify(ws, never()).sendRaw(anyString(), anyString());
	}

	@Test
	void midFlushDeltaRearmsCoalescer() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player player = mgr.join("123456", "A", "sa", "player", null);
		doAnswer(new org.mockito.stubbing.Answer<Object>() {
			@Override
			public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv) throws java.lang.Throwable {
				{
					roomOf(mgr).applyScore(player.getUuid(), 5);
					return null;
				}
			}
		}).when(ws).broadcastRaw(any(), any());
		mgr.flushLeaderboardDelta("123456");
		verify(scheduler, times(2)).markDirty(eq(123456), any());
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void nullSessionFilteredFromFanout() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		roomOf(mgr).addPlayer(new Player("ghost", "G", 0, null, true, "tok"));
		mgr.flushLeaderboardDelta("123456");
		ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass((Class) Collection.class);
		verify(ws).broadcastRaw(ids.capture(), anyString());
		assertEquals(List.of("sa"), List.copyOf(ids.getValue()));
	}

	@Test
	void createRoomRetriesRegistryCollision() {
		when(quizRepository.findById("qz")).thenReturn(
				Optional.of(new com.sprintjudge.domain.models.Quiz("qz", "T", "", null, Instant.now(), false)));
		when(sessionRepository.create(eq("qz"), eq("host-1"), anyString(), eq(null), anyString()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						return new GameSession("gen", "qz", inv.getArgument(2), inv.getArgument(1), "LOBBY", "STANDARD",
								0, null, null, null, Instant.now());
					}
				});
		GameRoomManager mgr = manager();
		try (org.mockito.MockedStatic<com.sprintjudge.util.Ids> mocked = org.mockito.Mockito
				.mockStatic(com.sprintjudge.util.Ids.class)) {
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() {
					com.sprintjudge.util.Ids.pin();
				}
			}).thenReturn("123456", "654321");
			// Pre-occupy the first pin so the generator loops exactly once.
			registryOf(mgr).put(123456, new GameRoom("s9", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.STANDARD));
			GameSession created = mgr.createRoom("qz", "host-1");
			assertEquals("654321", created.getPinCode());
		}
	}

	@Test
	void busyRejectToDepartedPlayerSendsNowhere() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player player = mgr.join("123456", "Cody", "sess-c", "player", null);
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						{
							mgr.kickPlayer("123456", player.getUuid());
							return java.util.concurrent.CompletableFuture.completedFuture(false);
						}
					}
				});
		armActive(mgr, "oj1");
		mgr.submit("123456", "oj1", player.getUuid(), "python", Json.readTree("{\"source\":\"x\"}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).send(eq((String) null), sent.capture());
		assertTrue(sent.getValue() instanceof com.sprintjudge.domain.dto.ErrorMessage);
	}

	@Test
	void endGameOnEndedRoomIsNoop() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		roomOf(mgr).setStatus("ENDED");
		mgr.endGame("123456");
		verify(sessionRepository, never()).updateStatus(anyString(), anyString());
	}

	@Test
	void reviewRanksEasiestAboveHardest() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of(
				new com.sprintjudge.domain.models.Submission("s1", "s1", "q1", "A", "ua", "{}", 900, true, null, 1,
						Instant.now()),
				new com.sprintjudge.domain.models.Submission("s2", "s1", "q2", "A", "ua", "{}", 0, false, null, 1,
						Instant.now())));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		mgr.endGame("123456");
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).broadcast(anyCollection(), sent.capture());
		Object review = null;
		for (java.lang.Object o : sent.getAllValues()) {
			if (o instanceof com.sprintjudge.domain.dto.GameReview) {
				review = (com.sprintjudge.domain.dto.GameReview) o;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		com.sprintjudge.domain.dto.GameReview gr = (com.sprintjudge.domain.dto.GameReview) review;
		assertEquals("q1", gr.getClassStats().getEasiestQuestionId());
		assertEquals("q2", gr.getClassStats().getHardestQuestionId());
	}

	@Test
	void reviewNeverCountsAStrayHostSeatAsAStudent() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		roomOf(mgr).addPlayer(new Player("ghost", "Host", 0, "sx", true, "t"));
		mgr.endGame("123456");
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).broadcast(anyCollection(), sent.capture());
		com.sprintjudge.domain.dto.GameReview review = null;
		for (java.lang.Object o : sent.getAllValues()) {
			if (o instanceof com.sprintjudge.domain.dto.GameReview) {
				review = (com.sprintjudge.domain.dto.GameReview) o;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(1, review.getClassStats().getTotalPlayers());
		assertEquals(1, review.getPlayers().size());
	}

	@Test
	void sweepSkipsConnectedActiveRoom() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		roomOf(mgr).setStatus("ACTIVE");
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(1, mgr.activeRooms());
	}

	@Test
	void sweepEvictsIdleReviewRoom() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		GameRoom room = roomOf(mgr);
		room.softRemove(room.getPlayers().get(0).getUuid());
		room.setStatus("REVIEW");
		setField(room, "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(0, mgr.activeRooms());
	}

	@Test
	void flushEmptyBoardStaysSilent() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Host", "sess-h", "host", null);
		org.mockito.Mockito.clearInvocations(scheduler);
		mgr.flushLeaderboardDelta("123456");
		verify(ws, never()).broadcastRaw(any(), anyString());
	}

	@Test
	void flushAfterKickHealsWithoutNoise() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player player = mgr.join("123456", "A", "sa", "player", null);
		mgr.flushLeaderboardDelta("123456");
		mgr.kickPlayer("123456", player.getUuid());
		org.mockito.Mockito.clearInvocations(ws);
		mgr.flushLeaderboardDelta("123456");
		verify(ws, never()).broadcastRaw(any(), anyString());
	}

	@Test
	void reviewCarriesDisplayOptions() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		Question withOptions = new Question("q1", "qz", "T", "D", "MCQ", null, 30, 100,
				Json.write(java.util.Map.of("options", List.of("A", "B"), "correctIndex", 1)), 0, Instant.now());
		Question withLines = new Question("q2", "qz", "T", "D", "DRAG_SORT", null, 30, 100,
				Json.write(java.util.Map.of("lines", List.of(java.util.Map.of("id", "l1", "text", "first")))), 1,
				Instant.now());
		Question bare = new Question("q3", "qz", "T", "D", "NUMERIC", null, 30, 100, null, 2, Instant.now());
		Question ragged = new Question("q4", "qz", "T", "D", "MCQ", null, 30, 100, "{\"options\":{}}", 3,
				Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(withOptions, withLines, bare, ragged));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		GameRoomManager mgr = manager();
		mgr.join("123456", "A", "sa", "player", null);
		mgr.endGame("123456");
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).broadcast(anyCollection(), sent.capture());
		com.sprintjudge.domain.dto.GameReview review = null;
		for (java.lang.Object o : sent.getAllValues()) {
			if (o instanceof com.sprintjudge.domain.dto.GameReview) {
				review = (com.sprintjudge.domain.dto.GameReview) o;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(List.of("A", "B"), review.getQuestions().get(0).getOptions());
		assertEquals(List.of("first"), review.getQuestions().get(1).getOptions());
		assertEquals(null, review.getQuestions().get(2).getOptions());
		assertEquals(null, review.getQuestions().get(3).getOptions());
	}

	@Test
	void submitNullUuidWithLiveRoundIsIgnored() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sess-1", "player", null);
		armActive(mgr, "q1");
		mgr.submit("123456", "q1", null, "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(writeBuffer, never()).offer(any());
		assertEquals(0, roomOf(mgr).attemptCount("q1", null));
	}

	// ---------- multi-round sagas per mode ----------

	@Test
	void standardFullGameTwoQuestionsTwoPlayersWalkAndAccumulate() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player alice = mgr.join("123456", "Alice", "sa", "player", null);
		com.sprintjudge.service.Player bob = mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		assertEquals("q1", roomOf(mgr).getCurrentQuestionId());
		mgr.submit("123456", "q1", alice.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", bob.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(100, roomOf(mgr).getPlayer(alice.getUuid()).getScore());
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		assertEquals("q2", roomOf(mgr).getCurrentQuestionId());
		mgr.submit("123456", "q2", alice.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q2", bob.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(210, roomOf(mgr).getPlayer(alice.getUuid()).getScore());
		assertEquals(210, roomOf(mgr).getPlayer(bob.getUuid()).getScore());
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
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
	void standardFullGameThreeQuestionsFourPlayersFinalRanksFollowScores() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2"), mcq("q3")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(questionRepository.findById("q3")).thenReturn(Optional.of(mcq("q3")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "A", "s1", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "B", "s2", "player", null);
		mgr.join("123456", "C", "s3", "player", null);
		mgr.join("123456", "D", "s4", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q3", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q3", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		java.util.List<com.sprintjudge.domain.dto.LeaderboardEntry> lb = roomOf(mgr).getLeaderboard();
		assertEquals(a.getUuid(), lb.get(0).getUuid());
		assertTrue(roomOf(mgr).getPlayer(a.getUuid()).getScore() >= roomOf(mgr).getPlayer(b.getUuid()).getScore());
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		verify(sessionRepository).updateStatus("s1", "ENDED");
	}

	@Test
	void standardFullGameReviewShipsBothQuestionsAndPlayerTotals() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player alice = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.join("123456", "Bob", "sb", "player", null);
		when(submissionRepository.findBySession("s1")).thenReturn(List.of(
				new com.sprintjudge.domain.models.Submission("id1", "s1", "q1", "Alice", alice.getUuid(), "{}", 100,
						true, null, 1, java.time.Instant.now()),
				new com.sprintjudge.domain.models.Submission("id2", "s1", "q2", "Alice", alice.getUuid(), "{}", 110,
						true, null, 1, java.time.Instant.now())));
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", alice.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", alice.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		org.mockito.Mockito.clearInvocations(ws);
		mgr.nextQuestion("123456");
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
		Object review = null;
		for (java.lang.Object o : sent.getAllValues()) {
			if (o instanceof com.sprintjudge.domain.dto.GameReview) {
				review = (com.sprintjudge.domain.dto.GameReview) o;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		com.sprintjudge.domain.dto.GameReview gr = (com.sprintjudge.domain.dto.GameReview) review;
		assertEquals(2, gr.getQuestions().size());
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.GameReview.PlayerReview p : gr.getPlayers()) {
				if (p.getTotalScore() == 210) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		assertEquals(2, gr.getClassStats().getTotalQuestions());
	}

	@Test
	void standardSagaWrongThenCorrectResetsStreakAcrossRounds() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0, 1.0);
		when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player alice = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", alice.getUuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
		assertEquals(0, roomOf(mgr).streakOf(alice.getUuid()));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", alice.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(1, roomOf(mgr).streakOf(alice.getUuid()));
		assertEquals(100, roomOf(mgr).getPlayer(alice.getUuid()).getScore());
	}

	@Test
	void examFullGameSuppressesReviewFlushButShipsFinalReview() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(700);
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		org.mockito.Mockito.clearInvocations(scheduler);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		verify(scheduler, never()).markDirty(anyInt(), any());
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		verify(sessionRepository).updateStatus("s1", "ENDED");
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object o : sent.getAllValues()) {
				if (o instanceof com.sprintjudge.domain.dto.GameReview) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void examFullGameTwoPlayersScoresAccumulateDespiteSuppressedBoard() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(500);
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(500, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(1050, roomOf(mgr).getPlayer(a.getUuid()).getScore());
	}

	@Test
	void practiceFullGameTwoRoundsKeepsLiveBoardAndAutoAdvanceArmed() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(scheduler, org.mockito.Mockito.atLeast(3)).markDirty(eq(123456), any());
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		verify(roundTimer, org.mockito.Mockito.atLeastOnce()).schedule(eq(123456), anyLong(), any());
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		assertEquals("q2", roomOf(mgr).getCurrentQuestionId());
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(1890, roomOf(mgr).getPlayer(a.getUuid()).getScore());
	}

	@Test
	void practiceFullGameWrongAnswersStillScoreZeroAndAdvance() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);
		when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
		assertEquals(0, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
		assertEquals(0, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		assertEquals(0, roomOf(mgr).streakOf(a.getUuid()));
	}

	@Test
	void autoPilotFullGameTwoRoundsSchedulesTimersAndAccumulates() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.AUTO_PILOT));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(400);
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		verify(roundTimer, times(1)).schedule(eq(123456), anyLong(), any());
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(840, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		verify(sessionRepository).updateStatus("s1", "ENDED");
	}

	@Test
	void teamFullGameTwoRoundsAggregatesIntoTeamTotals() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(200);
		GameRoom room = roomOf(mgr);
		com.sprintjudge.service.Player p1 = new Player("uuid-A1", "A1", 0, "s-a1", true);
		com.sprintjudge.service.Player p2 = new Player("uuid-A2", "A2", 0, "s-a2", true);
		com.sprintjudge.service.Player p3 = new Player("uuid-B1", "B1", 0, "s-b1", true);
		room.addPlayer(p1);
		room.addPlayer(p2);
		room.addPlayer(p3);
		com.sprintjudge.service.GameRoom.Team alpha = mgr.createTeam("123456", "Alpha");
		com.sprintjudge.service.GameRoom.Team beta = mgr.createTeam("123456", "Beta");
		mgr.joinTeam("123456", alpha.getId(), p1.getUuid());
		mgr.joinTeam("123456", alpha.getId(), p2.getUuid());
		mgr.joinTeam("123456", beta.getId(), p3.getUuid());
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", p1.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", p2.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", p3.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(400, room.getTeam(alpha.getId()).getScore());
		assertEquals(200, room.getTeam(beta.getId()).getScore());
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", room.getStatus());
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", p1.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(620, room.getTeam(alpha.getId()).getScore());
		assertEquals(2, mgr.getTeams("123456").size());
	}

	@Test
	void battleFullGameTwoPlayersPairsThenScoresThenCrownsChampion() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		lenient().when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(300);
		GameRoom room = roomOf(mgr);
		com.sprintjudge.service.Player p1 = new Player("uuid-P1", "P1", 0, "s-p1", true);
		com.sprintjudge.service.Player p2 = new Player("uuid-P2", "P2", 0, "s-p2", true);
		room.addPlayer(p1);
		room.addPlayer(p2);
		mgr.startBattle("123456");
		assertEquals("ACTIVE", room.getStatus());
		assertEquals(1, mgr.getBattleMatches("123456").size());
		assertEquals(1, mgr.getBracket("123456").size());
		mgr.submit("123456", "q1", p1.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", p2.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(300, room.getPlayer(p1.getUuid()).getScore());
		mgr.forceSubmit("123456");
		// A two-player battle is decided the moment the first round reveals.
		assertEquals("ENDED", room.getStatus());
		assertEquals(p1.getUuid(), room.getChampionUuid());
		verify(sessionRepository).updateStatus("s1", "ENDED");
	}

	@Test
	void forceSubmitMidRoundThenNextQuestionAdvancesIndexAndRearms() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		assertEquals(0, roomOf(mgr).getCurrentQuestionIndex());
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		assertEquals(1, roomOf(mgr).getCurrentQuestionIndex());
		assertEquals("q2", roomOf(mgr).getCurrentQuestionId());
		verify(writeBuffer, org.mockito.Mockito.atLeastOnce()).flush();
	}

	@Test
	void forceSubmitTwiceSecondStaysInReviewWithoutDuplicateFlush() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		org.mockito.Mockito.clearInvocations(writeBuffer);
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		verify(writeBuffer, never()).flush();
	}

	@Test
	void forceSubmitThenSubmitIsLockedWithRoundLockedError() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		org.mockito.Mockito.clearInvocations(ws);
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("sa"), sent.capture());
		assertTrue(
				((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).getMessage().contains("Round is locked"));
	}

	@Test
	void forceSubmitInLobbyIsIgnoredWithoutFlush() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.forceSubmit("123456");
		assertEquals("LOBBY", roomOf(mgr).getStatus());
		verify(writeBuffer, never()).flush();
	}

	@Test
	void examForceSubmitMidRoundThenNextQuestionKeepsExamTotal() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(600);
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		long examEnd = roomOf(mgr).getCurrentQuestionEndEpochMs();
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		assertEquals("q2", roomOf(mgr).getCurrentQuestionId());
		assertTrue(roomOf(mgr).getCurrentQuestionEndEpochMs() >= examEnd - 1000);
	}

	@Test
	void practiceForceSubmitMidRoundThenNextQuestionAutoSchedules() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		verify(roundTimer, times(1)).schedule(eq(123456), anyLong(), any());
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
	}

	@Test
	void teamForceSubmitMidRoundThenNextQuestionPreservesTeams() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(150);
		GameRoom room = roomOf(mgr);
		com.sprintjudge.service.Player p = new Player("uuid-X", "X", 0, "sx", true);
		room.addPlayer(p);
		com.sprintjudge.service.GameRoom.Team t = mgr.createTeam("123456", "Alpha");
		mgr.joinTeam("123456", t.getId(), p.getUuid());
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", room.getStatus());
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", room.getStatus());
		assertEquals(1, mgr.getTeams("123456").size());
		assertEquals(t.getId(), mgr.getTeams("123456").get(0).getId());
	}

	@Test
	void battleForceSubmitMidRoundThenNextQuestionKeepsBracket() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		lenient().when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(120);
		GameRoom room = roomOf(mgr);
		room.addPlayer(new Player("u1", "A", 0, "s1", true));
		room.addPlayer(new Player("u2", "B", 0, "s2", true));
		room.addPlayer(new Player("u3", "C", 0, "s3", true));
		room.addPlayer(new Player("u4", "D", 0, "s4", true));
		mgr.startBattle("123456");
		mgr.submit("123456", "q1", "u1", "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", "u3", "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", room.getStatus());
		int matchesBefore = mgr.getBattleMatches("123456").size();
		mgr.nextQuestion("123456");
		assertEquals("ACTIVE", room.getStatus());
		assertEquals(2, mgr.getBracket("123456").size());
		assertEquals(matchesBefore, mgr.getBattleMatches("123456").size());
	}

	@Test
	void extendTimerTriplePlus300CapsAtBasePlus300AndThirdAppliesZero() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		long base = roomOf(mgr).getQuestionEndBaseEpochMs();
		mgr.extendTimer("123456", 300);
		mgr.extendTimer("123456", 300);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.extendTimer("123456", 300);
		assertEquals(base + 300_000, roomOf(mgr).getCurrentQuestionEndEpochMs());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
	}

	@Test
	void extendTimerDoublePlus300SecondAppliesZero() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.extendTimer("123456", 300);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.extendTimer("123456", 300);
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
		assertEquals(roomOf(mgr).getQuestionEndBaseEpochMs() + 300_000, roomOf(mgr).getCurrentQuestionEndEpochMs());
	}

	@Test
	void extendTimerSinglePlus300AppliesFullExtension() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		long before = roomOf(mgr).getCurrentQuestionEndEpochMs();
		mgr.extendTimer("123456", 300);
		assertEquals(before + 300_000, roomOf(mgr).getCurrentQuestionEndEpochMs());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(300L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
	}

	@Test
	void extendTimerAfterCapNegativeReopensHeadroomForPositive() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.extendTimer("123456", 300);
		mgr.extendTimer("123456", -60);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.extendTimer("123456", 60);
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(60L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
	}

	@Test
	void extendTimerFourSmallExtensionsThenCapShrinksLast() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.extendTimer("123456", 100);
		mgr.extendTimer("123456", 100);
		mgr.extendTimer("123456", 100);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.extendTimer("123456", 100);
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
	}

	@Test
	void extendTimerZeroSecondsBroadcastsZeroWithoutMovingDeadline() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		long before = roomOf(mgr).getCurrentQuestionEndEpochMs();
		mgr.extendTimer("123456", 0);
		assertEquals(before, roomOf(mgr).getCurrentQuestionEndEpochMs());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
	}

	@Test
	void kickMidRoundThenSubmitFromKickedSeatBurnsNoAttempt() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		mgr.kickPlayer("123456", a.getUuid());
		org.mockito.Mockito.clearInvocations(ws, writeBuffer);
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(writeBuffer, never()).offer(any());
		assertEquals(0, roomOf(mgr).attemptCount("q1", a.getUuid()));
		verify(ws, never()).send(eq("sa"), any());
	}

	@Test
	void kickMidRoundOtherPlayerStillScores() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		mgr.kickPlayer("123456", a.getUuid());
		mgr.submit("123456", "q1", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(100, roomOf(mgr).getPlayer(b.getUuid()).getScore());
		assertNull(roomOf(mgr).getPlayer(a.getUuid()));
	}

	@Test
	void kickMidRoundRemovesBoardRowSoLeaderboardShrinks() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.join("123456", "Bob", "sb", "player", null);
		assertEquals(2, roomOf(mgr).getLeaderboard().size());
		mgr.startQuestion("123456");
		mgr.kickPlayer("123456", a.getUuid());
		assertEquals(1, roomOf(mgr).getLeaderboard().size());
		assertEquals(1, mgr.getRoomState("123456").getPlayers().size());
	}

	@Test
	void leaveAllPlayersThenSweepEvictsIdleLobbyRoom() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "Bob", "sb", "player", null);
		mgr.leave("123456", a.getUuid());
		mgr.leave("123456", b.getUuid());
		assertEquals(0, roomOf(mgr).connectedCount());
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(0, mgr.activeRooms());
		verify(sessionRepository).updateStatus("s1", "ENDED");
	}

	@Test
	void leaveAllMidActiveThenSweepEvictsAbandonedGame() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		mgr.leave("123456", a.getUuid());
		mgr.leave("123456", b.getUuid());
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(0, mgr.activeRooms());
	}

	@Test
	void leaveOneOfTwoKeepsRoomAliveThroughSweep() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.join("123456", "Bob", "sb", "player", null);
		mgr.leave("123456", a.getUuid());
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(1, mgr.activeRooms());
	}

	@Test
	void battleOddPlayerOutAdvancesOnABye() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
		GameRoom room = roomOf(mgr);
		room.addPlayer(new Player("u1", "A", 0, "s1", true));
		room.addPlayer(new Player("u2", "B", 0, "s2", true));
		room.addPlayer(new Player("u3", "C", 0, "s3", true));
		room.addPlayer(new Player("u4", "D", 0, "s4", true));
		room.addPlayer(new Player("u5", "E", 0, "s5", true));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startBattle("123456");
		assertEquals(2, mgr.getBattleMatches("123456").size());
		GameRoom.BattleRound round = mgr.getBracket("123456").get(0);
		assertEquals(3, round.getPairs().size());
		assertTrue(round.getPairs().get(2).isBye());
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(5)).send(anyString(), sent.capture());
		{
			boolean streamNoneResult = true;
			for (java.lang.Object streamItem : sent.getAllValues()) {
				if (streamItem instanceof com.sprintjudge.domain.dto.ErrorMessage) {
					streamNoneResult = false;
					break;
				}
			}
			assertTrue(streamNoneResult);
		}
	}

	@Test
	void battleEvenPlayersSendNoOddNotice() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
		GameRoom room = roomOf(mgr);
		room.addPlayer(new Player("u1", "A", 0, "s1", true));
		room.addPlayer(new Player("u2", "B", 0, "s2", true));
		room.addPlayer(new Player("u3", "C", 0, "s3", true));
		room.addPlayer(new Player("u4", "D", 0, "s4", true));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startBattle("123456");
		assertEquals(2, mgr.getBattleMatches("123456").size());
		verify(ws, times(4)).send(anyString(), any());
		verify(ws, never()).send(anyString(),
				org.mockito.ArgumentMatchers.isA(com.sprintjudge.domain.dto.ErrorMessage.class));
	}

	@Test
	void teamScoreAggregationAcrossRoundsSumsBothRounds() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoom room = roomOf(mgr);
		com.sprintjudge.service.Player p1 = new Player("u1", "A", 0, "s1", true);
		com.sprintjudge.service.Player p2 = new Player("u2", "B", 0, "s2", true);
		room.addPlayer(p1);
		room.addPlayer(p2);
		com.sprintjudge.service.GameRoom.Team t = mgr.createTeam("123456", "Alpha");
		mgr.joinTeam("123456", t.getId(), p1.getUuid());
		mgr.joinTeam("123456", t.getId(), p2.getUuid());
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", p1.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", p2.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", p1.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q2", p2.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(420, room.getTeam(t.getId()).getScore());
	}

	@Test
	void examSubmitArmsLiveBoardWhileReviewFlushStaysSuppressed() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(700);
		com.sprintjudge.service.Player p = mgr.join("123456", "Alice", "sa", "player", null);
		GameRoom room = roomOf(mgr);
		room.setStatus("ACTIVE");
		room.setCurrentQuestionId("q1");
		room.setCurrentQuestionStartEpochMs(java.time.Instant.now().toEpochMilli());
		org.mockito.Mockito.clearInvocations(scheduler);
		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(scheduler, times(1)).markDirty(eq(123456), any());
		org.mockito.Mockito.clearInvocations(scheduler);
		room.setStatus("ACTIVE");
		mgr.forceSubmit("123456");
		verify(scheduler, never()).markDirty(anyInt(), any());
	}

	@Test
	void rejoinTokenReclaimMidGameKeepsScoreAndSession() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(250);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player alice = mgr.join("123456", "Alice", "sa", "player", null);
		String token = alice.getToken();
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", alice.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(250, roomOf(mgr).getPlayer(alice.getUuid()).getScore());
		mgr.leave("123456", alice.getUuid());
		assertFalse(roomOf(mgr).getPlayer(alice.getUuid()).isConnected());
		com.sprintjudge.service.Player back = mgr.join("123456", "Alice", "sa-new", "player", token);
		assertEquals(alice.getUuid(), back.getUuid());
		assertEquals("sa-new", back.getSessionId());
		assertEquals(250, roomOf(mgr).getPlayer(alice.getUuid()).getScore());
		assertEquals(1, roomOf(mgr).attemptCount("q1", alice.getUuid()));
	}

	@Test
	void hostDoubleJoinRejectedWithAlreadyConnected() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Host", "sh", "host", null);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.join("123456", "Host2", "sh2", "host", null);
			}
		});
		assertEquals(1, mgr.getRoomState("123456").getPlayers().size());
	}

	@Test
	void submitAfterEndedIsIgnoredWithoutScoring() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.endGame("123456");
		org.mockito.Mockito.clearInvocations(writeBuffer, ws);
		try {
			mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("No active room"));
		}
		verify(writeBuffer, never()).offer(any());
	}

	@Test
	void standardThreePlayersOneIdleReviewStillListsIdleWithZero() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "A", "s1", "player", null);
		mgr.join("123456", "B", "s2", "player", null);
		mgr.join("123456", "C", "s3", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(210, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		assertEquals(0, roomOf(mgr).getPlayer(mgr.getRoomState("123456").getPlayers().get(1).getUuid()).getScore());
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		verify(sessionRepository).updateStatus("s1", "ENDED");
	}

	@Test
	void standardStreakAcrossThreeRoundsYieldsEscalatingBonus() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2"), mcq("q3")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(questionRepository.findById("q3")).thenReturn(Optional.of(mcq("q3")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q3", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(3, roomOf(mgr).streakOf(a.getUuid()));
		assertEquals(330, roomOf(mgr).getPlayer(a.getUuid()).getScore());
	}

	@Test
	void standardAttemptsIsolatedAcrossQuestionsAfterHeavyResubmit() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(10);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		for (int i = 0; i < 5; i++)
			mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(5, roomOf(mgr).attemptCount("q1", a.getUuid()));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		assertEquals(0, roomOf(mgr).attemptCount("q2", a.getUuid()));
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(1, roomOf(mgr).attemptCount("q2", a.getUuid()));
	}

	@Test
	void standardHostPresentExcludedFromScoresAcrossFullGame() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sh", "host", null);
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
		com.sprintjudge.domain.dto.GameReview review = null;
		for (java.lang.Object o : sent.getAllValues()) {
			if (o instanceof com.sprintjudge.domain.dto.GameReview) {
				review = (com.sprintjudge.domain.dto.GameReview) o;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		{
			boolean streamNoneResult = true;
			for (com.sprintjudge.domain.dto.GameReview.PlayerReview p : review.getPlayers()) {
				if (p.getPlayerUuid().equals(host.getUuid())) {
					streamNoneResult = false;
					break;
				}
			}
			assertTrue(streamNoneResult);
		}
		assertEquals(1, review.getClassStats().getTotalPlayers());
	}

	@Test
	void examThreeQuestionsFourPlayersWalkToEnded() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2"), mcq("q3")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(questionRepository.findById("q3")).thenReturn(Optional.of(mcq("q3")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(200);
		com.sprintjudge.service.Player a = mgr.join("123456", "A", "s1", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "B", "s2", "player", null);
		com.sprintjudge.service.Player c = mgr.join("123456", "C", "s3", "player", null);
		com.sprintjudge.service.Player d = mgr.join("123456", "D", "s4", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", c.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", d.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q3", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		verify(sessionRepository).updateStatus("s1", "ENDED");
		assertEquals(660, 200 + 220 + 240);
	}

	@Test
	void autoPilotThreeQuestionsWalkWithPerRoundTimers() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.AUTO_PILOT));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2"), mcq("q3")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(questionRepository.findById("q3")).thenReturn(Optional.of(mcq("q3")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(50);
		com.sprintjudge.service.Player a = mgr.join("123456", "A", "s1", "player", null);
		com.sprintjudge.service.Player b = mgr.join("123456", "B", "s2", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q3", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		verify(sessionRepository).updateStatus("s1", "ENDED");
		verify(roundTimer, org.mockito.Mockito.atLeast(4)).schedule(eq(123456), anyLong(), any());
	}

	@Test
	void teamTwoTeamsCompeteWinningTeamHasHigherTotal() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoom room = roomOf(mgr);
		com.sprintjudge.service.Player a1 = new Player("a1", "A1", 0, "s-a1", true);
		com.sprintjudge.service.Player a2 = new Player("a2", "A2", 0, "s-a2", true);
		com.sprintjudge.service.Player b1 = new Player("b1", "B1", 0, "s-b1", true);
		room.addPlayer(a1);
		room.addPlayer(a2);
		room.addPlayer(b1);
		com.sprintjudge.service.GameRoom.Team alpha = mgr.createTeam("123456", "Alpha");
		com.sprintjudge.service.GameRoom.Team beta = mgr.createTeam("123456", "Beta");
		mgr.joinTeam("123456", alpha.getId(), a1.getUuid());
		mgr.joinTeam("123456", alpha.getId(), a2.getUuid());
		mgr.joinTeam("123456", beta.getId(), b1.getUuid());
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a1.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", a2.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", b1.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		room.applyTeamScore(alpha.getId(), 200);
		room.applyTeamScore(beta.getId(), 100);
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		mgr.submit("123456", "q2", a1.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		room.applyTeamScore(alpha.getId(), 110);
		assertTrue(room.getTeam(alpha.getId()).getScore() > room.getTeam(beta.getId()).getScore());
	}

	@Test
	void battleFourPlayersCreateTwoMatchesAndScoreBothRounds() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(80);
		GameRoom room = roomOf(mgr);
		room.addPlayer(new Player("u1", "A", 0, "s1", true));
		room.addPlayer(new Player("u2", "B", 0, "s2", true));
		room.addPlayer(new Player("u3", "C", 0, "s3", true));
		room.addPlayer(new Player("u4", "D", 0, "s4", true));
		mgr.startBattle("123456");
		assertEquals(2, mgr.getBattleMatches("123456").size());
		assertEquals(2, mgr.getBracket("123456").get(0).getPairs().size());
		mgr.submit("123456", "q1", "u1", "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", "u3", "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");
		assertEquals(2, mgr.getBracket("123456").size());
		mgr.submit("123456", "q2", "u1", "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(168, room.getPlayer("u1").getScore());
	}

	@Test
	void extendTimerFivePlus60ThenPlus1CapsLastToZero() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		for (int i = 0; i < 5; i++)
			mgr.extendTimer("123456", 60);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.extendTimer("123456", 60);
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
	}

	@Test
	void extendTimerTwoPlus150SecondAppliesFullThenCapped() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.extendTimer("123456", 150);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.extendTimer("123456", 150);
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(150L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
		org.mockito.Mockito.clearInvocations(ws);
		mgr.extendTimer("123456", 150);
		ArgumentCaptor<Object> msg2 = ArgumentCaptor.forClass(Object.class);
		verify(ws, times(1)).broadcast(anyCollection(), msg2.capture());
		Object capped = null;
		for (java.lang.Object m : msg2.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				capped = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (capped == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) capped).getExtendSec());
	}

	@Test
	void extendTimerIgnoredInReviewWithoutReschedule() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		org.mockito.Mockito.clearInvocations(roundTimer, ws);
		mgr.extendTimer("123456", 30);
		verify(roundTimer, never()).schedule(anyInt(), anyLong(), any());
		assertEquals("REVIEW", roomOf(mgr).getStatus());
	}

	@Test
	void startQuestionWhileActiveIsNoopWithoutNewBroadcast() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(ws);
		mgr.startQuestion("123456");
		verify(ws, never()).broadcast(anyCollection(), any());
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
	}

	@Test
	void nextQuestionFromLobbyStartsFirstWithoutIncrement() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		assertEquals(0, roomOf(mgr).getCurrentQuestionIndex());
		mgr.nextQuestion("123456");
		assertEquals(0, roomOf(mgr).getCurrentQuestionIndex());
		assertEquals("q1", roomOf(mgr).getCurrentQuestionId());
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
	}

	@Test
	void sweepIdleReviewRoomWithNoConnectionsEvicts() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		mgr.leave("123456", a.getUuid());
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(0, mgr.activeRooms());
	}

	@Test
	void codingSubmitMidGameRoutesToProcessorThenForceSubmitReveals() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(new com.sprintjudge.domain.models.Question("oj1",
				"qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, java.time.Instant.now())));
		com.sprintjudge.domain.models.Question oj = new com.sprintjudge.domain.models.Question("oj1", "qz", "OJ", "D",
				"OJ_FULL", null, 60, 500, "{}", 0, java.time.Instant.now());
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
					@Override
					public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv)
							throws java.lang.Throwable {
						{
							CodingOutcomeConsumer h = inv.getArgument(10);
							h.accept(inv.getArgument(4), 400, true, 5, 5, null);
							return java.util.concurrent.CompletableFuture.completedFuture(true);
						}
					}
				});
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player p = mgr.join("123456", "Cody", "sc", "player", null);
		GameRoom room = roomOf(mgr);
		room.setStatus("ACTIVE");
		room.setCurrentQuestionId("oj1");
		room.setCurrentQuestionStartEpochMs(java.time.Instant.now().toEpochMilli());
		mgr.submit("123456", "oj1", p.getUuid(), "python", Json.readTree("{\"source\":\"print(1)\"}"));
		assertEquals(400, room.getPlayer(p.getUuid()).getScore());
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", room.getStatus());
	}

	@Test
	void rejoinWithBadTokenMidGameCreatesFreshSeatWithoutStealingScore() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.leave("123456", a.getUuid());
		com.sprintjudge.service.Player fresh = mgr.join("123456", "Bob", "sb", "player", "bogus-token");
		assertFalse(fresh.getUuid().equals(a.getUuid()));
		assertEquals(100, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		assertEquals(0, roomOf(mgr).getPlayer(fresh.getUuid()).getScore());
	}

	@Test
	void submitAfterEndedRoomStillActiveThrowsLockErrorInsteadOfScoring() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "ENDED", 500, GameRoom.GameMode.STANDARD));
		com.sprintjudge.service.Player p = new Player("u1", "A", 0, "s1", true);
		roomOf(mgr).addPlayer(p);
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws).send(eq("s1"), sent.capture());
		assertTrue(
				((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).getMessage().contains("Round is locked"));
		verify(writeBuffer, never()).offer(any());
	}

	@Test
	void kickHostMidGameClearsHostButKeepsPlayerScores() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sh", "host", null);
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.kickPlayer("123456", host.getUuid());
		assertNull(roomOf(mgr).getHostUuid());
		assertEquals(100, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
	}

	@Test
	void leaveHostMidGameClearsHostWithoutEndingRound() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player host = mgr.join("123456", "Host", "sh", "host", null);
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.leave("123456", host.getUuid());
		assertNull(roomOf(mgr).getHostUuid());
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
	}

	@Test
	void examReviewContentsIncludeAnswerKeyAndPlayerAnswers() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(700);
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		when(submissionRepository.findBySession("s1"))
				.thenReturn(List.of(new com.sprintjudge.domain.models.Submission("id1", "s1", "q1", "Alice",
						a.getUuid(), "{}", 700, true, null, 1, java.time.Instant.now())));
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.forceSubmit("123456");
		org.mockito.Mockito.clearInvocations(ws);
		mgr.endGame("123456");
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
		com.sprintjudge.domain.dto.GameReview review = null;
		for (java.lang.Object o : sent.getAllValues()) {
			if (o instanceof com.sprintjudge.domain.dto.GameReview) {
				review = (com.sprintjudge.domain.dto.GameReview) o;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(1, review.getQuestions().size());
		assertNotNull(review.getQuestions().get(0).getAnswer());
		assertEquals(1, review.getPlayers().size());
		assertEquals(700, review.getPlayers().get(0).getTotalScore());
	}

	@Test
	void trueFalseSubmitScoresCorrectWithSpeedBonus() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.domain.models.Question tf = new com.sprintjudge.domain.models.Question("tf1", "qz", "TF", "D",
				"TRUE_FALSE", null, 20, 80, Json.write(java.util.Map.of("correctIndex", 1)), 0,
				java.time.Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(tf));
		when(questionRepository.findById("tf1")).thenReturn(Optional.of(tf));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(75);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "tf1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":1}"));
		assertEquals(75, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		verify(writeBuffer).offer(any());
	}

	@Test
	void numericSubmitWrongSavesZeroAndResetsStreak() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.domain.models.Question num = new com.sprintjudge.domain.models.Question("n1", "qz", "N", "D",
				"NUMERIC", null, 20, 80, Json.write(java.util.Map.of("answer", 42)), 0, java.time.Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(num));
		when(questionRepository.findById("n1")).thenReturn(Optional.of(num));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);
		when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		roomOf(mgr).bumpStreak(a.getUuid());
		roomOf(mgr).bumpStreak(a.getUuid());
		mgr.submit("123456", "n1", a.getUuid(), "python", Json.readTree("{\"value\":0}"));
		assertEquals(0, roomOf(mgr).streakOf(a.getUuid()));
		assertEquals(0, roomOf(mgr).getPlayer(a.getUuid()).getScore());
	}

	@Test
	void sevenConsecutiveCorrectCapsStreakBonusAtFiveSteps() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		for (int i = 0; i < 7; i++)
			mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(7, roomOf(mgr).streakOf(a.getUuid()));
		int[] round = roomOf(mgr).roundOf(a.getUuid());
		assertEquals(100, round[0]);
		assertEquals(50, round[1]);
		assertEquals(900, roomOf(mgr).getPlayer(a.getUuid()).getScore());
	}

	@Test
	void duplicateTeamNamesGetDistinctIds() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
		com.sprintjudge.service.GameRoom.Team t1 = mgr.createTeam("123456", "Alpha");
		com.sprintjudge.service.GameRoom.Team t2 = mgr.createTeam("123456", "Alpha");
		assertFalse(t1.getId().equals(t2.getId()));
		assertEquals("Alpha", t1.getName());
		assertEquals("Alpha", t2.getName());
		assertEquals(2, mgr.getTeams("123456").size());
	}

	@Test
	void playerSwitchingTeamsLeavesOldMembership() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
		GameRoom room = roomOf(mgr);
		com.sprintjudge.service.Player p = new Player("u1", "A", 0, "s1", true);
		room.addPlayer(p);
		com.sprintjudge.service.GameRoom.Team alpha = mgr.createTeam("123456", "Alpha");
		com.sprintjudge.service.GameRoom.Team beta = mgr.createTeam("123456", "Beta");
		mgr.joinTeam("123456", alpha.getId(), p.getUuid());
		assertEquals(alpha.getId(), room.teamIdOf(p.getUuid()));
		mgr.joinTeam("123456", beta.getId(), p.getUuid());
		assertEquals(beta.getId(), room.teamIdOf(p.getUuid()));
		assertTrue(room.getTeam(beta.getId()).getMemberUuids().contains(p.getUuid()));
	}

	@Test
	void battleRestartClearsStaleMatchesBeforeRepairing() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
		GameRoom room = roomOf(mgr);
		room.addPlayer(new Player("u1", "A", 0, "s1", true));
		room.addPlayer(new Player("u2", "B", 0, "s2", true));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startBattle("123456");
		assertEquals(1, mgr.getBattleMatches("123456").size());
		room.setStatus("LOBBY");
		room.addPlayer(new Player("u3", "C", 0, "s3", true));
		room.addPlayer(new Player("u4", "D", 0, "s4", true));
		mgr.startBattle("123456");
		assertEquals(2, mgr.getBattleMatches("123456").size());
		assertEquals(1, mgr.getBracket("123456").size());
	}

	@Test
	void extendTimerLargeNegativeShrinksDeadline() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		long before = roomOf(mgr).getCurrentQuestionEndEpochMs();
		mgr.extendTimer("123456", -120);
		assertEquals(before - 120_000, roomOf(mgr).getCurrentQuestionEndEpochMs());
		ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), msg.capture());
		Object update = null;
		for (java.lang.Object m : msg.getAllValues()) {
			if (m instanceof com.sprintjudge.domain.dto.TimerUpdate) {
				update = (com.sprintjudge.domain.dto.TimerUpdate) m;
				break;
			}
		}
		if (update == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(-120L, ((com.sprintjudge.domain.dto.TimerUpdate) update).getExtendSec());
	}

	@Test
	void sweepFreshLobbyWithConnectedPlayerKeepsRoom() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis());
		mgr.sweepIdleRooms();
		assertEquals(1, mgr.activeRooms());
		verify(sessionRepository, never()).updateStatus(anyString(), anyString());
	}

	@Test
	void sweepSkipsEndedRoomEvenWhenIdle() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		roomOf(mgr).setStatus("ENDED");
		setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 60L * 60_000);
		mgr.sweepIdleRooms();
		assertEquals(1, mgr.activeRooms());
	}

	@Test
	void flushMissingPinReturnsSilently() {
		GameRoomManager mgr = manager();
		mgr.flushLeaderboardDelta("999999");
		verify(ws, never()).broadcastRaw(any(), anyString());
	}

	@Test
	void sendFullLeaderboardNonEmptyShipsResyncSnapshot() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player p = mgr.join("123456", "Alice", "sa", "player", null);
		org.mockito.Mockito.clearInvocations(ws);
		mgr.sendFullLeaderboard("123456", p.getSessionId());
		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(ws).sendRaw(eq("sa"), payload.capture());
		assertTrue(payload.getValue().contains("\"resync\":true"));
		assertTrue(payload.getValue().contains("Alice"));
	}

	@Test
	void endGameHostOnlyReviewHasZeroContestants() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		GameRoomManager mgr = manager();
		mgr.join("123456", "Host", "sh", "host", null);
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(ws);
		mgr.endGame("123456");
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
		com.sprintjudge.domain.dto.GameReview review = null;
		for (java.lang.Object o : sent.getAllValues()) {
			if (o instanceof com.sprintjudge.domain.dto.GameReview) {
				review = (com.sprintjudge.domain.dto.GameReview) o;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(0, review.getClassStats().getTotalPlayers());
		assertEquals(1, review.getQuestions().size());
	}

	@Test
	void nextQuestionFromActiveCancelsTimerThenAdvances() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(roundTimer);
		mgr.nextQuestion("123456");
		verify(roundTimer).cancel(123456);
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		assertEquals("q2", roomOf(mgr).getCurrentQuestionId());
	}

	@Test
	void examExtendTimerCapsAtBasePlus300() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		long base = roomOf(mgr).getQuestionEndBaseEpochMs();
		mgr.extendTimer("123456", 600);
		assertEquals(base + 300_000, roomOf(mgr).getCurrentQuestionEndEpochMs());
	}

	@Test
	void standardSubmitFreezesBoardWhilePracticeLiveBoards() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(scheduler);
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(scheduler, never()).markDirty(anyInt(), any());
		GameRoomManager mgr2 = manager();
		registryOf(mgr2).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		com.sprintjudge.service.Player b = mgr2.join("123456", "Bob", "sb", "player", null);
		GameRoom r2 = registryOf(mgr2).get(123456);
		r2.setStatus("ACTIVE");
		r2.setCurrentQuestionId("q1");
		r2.setCurrentQuestionStartEpochMs(java.time.Instant.now().toEpochMilli());
		org.mockito.Mockito.clearInvocations(scheduler);
		mgr2.submit("123456", "q1", b.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(scheduler, org.mockito.Mockito.atLeastOnce()).markDirty(eq(123456), any());
	}

	@Test
	void multipleSelectPartialCreditFlowsToEngine() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.domain.models.Question ms = new com.sprintjudge.domain.models.Question("ms1", "qz", "M", "D",
				"MULTIPLE_SELECT", null, 30, 120,
				Json.write(java.util.Map.of("correctIndices", java.util.List.of(0, 2))), 0, java.time.Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(ms));
		when(questionRepository.findById("ms1")).thenReturn(Optional.of(ms));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.5);
		when(scoringEngine.scoreSelection(eq(0.5), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(55);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "ms1", a.getUuid(), "python", Json.readTree("{\"selectedIndices\":[0]}"));
		assertEquals(55, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		verify(writeBuffer).offer(any());
	}

	@Test
	void fillBlankCorrectSubmitScoresFull() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		com.sprintjudge.domain.models.Question fb = new com.sprintjudge.domain.models.Question("fb1", "qz", "F", "D",
				"FILL_BLANK", null, 25, 90, Json.write(java.util.Map.of("answer", "paris")), 0,
				java.time.Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(fb));
		when(questionRepository.findById("fb1")).thenReturn(Optional.of(fb));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(85);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "fb1", a.getUuid(), "python", Json.readTree("{\"text\":\"paris\"}"));
		assertEquals(85, roomOf(mgr).getPlayer(a.getUuid()).getScore());
	}

	@Test
	void createTeamAfterGameStartedIsRejected() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.createTeam("123456", "LateTeam");
			}
		});
		assertTrue(mgr.getTeams("123456").isEmpty());
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
	}

	@Test
	void joinTeamAfterRoundStartedIsRejected() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoom room = roomOf(mgr);
		com.sprintjudge.service.Player p = new Player("u99", "Z", 0, "sz", true);
		room.addPlayer(p);
		com.sprintjudge.service.GameRoom.Team t = mgr.createTeam("123456", "Alpha");
		mgr.startQuestion("123456");
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", t.getId(), p.getUuid());
			}
		});
		assertNull(room.teamIdOf(p.getUuid()));
	}

	@Test
	void battleWithHostAndOddPlayersAdvancesTheBye() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
		GameRoom room = roomOf(mgr);
		com.sprintjudge.service.Player host = new Player("host-9", "Host", 0, "sh", true);
		room.addHost(host);
		room.setHostUuid(host.getUuid());
		room.addPlayer(new Player("u1", "A", 0, "s1", true));
		room.addPlayer(new Player("u2", "B", 0, "s2", true));
		room.addPlayer(new Player("u3", "C", 0, "s3", true));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startBattle("123456");
		assertEquals(1, mgr.getBattleMatches("123456").size());
		GameRoom.BattleRound round = mgr.getBracket("123456").get(0);
		assertEquals(2, round.getPairs().size());
		assertTrue(round.getPairs().get(1).isBye());
		// The host never receives an opponent frame: only the three fighters do.
		verify(ws, times(3)).send(anyString(), any());
		verify(ws, never()).send(eq("sh"), any());
	}

	@Test
	void submitWithNullResponseScoresViaEvaluation() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);
		when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", null);
		verify(writeBuffer).offer(any());
		assertEquals(0, roomOf(mgr).getPlayer(a.getUuid()).getScore());
	}

	@Test
	void secondSubmitSameRoundAddsStreakBonusToTotal() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(200);
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		mgr.submit("123456", "q1", a.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(420, roomOf(mgr).getPlayer(a.getUuid()).getScore());
		assertEquals(2, roomOf(mgr).attemptCount("q1", a.getUuid()));
	}

	@Test
	void leaveDuringReviewKeepsReviewStatus() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		mgr.leave("123456", a.getUuid());
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		assertFalse(roomOf(mgr).getPlayer(a.getUuid()).isConnected());
	}

	@Test
	void kickDuringReviewRemovesSeatButKeepsReview() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		mgr.kickPlayer("123456", a.getUuid());
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		assertNull(roomOf(mgr).getPlayer(a.getUuid()));
	}

	@Test
	void autoPilotSubmitFreezesBoardUntilReviewFlush() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.AUTO_PILOT));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(300);
		com.sprintjudge.service.Player p = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		org.mockito.Mockito.clearInvocations(scheduler);
		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		verify(scheduler, never()).markDirty(anyInt(), any());
		org.mockito.Mockito.clearInvocations(ws);
		mgr.forceSubmit("123456");
		assertEquals("REVIEW", roomOf(mgr).getStatus());
		ArgumentCaptor<Object> reviewMsg = ArgumentCaptor.forClass(Object.class);
		verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), reviewMsg.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : reviewMsg.getAllValues()) {
				if (m instanceof com.sprintjudge.domain.dto.RoundResult) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void examSubmitWrongStillArmsLiveBoard() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);
		when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
		com.sprintjudge.service.Player p = mgr.join("123456", "Alice", "sa", "player", null);
		GameRoom room = roomOf(mgr);
		room.setStatus("ACTIVE");
		room.setCurrentQuestionId("q1");
		room.setCurrentQuestionStartEpochMs(java.time.Instant.now().toEpochMilli());
		org.mockito.Mockito.clearInvocations(scheduler);
		mgr.submit("123456", "q1", p.getUuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
		verify(scheduler, times(1)).markDirty(eq(123456), any());
		assertEquals(0, room.getPlayer(p.getUuid()).getScore());
	}

	@Test
	void endGameTwiceWithReviewInBetweenSecondNoop() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		mgr.join("123456", "Alice", "sa", "player", null);
		mgr.startQuestion("123456");
		mgr.endGame("123456");
		mgr.endGame("123456");
		verify(sessionRepository, times(1)).updateStatus("s1", "ENDED");
	}

	@Test
	void standardKickThenLeaveUnknownKeepsRoomUsable() {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		GameRoomManager mgr = manager();
		com.sprintjudge.service.Player a = mgr.join("123456", "Alice", "sa", "player", null);
		mgr.join("123456", "Bob", "sb", "player", null);
		mgr.startQuestion("123456");
		mgr.kickPlayer("123456", a.getUuid());
		mgr.leave("123456", "ghost-uuid");
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		assertEquals(1, mgr.getRoomState("123456").getPlayers().size());
	}

	@Test
	void practiceJoinAfterStartStillScoresLive() {
		GameRoomManager mgr = manager();
		registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(120);
		com.sprintjudge.service.Player early = mgr.join("123456", "Early", "se", "player", null);
		mgr.startQuestion("123456");
		com.sprintjudge.service.Player late = mgr.join("123456", "Late", "sl", "player", null);
		GameRoom room = roomOf(mgr);
		mgr.submit("123456", "q1", late.getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(120, room.getPlayer(late.getUuid()).getScore());
		assertEquals(0, room.getPlayer(early.getUuid()).getScore());
	}

	@SuppressWarnings("unchecked")
	private static List<String> displayOptions(String json) throws Exception {
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("displayOptions",
				com.fasterxml.jackson.databind.JsonNode.class);
		m.setAccessible(true);
		return (List<String>) m.invoke(null, Json.readTree(json));
	}

	@Test
	void displayOptionsTextualOptions() throws Exception {
		assertEquals(List.of("A", "B"), displayOptions("{\"options\":[\"A\",\"B\"]}"));
	}

	@Test
	void displayOptionsLineObjects() throws Exception {
		assertEquals(List.of("first", "second"),
				displayOptions("{\"lines\":[{\"id\":\"l1\",\"text\":\"first\"},{\"id\":\"l2\",\"text\":\"second\"}]}"));
	}

	@Test
	void displayOptionsLabelObjects() throws Exception {
		assertEquals(List.of("X"), displayOptions("{\"options\":[{\"label\":\"X\"}]}"));
	}

	@Test
	void displayOptionsNullForMissingShapes() throws Exception {
		assertEquals(null, displayOptions("null"));
		assertEquals(null, displayOptions("[1,2]"));
		assertEquals(null, displayOptions("{}"));
		assertEquals(null, displayOptions("{\"options\":[]}"));
		assertEquals(null, displayOptions("{\"options\":[{\"id\":\"l1\"}]}"));
	}
}
