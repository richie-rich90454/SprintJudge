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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-closure tests for the room manager's defensive arms: null/gone
 * seats, host guards, timer races, sweep matrix, flush healing and fan-out
 * filtering. Each test targets one JaCoCo-flagged site.
 */
@ExtendWith(MockitoExtension.class)
class GameRoomManagerCoverageTest {

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

    private GameSession session(String pin) {
        return new GameSession("s1", "qz", pin, "host", "LOBBY", 0, null, null, null, Instant.now());
    }

    private Question mcq(String id) {
        return new Question(id, "qz", "T", "D", "MCQ", null, 30, 100,
                Json.write(java.util.Map.of("correctIndex", 0)), 0, Instant.now());
    }

    private RoomRegistry registryOf(GameRoomManager mgr) {
        try {
            var f = GameRoomManager.class.getDeclaredField("registry");
            f.setAccessible(true);
            return (RoomRegistry) f.get(mgr);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private GameRoom roomOf(GameRoomManager mgr) {
        return registryOf(mgr).get(123456);
    }

    private void setField(Object obj, String name, Object value) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void armActive(GameRoomManager mgr, String questionId) {
        GameRoom room = roomOf(mgr);
        room.setStatus("ACTIVE");
        room.setCurrentQuestionId(questionId);
        room.setCurrentQuestionStartEpochMs(Instant.now().toEpochMilli());
    }

    private Object callPrivate(GameRoomManager mgr, String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = String.class;
        var m = GameRoomManager.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(mgr, args);
    }

    private void awaitStatus(GameRoomManager mgr, String pin, String want) throws Exception {
        RoomRegistry reg = registryOf(mgr);
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            GameRoom r = reg.get(Integer.parseInt(pin));
            if (r != null && want.equals(r.status())) return;
            Thread.sleep(50);
        }
        throw new AssertionError("room " + pin + " never reached " + want);
    }

    // ---------- createRoom pin-collision retry ----------

    @Test
    void createRoomRetriesOnPinCollision() {
        when(quizRepository.findById("qz")).thenReturn(Optional.of(
                new com.sprintjudge.domain.models.Quiz("qz", "T", "", null, Instant.now(), false)));
        when(sessionRepository.findByPin(anyString()))
                .thenReturn(Optional.of(session("123456")))
                .thenReturn(Optional.empty());
        when(sessionRepository.create(eq("qz"), eq("host-1"), anyString(), eq(null)))
                .thenAnswer(inv -> new GameSession("gen", "qz", inv.getArgument(2),
                        inv.getArgument(1), "LOBBY", 0, null, null, null, Instant.now()));
        GameSession created = manager().createRoom("qz", "host-1");
        assertTrue(created.pinCode().matches("\\d{6}"));
        verify(sessionRepository).create(eq("qz"), eq("host-1"), anyString(), eq(null));
    }

    // ---------- capacity ----------

    @Test
    void playerJoinWhenFullThrows() throws Exception {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        var f = GameRoomManager.class.getDeclaredField("maxPlayers");
        f.setAccessible(true);
        f.setInt(mgr, 1);
        mgr.join("123456", "Host", "sess-h", "host", null);
        assertThrows(IllegalStateException.class,
                () -> mgr.join("123456", "Extra", "s1", "player", null));
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
        var player = mgr.join("123456", "Alice", "sess-1", "player", null);
        GameRoom real = roomOf(mgr);
        GameRoom spy = new GameRoom(real.sessionId(), real.quizId(), "123456", "LOBBY", 500,
                GameRoom.GameMode.STANDARD) {
            int calls;
            @Override public Player getPlayer(String uuid) {
                if (++calls == 1) return super.getPlayer(uuid);
                return null;
            }
        };
        for (Player p : real.players()) {
            spy.addPlayer(new Player(p.uuid(), p.name(), 0, "sess-1", true, "tok"));
        }
        spy.setStatus("ACTIVE");
        spy.setCurrentQuestionId("q1");
        spy.setCurrentQuestionStartEpochMs(Instant.now().toEpochMilli());
        registryOf(mgr).put(123456, spy);
        mgr.submit("123456", "q1", player.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        verify(writeBuffer, never()).offer(any());
    }

    @Test
    void coderVanishingBeforeRunIsIgnored() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        GameRoomManager mgr = manager();
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        GameRoom real = roomOf(mgr);
        GameRoom spy = new GameRoom(real.sessionId(), real.quizId(), "123456", "LOBBY", 500,
                GameRoom.GameMode.STANDARD) {
            int calls;
            @Override public Player getPlayer(String uuid) {
                if (++calls == 1) return super.getPlayer(uuid);
                return null;
            }
        };
        for (Player p : real.players()) {
            spy.addPlayer(new Player(p.uuid(), p.name(), 0, "sess-c", true, "tok"));
        }
        spy.setStatus("ACTIVE");
        spy.setCurrentQuestionId("oj1");
        spy.setCurrentQuestionStartEpochMs(Instant.now().toEpochMilli());
        registryOf(mgr).put(123456, spy);
        mgr.submit("123456", "oj1", player.uuid(), "python", Json.readTree("{\"source\":\"x\"}"));
        verify(submissionProcessor, never()).processCoding(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyInt(), any(), anyLong(), any());
    }

    @Test
    void handlerAcceptToDepartedPlayerSendsNowhere() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        GameRoomManager mgr = manager();
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any()))
                .thenAnswer(inv -> {
                    CodingOutcomeConsumer h = inv.getArgument(10);
                    mgr.kickPlayer("123456", player.uuid());
                    h.accept(player.uuid(), 100, true, 1, 1, null);
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                });
        armActive(mgr, "oj1");
        mgr.submit("123456", "oj1", player.uuid(), "python", Json.readTree("{\"source\":\"x\"}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, times(1)).send(eq((String) null), sent.capture());
        assertTrue(sent.getValue() instanceof SubmissionResult);
    }

    @Test
    void handlerRejectedToDepartedPlayerSendsNowhere() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        GameRoomManager mgr = manager();
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any()))
                .thenAnswer(inv -> {
                    CodingOutcomeConsumer h = inv.getArgument(10);
                    mgr.kickPlayer("123456", player.uuid());
                    h.rejected(player.uuid());
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                });
        armActive(mgr, "oj1");
        mgr.submit("123456", "oj1", player.uuid(), "python", Json.readTree("{\"source\":\"x\"}"));
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
        assertThrows(IllegalStateException.class, () -> mgr.startBattle("123456"));
    }

    // ---------- timer expiry ----------

    @Test
    void practiceExpiryIsIgnored() throws Exception {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456,
                new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
        mgr.join("123456", "A", "sa", "player", null);
        GameRoom room = roomOf(mgr);
        room.setStatus("ACTIVE");
        room.setCurrentQuestionEndEpochMs(Instant.now().toEpochMilli() - 1000);
        callPrivate(mgr, "onTimerExpired", "123456");
        assertEquals("ACTIVE", room.status());
        verify(roundTimer, never()).schedule(anyInt(), anyLong(), any());
    }

    @Test
    void asyncExamExpiryFailureIsContained() throws Exception {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456,
                new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
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
        assertEquals("LOBBY", roomOf(mgr).status());
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
        roomOf(mgr).softRemove(roomOf(mgr).players().get(0).uuid());
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
        roomOf(mgr).softRemove(roomOf(mgr).players().get(0).uuid());
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
        var player = mgr.join("123456", "A", "sa", "player", null);
        doAnswer(inv -> {
            roomOf(mgr).applyScore(player.uuid(), 5);
            return null;
        }).when(ws).broadcastRaw(any(), any());
        mgr.flushLeaderboardDelta("123456");
        verify(scheduler, times(2)).markDirty(eq(123456), any());
    }

    @Test
    void nullSessionFilteredFromFanout() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "A", "sa", "player", null);
        roomOf(mgr).addPlayer(new Player("ghost", "G", 0, null, true, "tok"));
        mgr.flushLeaderboardDelta("123456");
        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(ws).broadcastRaw(ids.capture(), anyString());
        assertEquals(List.of("sa"), List.copyOf(ids.getValue()));
    }
}
