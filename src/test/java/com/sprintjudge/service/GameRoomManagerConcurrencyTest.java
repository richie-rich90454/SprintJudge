package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Submission;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Focused regression tests for the live-room concurrency fixes. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class GameRoomManagerConcurrencyTest {

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

	private final org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> processorProvider = new org.springframework.beans.factory.ObjectProvider<>() {
		@Override
		public SubmissionProcessor getObject() {
			return submissionProcessor;
		}
	};

	private GameRoomManager manager() {
		return new GameRoomManager(sessionRepository, quizRepository, questionRepository, submissionRepository,
				scoringEngine, processorProvider, ws, evaluationService, settingsService, scheduler, writeBuffer,
				roundTimer, eventPublisher);
	}

	private RoomRegistry registryOf(GameRoomManager mgr) throws Exception {
		java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("registry");
		f.setAccessible(true);
		return (RoomRegistry) f.get(mgr);
	}

	private GameRoom roomOf(GameRoomManager mgr) throws Exception {
		return registryOf(mgr).get(123456);
	}

	private ScheduledExecutorService sweeperOf(GameRoomManager mgr) throws Exception {
		java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("sweeper");
		f.setAccessible(true);
		return (ScheduledExecutorService) f.get(mgr);
	}

	private Question mcq(String id) {
		return new Question(id, "qz", "T", "D", "MCQ", null, 30, 100, Json.write(Map.of("correctIndex", 0)), 0,
				Instant.now());
	}

	private void seedRoom(GameRoomManager mgr) {
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(new GameSession("s1", "qz", "123456", "host",
				"LOBBY", "STANDARD", 0, null, null, null, Instant.now())));
		mgr.join("123456", "Seed", "seed-sess", "player", null);
	}

	private void scoreCorrectly() {
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
	}

	@Test
	void latePlusTimeAfterTimerFireDoesNotRevealRound() throws Exception {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		mgr.startQuestion("123456");
		ArgumentCaptor<Runnable> timer = ArgumentCaptor.forClass(Runnable.class);
		verify(roundTimer).schedule(eq(123456), anyLong(), timer.capture());

		// Occupy the single sweeper thread so +time can land after the timer
		// thread has already accepted the stale fire.
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		sweeperOf(mgr).execute(() -> {
			started.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(started.await(2, TimeUnit.SECONDS));
		roomOf(mgr).setCurrentQuestionEndEpochMs(System.currentTimeMillis() - 10_000);
		timer.getValue().run(); // timer thread: now >= end, enqueues the sweeper task
		mgr.extendTimer("123456", 300); // deadline moves into the future before the sweeper runs
		release.countDown();
		Thread.sleep(250);

		assertEquals("ACTIVE", roomOf(mgr).getStatus());
		verify(sessionRepository, never()).updateStatus("s1", "REVIEW");
		mgr.stopSweep();
	}

	@Test
	void statusFlipExposesQuestionAndDeadlineTogether() throws Exception {
		GameRoomManager mgr = manager();
		GameRoom room = new GameRoom("s1", "qz", "123456", "LOBBY", 100, GameRoom.GameMode.STANDARD);
		room.setCurrentQuestionIndex(1);
		room.addPlayer(new Player("u1", "Alice", 0, "s1", true));
		GameRoom spy = org.mockito.Mockito.spy(room);
		org.mockito.Mockito.doAnswer(invocation -> {
			invocation.callRealMethod();
			if ("ACTIVE".equals(invocation.getArgument(0))) {
				// Observe the round at the exact status flip, re-entrantly.
				mgr.submit("123456", "q2", "u1", "python", Json.readTree("{}"));
			}
			return null;
		}).when(spy).setStatus(anyString());
		registryOf(mgr).put(123456, spy);

		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
		when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
		scoreCorrectly();

		mgr.startQuestion("123456");

		// A half-started round gates this submit away; a fully started round scores it.
		verify(writeBuffer).offer(any(Submission.class));
		assertTrue(spy.getCurrentQuestionEndEpochMs() > System.currentTimeMillis());
		mgr.stopSweep();
	}

	@Test
	void submitHoldsRoomLockAgainstRacingSkip() throws Exception {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		GameRoom room = roomOf(mgr);
		String playerUuid = room.getPlayers().get(0).getUuid();
		room.setStatus("ACTIVE");
		room.setCurrentQuestionId("q1");
		room.setCurrentQuestionStartEpochMs(System.currentTimeMillis());
		room.setCurrentQuestionEndEpochMs(System.currentTimeMillis() + 60_000);
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
		when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
		scoreCorrectly();

		CountDownLatch evaluating = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(evaluationService.evaluateCorrectness(any(), any())).thenAnswer(invocation -> {
			evaluating.countDown();
			release.await(2, TimeUnit.SECONDS);
			return 1.0;
		});
		AtomicReference<Throwable> failure = new AtomicReference<>();
		JsonNode response = Json.readTree("{}");
		Thread submitter = new Thread(() -> {
			try {
				mgr.submit("123456", "q1", playerUuid, "python", response);
			} catch (Throwable t) {
				failure.set(t);
			}
		});
		Thread skipper = new Thread(() -> {
			try {
				mgr.forceSubmit("123456");
			} catch (Throwable t) {
				failure.set(t);
			}
		});
		submitter.start();
		assertTrue(evaluating.await(2, TimeUnit.SECONDS));
		skipper.start();
		Thread.sleep(150);
		assertEquals("ACTIVE", room.getStatus()); // skip cannot flip mid-submit
		verify(sessionRepository, never()).updateStatus("s1", "REVIEW");
		release.countDown();
		submitter.join(3000);
		skipper.join(3000);
		assertNull(failure.get());
		assertEquals("REVIEW", room.getStatus());
		verify(writeBuffer).offer(any(Submission.class));
		mgr.stopSweep();
	}

	@Test
	void endGameEvictsRoomAndRefusesLateJoin() throws Exception {
		GameRoomManager mgr = manager();
		seedRoom(mgr);
		mgr.endGame("123456");
		assertNull(registryOf(mgr).get(123456));
		assertEquals(0, mgr.activeRooms());
		// The DB row is ENDED by then, so a late socket cannot recreate the room.
		when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(new GameSession("s1", "qz", "123456", "host",
				"ENDED", "STANDARD", 0, null, null, null, Instant.now())));
		assertThrows(IllegalStateException.class, () -> mgr.join("123456", "Late", "late-sess", "player", null));
		mgr.stopSweep();
	}
}
