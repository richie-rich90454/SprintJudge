package com.sprintjudge.service;

import com.sprintjudge.domain.dto.ErrorMessage;
import com.sprintjudge.domain.dto.QuestionStart;
import com.sprintjudge.domain.dto.SubmissionResult;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRoomManagerModesTest {

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

    private Question mcq(String id) {
        return new Question(id, "qz", "T", "D", "MCQ", null, 30, 100,
                Json.write(Map.of("correctIndex", 0)), 0, Instant.now());
    }

    private RoomRegistry registryOf(GameRoomManager mgr) {
        try {
            var f = GameRoomManager.class.getDeclaredField("registry");
            f.setAccessible(true);
            return (RoomRegistry) f.get(mgr);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void putRoom(GameRoomManager mgr, GameRoom.GameMode mode) {
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, mode));
    }

    private void armRound(GameRoomManager mgr, String questionId) {
        GameRoom room = registryOf(mgr).get(123456);
        room.setStatus("ACTIVE");
        room.setCurrentQuestionId(questionId);
    }

    private Player addPlayer(GameRoomManager mgr, String name) {
        GameRoom room = registryOf(mgr).get(123456);
        Player p = new Player("uuid-" + name, name, 0, "sess-" + name, true);
        room.addPlayer(p);
        return p;
    }

    // ---------- TEAM guards ----------

    @Test
    void createTeamBlankNameThrows() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        assertThrows(IllegalArgumentException.class, () -> mgr.createTeam("123456", "  "));
    }

    @Test
    void createTeamNullNameThrows() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        assertThrows(IllegalArgumentException.class, () -> mgr.createTeam("123456", null));
    }

    @Test
    void createTeamHappyReturnsTrackedTeam() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        GameRoom.Team t = mgr.createTeam("123456", "Alpha");
        assertNotNull(t.id());
        assertEquals("Alpha", t.name());
        assertEquals(1, mgr.getTeams("123456").size());
    }

    @Test
    void joinTeamBlankTeamIdThrows() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", " ", "u1"));
    }

    @Test
    void joinTeamBlankPlayerUuidThrows() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", "team-1", null));
    }

    @Test
    void joinTeamUnknownPlayerThrows() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        GameRoom.Team t = mgr.createTeam("123456", "Alpha");
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", t.id(), "ghost"));
    }

    @Test
    void joinTeamUnknownTeamThrows() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        Player p = addPlayer(mgr, "Ann");
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", "team-999", p.uuid()));
    }

    @Test
    void joinTeamHappyAddsMember() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        Player p = addPlayer(mgr, "Ann");
        GameRoom.Team t = mgr.createTeam("123456", "Alpha");
        GameRoom.Team joined = mgr.joinTeam("123456", t.id(), p.uuid());
        assertTrue(joined.memberUuids().contains(p.uuid()));
        assertEquals(1, mgr.getTeams("123456").size());
    }

    @Test
    void teamModeSubmitFreezesPublicBoard() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.TEAM);
        Player p = addPlayer(mgr, "Ann");
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any()))
                .thenReturn(800);
        armRound(mgr, "q1");
        mgr.submit("123456", "q1", p.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(eq(p.sessionId()), sent.capture());
        assertTrue(sent.getValue() instanceof SubmissionResult);
        verify(scheduler, never()).markDirty(anyInt(), any());
    }

    // ---------- BATTLE ----------

    @Test
    void battleHappyPairsTwoPlayers() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.BATTLE);
        addPlayer(mgr, "Ann");
        addPlayer(mgr, "Bo");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
        mgr.startBattle("123456");
        assertEquals(1, mgr.getBattleMatches("123456").size());
        assertEquals(1, mgr.getBracket("123456").size());
        assertEquals("ACTIVE", mgr.getRoomState("123456").status());
    }

    @Test
    void battleOddPlayerSitsOutWithNotice() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.BATTLE);
        addPlayer(mgr, "Ann");
        addPlayer(mgr, "Bo");
        addPlayer(mgr, "Cy");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startBattle("123456");
        assertEquals(1, mgr.getBattleMatches("123456").size());
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, times(1)).send(anyString(), sent.capture());
        assertTrue(((ErrorMessage) sent.getValue()).message().contains("Odd player"));
    }

    @Test
    void battleSinglePlayerRejected() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.BATTLE);
        addPlayer(mgr, "Solo");
        assertThrows(IllegalStateException.class, () -> mgr.startBattle("123456"));
    }

    @Test
    void battleExcludesHostFromContestants() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.BATTLE);
        GameRoom room = registryOf(mgr).get(123456);
        Player host = new Player("host-1", "Host", 0, "sess-h", true);
        room.addHost(host);
        room.setHostUuid(host.uuid());
        Player a = addPlayer(mgr, "Ann");
        Player b = addPlayer(mgr, "Bo");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startBattle("123456");
        assertEquals(1, mgr.getBattleMatches("123456").size());
        var match = mgr.getBattleMatches("123456").get(0);
        assertTrue(match.p1Uuid().equals(a.uuid()) || match.p1Uuid().equals(b.uuid()));
        assertTrue(match.p2Uuid().equals(a.uuid()) || match.p2Uuid().equals(b.uuid()));
    }

    @Test
    void battleRequiresLobbyStatus() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.BATTLE);
        addPlayer(mgr, "Ann");
        addPlayer(mgr, "Bo");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        assertThrows(IllegalStateException.class, () -> mgr.startBattle("123456"));
    }

    @Test
    void battleWithNoQuestionsThrows() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.BATTLE);
        addPlayer(mgr, "Ann");
        addPlayer(mgr, "Bo");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> mgr.startBattle("123456"));
    }

    @Test
    void battleIndexPastLastQuestionThrows() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.BATTLE);
        addPlayer(mgr, "Ann");
        addPlayer(mgr, "Bo");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        registryOf(mgr).get(123456).setCurrentQuestionIndex(4);
        assertThrows(IllegalStateException.class, () -> mgr.startBattle("123456"));
    }

    // ---------- EXAM ----------

    @Test
    void examUsesPresetTotalEnd() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.EXAM);
        addPlayer(mgr, "Ann");
        long end = System.currentTimeMillis() + 120_000;
        registryOf(mgr).get(123456).setTotalEndEpochMs(end);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        assertEquals(end, registryOf(mgr).get(123456).currentQuestionEndEpochMs());
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        QuestionStart start = msg.getAllValues().stream()
                .filter(m -> m instanceof QuestionStart).map(m -> (QuestionStart) m)
                .findFirst().orElseThrow();
        assertEquals(120, start.timeLimitSec(), 5);
    }

    @Test
    void examComputesTotalFromQuestionsWhenUnset() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.EXAM);
        addPlayer(mgr, "Ann");
        Question slow = new Question("q2", "qz", "T", "D", "MCQ", null, 45, 100,
                Json.write(Map.of("correctIndex", 0)), 1, Instant.now());
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), slow));
        long before = System.currentTimeMillis();
        mgr.startQuestion("123456");
        long examEnd = registryOf(mgr).get(123456).currentQuestionEndEpochMs();
        assertTrue(examEnd - before >= 70_000 && examEnd - before <= 80_000);
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        QuestionStart start = msg.getAllValues().stream()
                .filter(m -> m instanceof QuestionStart).map(m -> (QuestionStart) m)
                .findFirst().orElseThrow();
        assertEquals(75, start.timeLimitSec());
    }

    @Test
    void examSubmitKeepsLiveBoard() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.EXAM);
        Player p = addPlayer(mgr, "Ann");
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any()))
                .thenReturn(700);
        armRound(mgr, "q1");
        mgr.submit("123456", "q1", p.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(eq(p.sessionId()), sent.capture());
        assertTrue(sent.getValue() instanceof SubmissionResult r && r.score() == 700);
        verify(scheduler, times(1)).markDirty(eq(123456), any());
    }

    @Test
    void examForceSubmitSkipsPublicBoardFlush() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.EXAM);
        addPlayer(mgr, "Ann");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        mgr.forceSubmit("123456");
        verify(sessionRepository).updateStatus("s1", "REVIEW");
        verify(scheduler, never()).markDirty(anyInt(), any());
    }

    // ---------- PRACTICE ----------

    @Test
    void practiceStartHasNoDeadline() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.PRACTICE);
        addPlayer(mgr, "Ann");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        assertEquals(Long.MAX_VALUE, registryOf(mgr).get(123456).currentQuestionEndEpochMs());
        assertEquals(Long.MAX_VALUE, registryOf(mgr).get(123456).questionEndBaseEpochMs());
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        QuestionStart start = msg.getAllValues().stream()
                .filter(m -> m instanceof QuestionStart).map(m -> (QuestionStart) m)
                .findFirst().orElseThrow();
        assertEquals(-1, start.timeLimitSec());
    }

    @Test
    void practiceWrongAnswerStillLiveBoards() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.PRACTICE);
        Player p = addPlayer(mgr, "Ann");
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);
        when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any()))
                .thenReturn(0);
        armRound(mgr, "q1");
        mgr.submit("123456", "q1", p.uuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(eq(p.sessionId()), sent.capture());
        assertTrue(sent.getValue() instanceof SubmissionResult r && !r.allPassed());
        verify(scheduler, times(1)).markDirty(eq(123456), any());
    }

    @Test
    void practiceForceSubmitSchedulesAutoAdvance() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.PRACTICE);
        addPlayer(mgr, "Ann");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        mgr.forceSubmit("123456");
        verify(roundTimer, times(1)).schedule(eq(123456), anyLong(), any());
    }

    // ---------- AUTO_PILOT ----------

    @Test
    void autoPilotStartUsesPerQuestionTimer() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.AUTO_PILOT);
        addPlayer(mgr, "Ann");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        QuestionStart start = msg.getAllValues().stream()
                .filter(m -> m instanceof QuestionStart).map(m -> (QuestionStart) m)
                .findFirst().orElseThrow();
        assertEquals(30, start.timeLimitSec());
        verify(roundTimer, times(1)).schedule(eq(123456), anyLong(), any());
    }

    @Test
    void autoPilotForceSubmitSchedulesAutoAdvance() {
        GameRoomManager mgr = manager();
        putRoom(mgr, GameRoom.GameMode.AUTO_PILOT);
        addPlayer(mgr, "Ann");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        mgr.forceSubmit("123456");
        verify(roundTimer, times(2)).schedule(eq(123456), anyLong(), any());
        assertEquals("REVIEW", mgr.getRoomState("123456").status());
    }

    // ---------- creation with explicit mode ----------

    @Test
    void createRoomWithTeamModePersistsMode() {
        when(quizRepository.findById("qz")).thenReturn(Optional.of(
                new Quiz("qz", "T", "", null, Instant.now(), false)));
        when(sessionRepository.findByPin(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.create(eq("qz"), eq("host"), anyString(), eq(null)))
                .thenAnswer(inv -> new GameSession("gen", "qz", inv.getArgument(2),
                        "host", "LOBBY", 0, null, null, null, Instant.now()));
        GameRoomManager mgr = manager();
        GameSession created = mgr.createRoom("qz", "host", GameRoom.GameMode.TEAM);
        assertEquals(GameRoom.GameMode.TEAM, registryOf(mgr).snapshot().get(0).gameMode());
        assertEquals(created.pinCode(), registryOf(mgr).snapshot().get(0).pin());
    }
}
