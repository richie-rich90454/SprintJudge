package com.sprintjudge.service;

import com.sprintjudge.domain.dto.BattleBracket;
import com.sprintjudge.domain.dto.BattleChampion;
import com.sprintjudge.domain.dto.BattleResult;
import com.sprintjudge.domain.dto.GameReview;
import com.sprintjudge.domain.dto.RoundResult;
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
class GameRoomTeamBattleFlowTest {

    @Mock QuizRepository quizRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock QuestionRepository questionRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock ScoringEngine scoringEngine;
    @Mock SubmissionProcessor submissionProcessor;
    private final org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> processorProvider =
            new org.springframework.beans.factory.ObjectProvider<>() {
                @Override public SubmissionProcessor getObject() { return submissionProcessor; }
            };
    @Mock WebSocketSessionManager ws;
    @Mock EvaluationService evaluationService;
    @Mock AdminSettingsService settingsService;
    @Mock BroadcastScheduler scheduler;
    @Mock SubmissionWriteBuffer writeBuffer;
    @Mock RoundTimeoutScheduler roundTimer;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    private GameRoomManager manager() {
        return new GameRoomManager(sessionRepository, quizRepository, questionRepository,
                submissionRepository, scoringEngine, processorProvider, ws,
                evaluationService, settingsService, scheduler, writeBuffer, roundTimer, eventPublisher);
    }

    private GameSession session(String pin, String mode) {
        return new GameSession("s1", "qz", pin, "host", "LOBBY", mode, 0, null, null, null, Instant.now());
    }

    private Question mcq(String id) {
        return new Question(id, "qz", "T" + id, "D", "MCQ", null, 30, 100,
                Json.write(Map.of("correctIndex", 0)), 0, Instant.now());
    }

