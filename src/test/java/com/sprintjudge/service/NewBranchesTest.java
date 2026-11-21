package com.sprintjudge.service;

import java.time.Instant;
import java.util.List;

import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.room.RoomRegistry;
import com.sprintjudge.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Covers branches introduced by the audit fixes. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class NewBranchesTest {

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
	ApplicationEventPublisher eventPublisher;

	private final org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> provider = new org.springframework.beans.factory.ObjectProvider<SubmissionProcessor>() {
		@Override
		public SubmissionProcessor getObject() {
			return submissionProcessor;
		}
	};

	private GameRoomManager manager() {
		return new GameRoomManager(sessionRepository, quizRepository, questionRepository, submissionRepository,
				scoringEngine, provider, ws, evaluationService, settingsService, scheduler, writeBuffer, roundTimer,
				eventPublisher);
	}

	private RoomRegistry registry(GameRoomManager mgr) {
		try {
			java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("registry");
			f.setAccessible(true);
			return (RoomRegistry) f.get(mgr);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void leaveIgnoresStaleSocketCloseAfterRejoin() {
		GameRoomManager mgr = manager();
		GameRoom room = new GameRoom("s", "q", "123456", "ACTIVE", 10);
		Player p = new Player("p1", "Alice", 0, "new-session", true, "token");
		room.addPlayer(p);
		room.applyScore("p1", 100);
		registry(mgr).put(123456, room);

		mgr.leave("123456", "p1", "old-session");
		assertTrue(room.getPlayer("p1").isConnected());
		assertEquals(100, room.getPlayer("p1").getScore());

		mgr.leave("123456", "p1", "new-session");
		assertEquals(false, room.getPlayer("p1").isConnected());
	}

	@Test
	void nextQuestionAfterGameEndIsNoOp() {
		GameRoomManager mgr = manager();
		GameRoom room = new GameRoom("s", "q", "123456", "ENDED", 10);
		registry(mgr).put(123456, room);
		mgr.nextQuestion("123456");
		assertEquals(0, room.getCurrentQuestionIndex());
	}

	@Test
	void leaveAndNextQuestionMissingRoomBranches() {
		GameRoomManager mgr = manager();
		mgr.leave("999999", "nobody", "sess");
	}

	@Test
	void leaveWithUnknownPlayerAndSessionIdIsSafe() {
		GameRoomManager mgr = manager();
		GameRoom room = new GameRoom("s", "q", "123456", "ACTIVE", 10);
		registry(mgr).put(123456, room);
		mgr.leave("123456", "ghost", "sess");
	}

	@Test
	void restoreWithNonNullQuestionsButOutOfRangeIndex() {
		GameRoomManager mgr = manager();
		GameSession session = new GameSession("sid4", "qz", "654324", "host", "ACTIVE", "STANDARD", 9, null, null, null,
				Instant.now());
		when(sessionRepository.findAllNonEnded()).thenReturn(List.of(session));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(new com.sprintjudge.domain.models.Question("q1",
				"qz", "Q", "D", "MCQ", null, 30, 100, "{}", 0, Instant.now())));
		mgr.restoreRooms();
		GameRoom room = registry(mgr).get(654324);
		assertNotNull(room);
		assertEquals(null, room.getCurrentQuestionId());
		assertEquals("ACTIVE", room.getStatus());
	}

	@Test
	void restoreWithNegativeQuestionIndex() {
		GameRoomManager mgr = manager();
		GameSession session = new GameSession("sid6", "qz", "654326", "host", "LOBBY", "STANDARD", -1, null, null, null,
				Instant.now());
		when(sessionRepository.findAllNonEnded()).thenReturn(List.of(session));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(new com.sprintjudge.domain.models.Question("q1",
				"qz", "Q", "D", "MCQ", null, 30, 100, "{}", 0, Instant.now())));
		mgr.restoreRooms();
		GameRoom room = registry(mgr).get(654326);
		assertNotNull(room);
		assertEquals(null, room.getCurrentQuestionId());
	}

	@Test
	void restoreNonActiveRoomKeepsStatusWithValidQuestion() {
		GameRoomManager mgr = manager();
		GameSession session = new GameSession("sid5", "qz", "654325", "host", "LOBBY", "STANDARD", 0, null, null, null,
				Instant.now());
		when(sessionRepository.findAllNonEnded()).thenReturn(List.of(session));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(new com.sprintjudge.domain.models.Question("q1",
				"qz", "Q", "D", "MCQ", null, 30, 100, "{}", 0, Instant.now())));
		mgr.restoreRooms();
		GameRoom room = registry(mgr).get(654325);
		assertNotNull(room);
		assertEquals("q1", room.getCurrentQuestionId());
		assertEquals("LOBBY", room.getStatus());
	}

	@Test
	void restoreHandlesOutOfRangeIndexAndNonActiveStatus() {
		GameRoomManager mgr = manager();
		GameSession session = new GameSession("sid3", "qz", "654323", "host", "LOBBY", "STANDARD", 9, null, null, null,
				Instant.now());
		when(sessionRepository.findAllNonEnded()).thenReturn(List.of(session));
		when(questionRepository.findByQuiz("qz")).thenReturn(null);
		mgr.restoreRooms();
		assertNotNull(registry(mgr).get(654323));
	}

	@Test
	void restoreSurvivesQuestionLookupFailure() {
		GameRoomManager mgr = manager();
		GameSession session = new GameSession("sid2", "qz", "654322", "host", "LOBBY", "STANDARD", 0, null, null, null,
				Instant.now());
		when(sessionRepository.findAllNonEnded()).thenReturn(List.of(session));
		when(questionRepository.findByQuiz("qz")).thenThrow(new RuntimeException("db down"));
		mgr.restoreRooms();
		assertNotNull(registry(mgr).get(654322));
	}

	@Test
	void restoreSetsCurrentQuestionAndMovesActiveToReview() {
		GameRoomManager mgr = manager();
		GameSession session = new GameSession("sid", "qz", "654321", "host", "ACTIVE", "STANDARD", 0, null, null, null,
				Instant.now());
		when(sessionRepository.findAllNonEnded()).thenReturn(List.of(session));
		when(submissionRepository.findBySession("sid")).thenReturn(List.of());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(new com.sprintjudge.domain.models.Question("q1",
				"qz", "Q", "D", "MCQ", null, 30, 100, "{}", 0, Instant.now())));
		mgr.restoreRooms();
		GameRoom room = registry(mgr).get(654321);
		assertNotNull(room);
		assertEquals("q1", room.getCurrentQuestionId());
		assertEquals("REVIEW", room.getStatus());
	}
}
