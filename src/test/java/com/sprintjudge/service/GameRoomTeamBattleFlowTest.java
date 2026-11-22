package com.sprintjudge.service;

import com.sprintjudge.domain.dto.BattleBracket;
import com.sprintjudge.domain.dto.BattleChampion;
import com.sprintjudge.domain.dto.BattleResult;
import com.sprintjudge.domain.dto.GameReview;
import com.sprintjudge.domain.dto.RoundResult;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class GameRoomTeamBattleFlowTest {

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

	private Question mcq(String id) {
		return new Question(id, "qz", "T" + id, "D", "MCQ", null, 30, 100, Json.write(Map.of("correctIndex", 0)), 0,
				Instant.now());
	}

	private GameRoom seeded(GameRoomManager mgr, String mode, String... names) {
		try {
			java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("registry");
			f.setAccessible(true);
			RoomRegistry registry = (RoomRegistry) f.get(mgr);
			registry.put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, mode));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		for (int i = 0; i < names.length; i++) {
			mgr.join("123456", names[i], "sess-" + i, "player", null);
		}
		return roomOf(mgr);
	}

	private GameRoom roomOf(GameRoomManager mgr) {
		try {
			java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("registry");
			f.setAccessible(true);
			return ((RoomRegistry) f.get(mgr)).get(123456);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void stubQuiz(int questions) {
		List<Question> qs = java.util.stream.IntStream.rangeClosed(1, questions)
				.mapToObj(new java.util.function.IntFunction<com.sprintjudge.domain.models.Question>() {
					@Override
					public com.sprintjudge.domain.models.Question apply(int i) {
						return mcq("q" + i);
					}
				}).toList();
		lenient().when(questionRepository.findByQuiz("qz")).thenReturn(qs);
		for (Question q : qs) {
			lenient().when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));
		}
	}

	// ---------- team room model ----------

	@Test
	void roomTeamsMoveBetweenTeamsAndLeaveCleanly() {
		GameRoom room = new GameRoom("s", "q", "p", "LOBBY");
		com.sprintjudge.service.GameRoom.Team a = room.createTeam("Alpha");
		com.sprintjudge.service.GameRoom.Team b = room.createTeam("Beta");
		room.joinTeam(a.getId(), "u1");
		room.joinTeam(b.getId(), "u1");
		assertEquals(b.getId(), room.teamIdOf("u1"));
		assertFalse(room.getTeam(a.getId()).getMemberUuids().contains("u1"));
		room.leaveTeam("u1");
		assertNull(room.teamIdOf("u1"));
		room.leaveTeam("nobody");
	}

	@Test
	void roomTeamsSortByScoreThenName() {
		GameRoom room = new GameRoom("s", "q", "p", "LOBBY");
		com.sprintjudge.service.GameRoom.Team slow = room.createTeam("Zulu");
		com.sprintjudge.service.GameRoom.Team fast = room.createTeam("Alpha");
		com.sprintjudge.service.GameRoom.Team tie = room.createTeam("Beta");
		room.applyTeamScore(fast.getId(), 10);
		room.applyTeamScore(slow.getId(), 5);
		room.applyTeamScore(tie.getId(), 10);
		{
			java.util.List<String> teamNames = new java.util.ArrayList<String>();
			for (GameRoom.Team t : room.teamsSorted()) {
				teamNames.add(t.getName());
			}
			assertEquals(List.of("Alpha", "Beta", "Zulu"), teamNames);
		}
	}

	@Test
	void roomBattleStateLifecycle() {
		GameRoom room = new GameRoom("s", "q", "p", "LOBBY");
		assertEquals(0, room.getBattleRound());
		assertNull(room.currentBattleRound());
		assertNull(room.pairOf("u1"));
		room.resolveCurrentBattleRound(List.of());

		room.beginBattleRound(List.of(new GameRoom.BattlePair("u1", "u2", null, false)));
		assertEquals(1, room.getBattleRound());
		assertNotNull(room.currentBattleRound());
		assertEquals("u1", room.pairOf("u1").getP1Uuid());
		assertNull(room.pairOf("u9"));
		assertNull(room.pairOf(null));

		room.resolveCurrentBattleRound(List.of(new GameRoom.BattlePair("u1", "u2", "u1", false)));
		assertEquals("u1", room.currentBattleRound().getPairs().get(0).getWinnerUuid());

		room.setBattleWinners(List.of("u1"));
		assertEquals(List.of("u1"), room.getBattleWinners());
		room.setChampionUuid("u1");
		assertEquals("u1", room.getChampionUuid());

		room.markBattleAnswer("u1", 42L);
		room.markBattleAnswer("u1", 99L);
		assertEquals(42L, room.battleAnswerMs("u1"));
		assertNull(room.battleAnswerMs("u2"));
		room.clearBattleAnswerTimes();
		assertNull(room.battleAnswerMs("u1"));

		room.resetBattle();
		assertTrue(room.getBracketRounds().isEmpty());
		assertEquals(0, room.getBattleRound());
		assertNull(room.getChampionUuid());
		assertTrue(room.getBattleWinners().isEmpty());
	}

	// ---------- team manager flow ----------

	@Test
	void managerTeamsAreLobbyOnlyAndValidated() {
		GameRoomManager mgr = manager();
		seeded(mgr, GameRoom.GameMode.TEAM, "Host");
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.createTeam("123456", null);
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.createTeam("123456", "  ");
			}
		});
		com.sprintjudge.service.GameRoom.Team team = mgr.createTeam("123456", "  Alpha  ");
		assertEquals("Alpha", team.getName());

		com.sprintjudge.service.Player player = mgr.join("123456", "Alice", "sess-a", "player", null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", null, player.getUuid());
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", " ", player.getUuid());
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", team.getId(), null);
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", team.getId(), " ");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", team.getId(), "ghost");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", "ghost", player.getUuid());
			}
		});

		mgr.joinTeam("123456", team.getId(), player.getUuid());
		assertEquals(team.getId(), roomOf(mgr).teamIdOf(player.getUuid()));
		mgr.leaveTeam("123456", player.getUuid());
		assertNull(roomOf(mgr).teamIdOf(player.getUuid()));

		stubQuiz(1);
		mgr.startQuestion("123456");
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.createTeam("123456", "Late");
			}
		});
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.joinTeam("123456", team.getId(), player.getUuid());
			}
		});
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.leaveTeam("123456", player.getUuid());
			}
		});
	}

	@Test
	void managerSelectionScoreFeedsTheTeamTotal() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.TEAM, "Alice", "Bob");
		com.sprintjudge.service.GameRoom.Team team = mgr.createTeam("123456", "Alpha");
		List<Player> players = room.getPlayers();
		mgr.joinTeam("123456", team.getId(), players.get(0).getUuid());
		mgr.joinTeam("123456", team.getId(), players.get(1).getUuid());
		stubQuiz(1);
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);
		mgr.startQuestion("123456");

		mgr.submit("123456", "q1", players.get(0).getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(900, mgr.getTeams("123456").get(0).getScore());
		mgr.submit("123456", "q1", players.get(1).getUuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
		assertEquals(1800, mgr.getTeams("123456").get(0).getScore());

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), sent.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object m : sent.getAllValues()) {
				if (m instanceof Map<?, ?> map && "TEAM_LIST".equals(map.get("type"))) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void managerSelectionScoreWithoutATeamIsIgnored() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.TEAM, "Solo");
		stubQuiz(1);
		when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
		when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(500);
		mgr.startQuestion("123456");

		mgr.submit("123456", "q1", room.getPlayers().get(0).getUuid(), "python",
				Json.readTree("{\"selectedIndex\":0}"));
		assertTrue(mgr.getTeams("123456").isEmpty());
	}

	@Test
	void managerCodingScoreFeedsTheTeamTotal() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.TEAM, "Cody");
		com.sprintjudge.service.GameRoom.Team team = mgr.createTeam("123456", "Alpha");
		Player cody = room.getPlayers().get(0);
		mgr.joinTeam("123456", team.getId(), cody.getUuid());
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(oj));
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		mgr.startQuestion("123456");
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));

		mgr.submit("123456", "oj1", cody.getUuid(), "python",
				Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));
		ArgumentCaptor<CodingOutcomeConsumer> cap = ArgumentCaptor.forClass(CodingOutcomeConsumer.class);
		verify(submissionProcessor).processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), cap.capture());
		cap.getValue().accept(cody.getUuid(), 300, true, 3, 3, null);

		assertEquals(300, mgr.getTeams("123456").get(0).getScore());
	}

	@Test
	void teamReviewCarriesStandingsAndSoloReviewDoesNot() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.TEAM, "Alice");
		com.sprintjudge.service.GameRoom.Team team = mgr.createTeam("123456", "Alpha");
		mgr.joinTeam("123456", team.getId(), room.getPlayers().get(0).getUuid());
		stubQuiz(1);
		mgr.endGame("123456");

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), sent.capture());
		GameReview review = null;
		for (java.lang.Object streamItem : sent.getAllValues()) {
			if (streamItem instanceof GameReview) {
				review = (GameReview) streamItem;
				break;
			}
		}
		if (review == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals(1, review.getTeams().size());
		assertEquals("Alpha", review.getTeams().get(0).getName());

		GameRoomManager solo = manager();
		seeded(solo, GameRoom.GameMode.STANDARD, "Solo");
		stubQuiz(1);
		solo.endGame("123456");
		ArgumentCaptor<Object> sentSolo = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), sentSolo.capture());
		GameReview soloReview = null;
		for (java.lang.Object streamItem : sentSolo.getAllValues()) {
			if (streamItem instanceof GameReview) {
				soloReview = (GameReview) streamItem;
			}
		}
		if (soloReview == null)
			throw new java.util.NoSuchElementException("No value present");
		assertTrue(soloReview.getTeams().isEmpty());
	}

	// ---------- battle flow ----------

	@Test
	void battlePairUpHandlesEvenAndOddFields() {
		assertEquals(2, GameRoomManager.pairUp(List.of("a", "b", "c", "d")).size());
		List<GameRoom.BattlePair> odd = GameRoomManager.pairUp(List.of("a", "b", "c"));
		assertEquals(2, odd.size());
		assertTrue(odd.get(1).isBye());
		assertEquals("c", odd.get(1).getWinnerUuid());
		assertNull(odd.get(1).getP2Uuid());
	}

	@Test
	void battleRequiresALobbyWithTwoPlayersAndAQuestion() {
		GameRoomManager mgr = manager();
		seeded(mgr, GameRoom.GameMode.BATTLE, "Only");
		stubQuiz(1);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.startBattle("123456");
			}
		});

		GameRoomManager empty = manager();
		seeded(empty, GameRoom.GameMode.BATTLE, "A", "B");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				empty.startBattle("123456");
			}
		});

		GameRoomManager active = manager();
		seeded(active, GameRoom.GameMode.BATTLE, "A", "B");
		stubQuiz(1);
		active.startQuestion("123456");
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				active.startBattle("123456");
			}
		});

		GameRoomManager past = manager();
		GameRoom room = seeded(past, GameRoom.GameMode.BATTLE, "A", "B");
		stubQuiz(1);
		room.setCurrentQuestionIndex(5);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				past.startBattle("123456");
			}
		});
	}

	@Test
	void battleWinnerAdvancesAndNextQuestionOpensTheNextRound() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B", "C", "D");
		stubQuiz(2);
		mgr.startBattle("123456");
		assertEquals(1, mgr.getBracket("123456").size());
		assertEquals(2, mgr.getBattleMatches("123456").size());

		List<Player> players = room.getPlayers();
		room.applyScore(players.get(0).getUuid(), 100);
		room.recordRound(players.get(0).getUuid(), 100, 0);
		room.applyScore(players.get(2).getUuid(), 80);
		room.recordRound(players.get(2).getUuid(), 80, 0);
		mgr.forceSubmit("123456");

		GameRoom.BattleRound round1 = mgr.getBracket("123456").get(0);
		assertEquals(players.get(0).getUuid(), round1.getPairs().get(0).getWinnerUuid());
		assertEquals(players.get(2).getUuid(), round1.getPairs().get(1).getWinnerUuid());
		assertEquals(2, room.getBattleWinners().size());

		mgr.nextQuestion("123456");
		assertEquals(2, mgr.getBracket("123456").size());
		GameRoom.BattleRound round2 = mgr.getBracket("123456").get(1);
		assertEquals(1, round2.getPairs().size());

		room.applyScore(players.get(0).getUuid(), 50);
		room.recordRound(players.get(0).getUuid(), 50, 0);
		mgr.forceSubmit("123456");

		assertEquals(players.get(0).getUuid(), room.getChampionUuid());
		assertEquals("ENDED", room.getStatus());
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), sent.capture());
		{
			boolean streamMatchResult = false;
			for (java.lang.Object streamItem : sent.getAllValues()) {
				if (streamItem instanceof BattleChampion) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		{
			boolean streamMatchResult = false;
			for (java.lang.Object streamItem : sent.getAllValues()) {
				if (streamItem instanceof BattleResult) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		{
			boolean streamMatchResult = false;
			for (java.lang.Object streamItem : sent.getAllValues()) {
				if (streamItem instanceof RoundResult) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void battleScoringTieBreaksByRoundThenTimeThenTotalThenOrder() {
		// Higher round score wins, both directions.
		assertEquals("p1", winnerOf(10, 5, null, null, 0, 0));
		assertEquals("p2", winnerOf(5, 10, null, null, 0, 0));
		// Equal round scores: the faster answer wins, both directions.
		assertEquals("p1", winnerOf(0, 0, 100L, 200L, 0, 0));
		assertEquals("p2", winnerOf(0, 0, 200L, 100L, 0, 0));
		// Equal times: total score decides, both directions.
		assertEquals("p1", winnerOf(0, 0, 100L, 100L, 500, 100));
		assertEquals("p2", winnerOf(0, 0, 100L, 100L, 100, 500));
		// One player never answered: total score still decides.
		assertEquals("p1", winnerOf(0, 0, 100L, null, 500, 100));
		// Perfect dead heat: the first-listed player advances.
		assertEquals("p1", winnerOf(0, 0, null, null, 0, 0));
	}

	/** Runs one two-player battle round with the given scoring inputs. */
	private String winnerOf(int roundA, int roundB, Long timeA, Long timeB, long totalA, long totalB) {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
		stubQuiz(1);
		mgr.startBattle("123456");
		List<Player> p = room.getPlayers();
		room.recordRound(p.get(0).getUuid(), roundA, 0);
		room.recordRound(p.get(1).getUuid(), roundB, 0);
		if (totalA != 0)
			room.applyScore(p.get(0).getUuid(), totalA);
		if (totalB != 0)
			room.applyScore(p.get(1).getUuid(), totalB);
		if (timeA != null)
			room.markBattleAnswer(p.get(0).getUuid(), timeA);
		if (timeB != null)
			room.markBattleAnswer(p.get(1).getUuid(), timeB);
		mgr.forceSubmit("123456");
		GameRoom.BattlePair pair = room.currentBattleRound().getPairs().get(0);
		return pair.getWinnerUuid().equals(p.get(0).getUuid()) ? "p1" : "p2";
	}

	@Test
	void battleOddFieldGetsAByeAndAdvancesIt() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B", "C");
		stubQuiz(2);
		mgr.startBattle("123456");
		GameRoom.BattleRound round = mgr.getBracket("123456").get(0);
		assertEquals(2, round.getPairs().size());
		assertTrue(round.getPairs().get(1).isBye());
		String byePlayer = round.getPairs().get(1).getP1Uuid();

		mgr.forceSubmit("123456");
		assertEquals(2, room.getBattleWinners().size());
		assertTrue(room.getBattleWinners().contains(byePlayer));

		mgr.nextQuestion("123456");
		GameRoom.BattleRound next = mgr.getBracket("123456").get(1);
		assertEquals(1, next.getPairs().size());
		assertTrue(next.getPairs().get(0).getP1Uuid().equals(byePlayer)
				|| next.getPairs().get(0).getP2Uuid().equals(byePlayer));
	}

	@Test
	void battleBroadcastSkipsPlayersWithoutASeatOrSession() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B", "C");
		stubQuiz(2);
		// A player who joins mid-battle is not in the round's pairs.
		mgr.startBattle("123456");
		Player late = mgr.join("123456", "Late", "sess-late", "player", null);
		assertNull(room.pairOf(late.getUuid()));
		// A seat with no live session cannot receive the opponent frame.
		mgr.join("123456", "Ghost", null, "player", null);

		mgr.forceSubmit("123456");
		mgr.nextQuestion("123456");

		verify(ws, never()).send(eq(null), any());
	}

	@Test
	void battleRoundWithoutPairingsIsANoOp() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
		stubQuiz(1);
		// Host pressed "Start round" through the plain question path.
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		assertNull(room.currentBattleRound());
		assertNull(room.getChampionUuid());
	}

	@Test
	void battleCodingOutcomeMarksTheAnswerTime() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "Cody", "Rival");
		Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(oj));
		when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
		mgr.startBattle("123456");
		when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), any()))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
		Player cody = room.getPlayers().get(0);

		mgr.submit("123456", "oj1", cody.getUuid(), "python",
				Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));
		ArgumentCaptor<CodingOutcomeConsumer> cap = ArgumentCaptor.forClass(CodingOutcomeConsumer.class);
		verify(submissionProcessor).processCoding(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyString(), anyString(), anyInt(), any(), anyLong(), cap.capture());
		cap.getValue().accept(cody.getUuid(), 100, true, 1, 1, null);

		assertNotNull(room.battleAnswerMs(cody.getUuid()));
	}

	@Test
	void battlePairWithAMissingOpponentAdvancesItsFirstPlayer() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
		stubQuiz(1);
		mgr.startBattle("123456");
		// A malformed legacy pair: not flagged as a bye but missing p2.
		room.beginBattleRound(List.of(new GameRoom.BattlePair("ghost-1", null, null, false)));
		mgr.forceSubmit("123456");
		assertEquals("ghost-1", room.getChampionUuid());
	}

	@Test
	void battleChampionWithoutASeatStillEndsCleanly() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
		stubQuiz(1);
		mgr.startBattle("123456");
		room.beginBattleRound(List.of(new GameRoom.BattlePair("ghost-1", "ghost-2", null, false)));
		mgr.forceSubmit("123456");

		assertEquals("ghost-1", room.getChampionUuid());
		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(ws, atLeastOnce()).broadcast(any(), sent.capture());
		BattleChampion champion = null;
		for (java.lang.Object streamItem : sent.getAllValues()) {
			if (streamItem instanceof BattleChampion) {
				champion = (BattleChampion) streamItem;
				break;
			}
		}
		if (champion == null)
			throw new java.util.NoSuchElementException("No value present");
		assertEquals("Player", champion.getChampionName());
	}

	@Test
	void battleRoundBroadcastSkipsASeatWithoutALiveSession() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
		room.addPlayer(new Player("shadow", "Shadow", 0, null, true));
		stubQuiz(1);
		mgr.startBattle("123456");
		// The seat is in a pair but cannot receive frames; its partner still can.
		verify(ws, never()).send(eq(null), any());
		assertNotNull(room.pairOf("shadow"));
	}

	@Test
	void battleStartRoundInLobbyStartsTheBattleInstead() {
		GameRoomManager mgr = manager();
		seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
		stubQuiz(2);
		mgr.nextQuestion("123456");
		assertNotNull(roomOf(mgr).currentBattleRound());
		assertEquals("ACTIVE", roomOf(mgr).getStatus());
	}

	@Test
	void bracketViewShowsChampionNameOrFallback() {
		GameRoomManager mgr = manager();
		GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
		stubQuiz(1);
		BattleBracket empty = mgr.getBracketView("123456");
		assertNull(empty.getChampionUuid());
		assertNull(empty.getChampionName());

		String uuid = room.getPlayers().get(0).getUuid();
		room.setChampionUuid(uuid);
		assertEquals(room.getPlayer(uuid).getName(), mgr.getBracketView("123456").getChampionName());

		room.setChampionUuid("ghost");
		assertEquals("ghost", mgr.getBracketView("123456").getChampionUuid());
		assertNull(mgr.getBracketView("123456").getChampionName());
	}

	@Test
	void battleGuardsRejectUnknownRooms() {
		GameRoomManager mgr = manager();
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.startBattle("000000");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.getBracket("000000");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.getBattleMatches("000000");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.getBracketView("000000");
			}
		});
	}

	// ---------- spectator mode ----------

	@Test
	void roomSpectatorsWatchWithoutASeat() {
		GameRoom room = new GameRoom("s", "q", "p", "LOBBY", 2, GameRoom.GameMode.STANDARD);
		room.addSpectator(new Player("v1", "Projector", 0, "sv1", true));
		assertEquals(1, room.getSpectators().size());
		assertEquals(1, room.connectedCount());
		room.removeSpectator("v1");
		room.removeSpectator(null);
		assertTrue(room.getSpectators().isEmpty());
		room.addPlayer(new Player("p1", "A", 0, "s1", true));
		room.addPlayer(new Player("p2", "B", 0, "s2", true));
		assertTrue(room.isFull());
		assertFalse(room.addSpectator(new Player("v2", "V", 0, "sv2", true)));
	}

	@Test
	void managerSpectatorJoinsAndLeavesWithoutScoring() {
		GameRoomManager mgr = manager();
		seeded(mgr, GameRoom.GameMode.STANDARD, "Alice");
		Player viewer = mgr.join("123456", "Projector", "sess-v", "spectator", null);
		assertEquals(1, roomOf(mgr).getSpectators().size());
		{
			boolean streamNoneResult = true;
			for (com.sprintjudge.domain.dto.RoomState.PlayerInfo p : mgr.getRoomState("123456").getPlayers()) {
				if (p.getUuid().equals(viewer.getUuid())) {
					streamNoneResult = false;
					break;
				}
			}
			assertTrue(streamNoneResult);
		}

		// Spectators receive the same broadcasts as players.
		mgr.join("123456", "Silent", null, "spectator", null);
		stubQuiz(1);
		mgr.startQuestion("123456");
		mgr.forceSubmit("123456");
		org.mockito.Mockito.verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(org.mockito.ArgumentMatchers
				.argThat(new org.mockito.ArgumentMatcher<java.util.Collection<java.lang.String>>() {
					@Override
					public boolean matches(java.util.Collection<java.lang.String> ids) {
						return ids != null && ids.contains("sess-v");
					}
				}), any());

		mgr.leave("123456", viewer.getUuid());
		{
			boolean streamNoneResult = true;
			for (com.sprintjudge.service.Player p : roomOf(mgr).getSpectators()) {
				if (p.getUuid().equals(viewer.getUuid())) {
					streamNoneResult = false;
					break;
				}
			}
			assertTrue(streamNoneResult);
		}
	}

	@Test
	void managerSpectatorIsRejectedWhenTheRoomIsFull() {
		GameRoomManager mgr = manager();
		try {
			java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("registry");
			f.setAccessible(true);
			((RoomRegistry) f.get(mgr)).put(123456,
					new GameRoom("s1", "qz", "123456", "LOBBY", 1, GameRoom.GameMode.STANDARD));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		mgr.join("123456", "Alice", "sess-a", "player", null);
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				mgr.join("123456", "Projector", "sess-v", "spectator", null);
			}
		});
	}
}