    private GameRoom seeded(GameRoomManager mgr, GameRoom.GameMode mode, String... names) {
        try {
            var f = GameRoomManager.class.getDeclaredField("registry");
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
            var f = GameRoomManager.class.getDeclaredField("registry");
            f.setAccessible(true);
            return ((RoomRegistry) f.get(mgr)).get(123456);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubQuiz(int questions) {
        List<Question> qs = java.util.stream.IntStream.rangeClosed(1, questions)
                .mapToObj(i -> mcq("q" + i)).toList();
        lenient().when(questionRepository.findByQuiz("qz")).thenReturn(qs);
        for (Question q : qs) {
            lenient().when(questionRepository.findById(q.id())).thenReturn(Optional.of(q));
        }
    }

    // ---------- team room model ----------

    @Test
    void roomTeamsMoveBetweenTeamsAndLeaveCleanly() {
        GameRoom room = new GameRoom("s", "q", "p", "LOBBY");
        var a = room.createTeam("Alpha");
        var b = room.createTeam("Beta");
        room.joinTeam(a.id(), "u1");
        room.joinTeam(b.id(), "u1");
        assertEquals(b.id(), room.teamIdOf("u1"));
        assertFalse(room.getTeam(a.id()).memberUuids().contains("u1"));
        room.leaveTeam("u1");
        assertNull(room.teamIdOf("u1"));
        room.leaveTeam("nobody");
    }

    @Test
    void roomTeamsSortByScoreThenName() {
        GameRoom room = new GameRoom("s", "q", "p", "LOBBY");
        var slow = room.createTeam("Zulu");
        var fast = room.createTeam("Alpha");
        var tie = room.createTeam("Beta");
        room.applyTeamScore(fast.id(), 10);
        room.applyTeamScore(slow.id(), 5);
        room.applyTeamScore(tie.id(), 10);
        assertEquals(List.of("Alpha", "Beta", "Zulu"),
                room.teamsSorted().stream().map(GameRoom.Team::name).toList());
    }

    @Test
    void roomBattleStateLifecycle() {
        GameRoom room = new GameRoom("s", "q", "p", "LOBBY");
        assertEquals(0, room.battleRound());
        assertNull(room.currentBattleRound());
        assertNull(room.pairOf("u1"));
        room.resolveCurrentBattleRound(List.of());

        room.beginBattleRound(List.of(new GameRoom.BattlePair("u1", "u2", null, false)));
        assertEquals(1, room.battleRound());
        assertNotNull(room.currentBattleRound());
        assertEquals("u1", room.pairOf("u1").p1Uuid());
        assertNull(room.pairOf("u9"));
        assertNull(room.pairOf(null));

        room.resolveCurrentBattleRound(List.of(new GameRoom.BattlePair("u1", "u2", "u1", false)));
        assertEquals("u1", room.currentBattleRound().pairs().get(0).winnerUuid());

        room.setBattleWinners(List.of("u1"));
        assertEquals(List.of("u1"), room.battleWinners());
        room.setChampionUuid("u1");
        assertEquals("u1", room.championUuid());

        room.markBattleAnswer("u1", 42L);
        room.markBattleAnswer("u1", 99L);
        assertEquals(42L, room.battleAnswerMs("u1"));
        assertNull(room.battleAnswerMs("u2"));
        room.clearBattleAnswerTimes();
        assertNull(room.battleAnswerMs("u1"));

        room.resetBattle();
        assertTrue(room.bracketRounds().isEmpty());
        assertEquals(0, room.battleRound());
        assertNull(room.championUuid());
        assertTrue(room.battleWinners().isEmpty());
    }

    // ---------- team manager flow ----------

    @Test
    void managerTeamsAreLobbyOnlyAndValidated() {
        GameRoomManager mgr = manager();
        seeded(mgr, GameRoom.GameMode.TEAM, "Host");
        assertThrows(IllegalArgumentException.class, () -> mgr.createTeam("123456", null));
        assertThrows(IllegalArgumentException.class, () -> mgr.createTeam("123456", "  "));
        var team = mgr.createTeam("123456", "  Alpha  ");
        assertEquals("Alpha", team.name());

        var player = mgr.join("123456", "Alice", "sess-a", "player", null);
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", null, player.uuid()));
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", " ", player.uuid()));
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", team.id(), null));
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", team.id(), " "));
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", team.id(), "ghost"));
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", "ghost", player.uuid()));

        mgr.joinTeam("123456", team.id(), player.uuid());
        assertEquals(team.id(), roomOf(mgr).teamIdOf(player.uuid()));
        mgr.leaveTeam("123456", player.uuid());
        assertNull(roomOf(mgr).teamIdOf(player.uuid()));

        stubQuiz(1);
        mgr.startQuestion("123456");
        assertThrows(IllegalStateException.class, () -> mgr.createTeam("123456", "Late"));
        assertThrows(IllegalStateException.class, () -> mgr.joinTeam("123456", team.id(), player.uuid()));
        assertThrows(IllegalStateException.class, () -> mgr.leaveTeam("123456", player.uuid()));
    }

    @Test
    void managerSelectionScoreFeedsTheTeamTotal() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.TEAM, "Alice", "Bob");
        var team = mgr.createTeam("123456", "Alpha");
        List<Player> players = room.players();
        mgr.joinTeam("123456", team.id(), players.get(0).uuid());
        mgr.joinTeam("123456", team.id(), players.get(1).uuid());
        stubQuiz(1);
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any()))
                .thenReturn(900);
        mgr.startQuestion("123456");

        mgr.submit("123456", "q1", players.get(0).uuid(), "python",
                Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(900, mgr.getTeams("123456").get(0).score());
        mgr.submit("123456", "q1", players.get(1).uuid(), "python",
                Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(1800, mgr.getTeams("123456").get(0).score());

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), sent.capture());
        assertTrue(sent.getAllValues().stream()
                .anyMatch(m -> m instanceof Map<?, ?> map && "TEAM_LIST".equals(map.get("type"))));
    }

    @Test
    void managerSelectionScoreWithoutATeamIsIgnored() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.TEAM, "Solo");
        stubQuiz(1);
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any()))
                .thenReturn(500);
        mgr.startQuestion("123456");

        mgr.submit("123456", "q1", room.players().get(0).uuid(), "python",
                Json.readTree("{\"selectedIndex\":0}"));
        assertTrue(mgr.getTeams("123456").isEmpty());
    }

    @Test
    void managerCodingScoreFeedsTheTeamTotal() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.TEAM, "Cody");
        var team = mgr.createTeam("123456", "Alpha");
        Player cody = room.players().get(0);
        mgr.joinTeam("123456", team.id(), cody.uuid());
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(oj));
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        mgr.startQuestion("123456");
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));

        mgr.submit("123456", "oj1", cody.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));
        ArgumentCaptor<CodingOutcomeConsumer> cap = ArgumentCaptor.forClass(CodingOutcomeConsumer.class);
        verify(submissionProcessor).processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), cap.capture());
        cap.getValue().accept(cody.uuid(), 300, true, 3, 3, null);

        assertEquals(300, mgr.getTeams("123456").get(0).score());
    }

    @Test
    void teamReviewCarriesStandingsAndSoloReviewDoesNot() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.TEAM, "Alice");
        var team = mgr.createTeam("123456", "Alpha");
        mgr.joinTeam("123456", team.id(), room.players().get(0).uuid());
        stubQuiz(1);
        mgr.endGame("123456");

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), sent.capture());
        GameReview review = sent.getAllValues().stream()
                .filter(GameReview.class::isInstance).map(GameReview.class::cast)
                .findFirst().orElseThrow();
        assertEquals(1, review.teams().size());
        assertEquals("Alpha", review.teams().get(0).name());

        GameRoomManager solo = manager();
        seeded(solo, GameRoom.GameMode.STANDARD, "Solo");
        stubQuiz(1);
        solo.endGame("123456");
        ArgumentCaptor<Object> sentSolo = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), sentSolo.capture());
        GameReview soloReview = sentSolo.getAllValues().stream()
                .filter(GameReview.class::isInstance).map(GameReview.class::cast)
                .reduce((first, second) -> second).orElseThrow();
        assertTrue(soloReview.teams().isEmpty());
    }

    // ---------- battle flow ----------

    @Test
    void battlePairUpHandlesEvenAndOddFields() {
        assertEquals(2, GameRoomManager.pairUp(List.of("a", "b", "c", "d")).size());
        List<GameRoom.BattlePair> odd = GameRoomManager.pairUp(List.of("a", "b", "c"));
        assertEquals(2, odd.size());
        assertTrue(odd.get(1).bye());
        assertEquals("c", odd.get(1).winnerUuid());
        assertNull(odd.get(1).p2Uuid());
    }

    @Test
    void battleRequiresALobbyWithTwoPlayersAndAQuestion() {
        GameRoomManager mgr = manager();
        seeded(mgr, GameRoom.GameMode.BATTLE, "Only");
        stubQuiz(1);
        assertThrows(IllegalStateException.class, () -> mgr.startBattle("123456"));

        GameRoomManager empty = manager();
        seeded(empty, GameRoom.GameMode.BATTLE, "A", "B");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> empty.startBattle("123456"));

        GameRoomManager active = manager();
        seeded(active, GameRoom.GameMode.BATTLE, "A", "B");
        stubQuiz(1);
        active.startQuestion("123456");
        assertThrows(IllegalStateException.class, () -> active.startBattle("123456"));

        GameRoomManager past = manager();
        GameRoom room = seeded(past, GameRoom.GameMode.BATTLE, "A", "B");
        stubQuiz(1);
        room.setCurrentQuestionIndex(5);
        assertThrows(IllegalStateException.class, () -> past.startBattle("123456"));
    }

    @Test
    void battleWinnerAdvancesAndNextQuestionOpensTheNextRound() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B", "C", "D");
        stubQuiz(2);
        mgr.startBattle("123456");
        assertEquals(1, mgr.getBracket("123456").size());
        assertEquals(2, mgr.getBattleMatches("123456").size());

        List<Player> players = room.players();
        room.applyScore(players.get(0).uuid(), 100);
        room.recordRound(players.get(0).uuid(), 100, 0);
        room.applyScore(players.get(2).uuid(), 80);
        room.recordRound(players.get(2).uuid(), 80, 0);
        mgr.forceSubmit("123456");

        GameRoom.BattleRound round1 = mgr.getBracket("123456").get(0);
        assertEquals(players.get(0).uuid(), round1.pairs().get(0).winnerUuid());
        assertEquals(players.get(2).uuid(), round1.pairs().get(1).winnerUuid());
        assertEquals(2, room.battleWinners().size());

        mgr.nextQuestion("123456");
        assertEquals(2, mgr.getBracket("123456").size());
        GameRoom.BattleRound round2 = mgr.getBracket("123456").get(1);
        assertEquals(1, round2.pairs().size());

        room.applyScore(players.get(0).uuid(), 50);
        room.recordRound(players.get(0).uuid(), 50, 0);
        mgr.forceSubmit("123456");

        assertEquals(players.get(0).uuid(), room.championUuid());
        assertEquals("ENDED", room.status());
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), sent.capture());
        assertTrue(sent.getAllValues().stream().anyMatch(BattleChampion.class::isInstance));
        assertTrue(sent.getAllValues().stream().anyMatch(BattleResult.class::isInstance));
        assertTrue(sent.getAllValues().stream().anyMatch(RoundResult.class::isInstance));
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
        List<Player> p = room.players();
        room.recordRound(p.get(0).uuid(), roundA, 0);
        room.recordRound(p.get(1).uuid(), roundB, 0);
        if (totalA != 0) room.applyScore(p.get(0).uuid(), totalA);
        if (totalB != 0) room.applyScore(p.get(1).uuid(), totalB);
        if (timeA != null) room.markBattleAnswer(p.get(0).uuid(), timeA);
        if (timeB != null) room.markBattleAnswer(p.get(1).uuid(), timeB);
        mgr.forceSubmit("123456");
        GameRoom.BattlePair pair = room.currentBattleRound().pairs().get(0);
        return pair.winnerUuid().equals(p.get(0).uuid()) ? "p1" : "p2";
    }

    @Test
    void battleOddFieldGetsAByeAndAdvancesIt() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B", "C");
        stubQuiz(2);
        mgr.startBattle("123456");
        GameRoom.BattleRound round = mgr.getBracket("123456").get(0);
        assertEquals(2, round.pairs().size());
        assertTrue(round.pairs().get(1).bye());
        String byePlayer = round.pairs().get(1).p1Uuid();

        mgr.forceSubmit("123456");
        assertEquals(2, room.battleWinners().size());
        assertTrue(room.battleWinners().contains(byePlayer));

        mgr.nextQuestion("123456");
        GameRoom.BattleRound next = mgr.getBracket("123456").get(1);
        assertEquals(1, next.pairs().size());
        assertTrue(next.pairs().get(0).p1Uuid().equals(byePlayer)
                || next.pairs().get(0).p2Uuid().equals(byePlayer));
    }

    @Test
    void battleBroadcastSkipsPlayersWithoutASeatOrSession() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B", "C");
        stubQuiz(2);
        // A player who joins mid-battle is not in the round's pairs.
        mgr.startBattle("123456");
        Player late = mgr.join("123456", "Late", "sess-late", "player", null);
        assertNull(room.pairOf(late.uuid()));
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
        assertNull(room.championUuid());
    }

    @Test
    void battleCodingOutcomeMarksTheAnswerTime() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "Cody", "Rival");
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(oj));
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        mgr.startBattle("123456");
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        Player cody = room.players().get(0);

        mgr.submit("123456", "oj1", cody.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));
        ArgumentCaptor<CodingOutcomeConsumer> cap = ArgumentCaptor.forClass(CodingOutcomeConsumer.class);
        verify(submissionProcessor).processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), cap.capture());
        cap.getValue().accept(cody.uuid(), 100, true, 1, 1, null);

        assertNotNull(room.battleAnswerMs(cody.uuid()));
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
        assertEquals("ghost-1", room.championUuid());
    }

    @Test
    void battleChampionWithoutASeatStillEndsCleanly() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
        stubQuiz(1);
        mgr.startBattle("123456");
        room.beginBattleRound(List.of(new GameRoom.BattlePair("ghost-1", "ghost-2", null, false)));
        mgr.forceSubmit("123456");

        assertEquals("ghost-1", room.championUuid());
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), sent.capture());
        BattleChampion champion = sent.getAllValues().stream()
                .filter(BattleChampion.class::isInstance).map(BattleChampion.class::cast)
                .findFirst().orElseThrow();
        assertEquals("Player", champion.championName());
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
        assertEquals("ACTIVE", roomOf(mgr).status());
    }

    @Test
    void bracketViewShowsChampionNameOrFallback() {
        GameRoomManager mgr = manager();
        GameRoom room = seeded(mgr, GameRoom.GameMode.BATTLE, "A", "B");
        stubQuiz(1);
        BattleBracket empty = mgr.getBracketView("123456");
        assertNull(empty.championUuid());
        assertNull(empty.championName());

        String uuid = room.players().get(0).uuid();
        room.setChampionUuid(uuid);
        assertEquals(room.getPlayer(uuid).name(), mgr.getBracketView("123456").championName());

        room.setChampionUuid("ghost");
        assertEquals("ghost", mgr.getBracketView("123456").championUuid());
        assertNull(mgr.getBracketView("123456").championName());
    }

    @Test
    void battleGuardsRejectUnknownRooms() {
        GameRoomManager mgr = manager();
        assertThrows(IllegalArgumentException.class, () -> mgr.startBattle("000000"));
        assertThrows(IllegalArgumentException.class, () -> mgr.getBracket("000000"));
        assertThrows(IllegalArgumentException.class, () -> mgr.getBattleMatches("000000"));
        assertThrows(IllegalArgumentException.class, () -> mgr.getBracketView("000000"));
    }
}
