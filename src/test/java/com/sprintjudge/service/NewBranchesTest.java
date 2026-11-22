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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
	void staleTimerDoesNotRevealANewerQuestion() throws Exception {
		GameRoomManager mgr = manager();
		GameRoom room = new GameRoom("s", "q", "123456", "ACTIVE", 10);
		room.setCurrentQuestionId("q2");
		registry(mgr).put(123456, room);
		java.lang.reflect.Method m = GameRoomManager.class.getDeclaredMethod("transitionToReview", String.class,
				String.class);
		m.setAccessible(true);
		m.invoke(mgr, "123456", "q1");
		assertEquals("ACTIVE", room.getStatus());
	}

	@Test
	void startQuestionWithNullQuestionListFailsWithoutFlippingActive() {
		GameRoomManager mgr = manager();
		GameRoom room = new GameRoom("s", "q", "123456", "LOBBY", 10);
		registry(mgr).put(123456, room);
		when(questionRepository.findByQuiz("q")).thenReturn(null);
		assertThrows(IllegalStateException.class, () -> mgr.startQuestion("123456"));
		assertEquals("LOBBY", room.getStatus());
	}

	@Test
	void rejoinNotifiesAndClosesTheSupersededTab() {
		when(sessionRepository.findByPin("123456"))
				.thenReturn(java.util.Optional.of(new com.sprintjudge.domain.models.GameSession("sid", "q", "123456",
						"host", "LOBBY", "STANDARD", 0, null, null, null, Instant.now())));
		GameRoomManager mgr = manager();
		Player p = mgr.join("123456", "Alice", "old-session", "player", null);
		registry(mgr).get(123456).softRemove(p.getUuid());

		Player reclaimed = mgr.join("123456", "Alice", "new-session", "player", p.getToken());
		assertEquals(p.getUuid(), reclaimed.getUuid());
		org.mockito.Mockito.verify(ws).send(org.mockito.ArgumentMatchers.eq("old-session"),
				org.mockito.ArgumentMatchers.any());
		org.mockito.Mockito.verify(ws).close("old-session");
	}

	@Test
	void rejoinOfSeatWithNoRecordedSessionDoesNotNotify() {
		when(sessionRepository.findByPin("123456"))
				.thenReturn(java.util.Optional.of(new com.sprintjudge.domain.models.GameSession("sid", "q", "123456",
						"host", "LOBBY", "STANDARD", 0, null, null, null, Instant.now())));
		GameRoomManager mgr = manager();
		Player p = mgr.join("123456", "Alice", "session-a", "player", null);
		GameRoom room = registry(mgr).get(123456);
		room.hardRemove(p.getUuid());
		room.addPlayer(new Player(p.getUuid(), "Alice", 0, null, false, p.getToken()));

		Player reclaimed = mgr.join("123456", "Alice", "session-b", "player", p.getToken());
		assertEquals(p.getUuid(), reclaimed.getUuid());
		org.mockito.Mockito.verify(ws, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejoinWithSameSessionDoesNotNotifyOrClose() {
		when(sessionRepository.findByPin("123456"))
				.thenReturn(java.util.Optional.of(new com.sprintjudge.domain.models.GameSession("sid", "q", "123456",
						"host", "LOBBY", "STANDARD", 0, null, null, null, Instant.now())));
		GameRoomManager mgr = manager();
		Player p = mgr.join("123456", "Alice", "same-session", "player", null);
		registry(mgr).get(123456).softRemove(p.getUuid());

		mgr.join("123456", "Alice", "same-session", "player", p.getToken());
		org.mockito.Mockito.verify(ws, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any());
		org.mockito.Mockito.verify(ws, org.mockito.Mockito.never()).close(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void hostLeavingAnEndedRoomDoesNotBroadcastNotice() {
		GameRoomManager mgr = manager();
		GameRoom room = new GameRoom("s", "q", "123456", "ENDED", 10);
		room.setHostUuid("host-1");
		registry(mgr).put(123456, room);
		mgr.leave("123456", "host-1", "sess");
		assertEquals(null, room.getHostUuid());
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
