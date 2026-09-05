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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
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

    @Test
    void createRoomRetriesRegistryCollision() {
        when(quizRepository.findById("qz")).thenReturn(Optional.of(
                new com.sprintjudge.domain.models.Quiz("qz", "T", "", null, Instant.now(), false)));
        when(sessionRepository.create(eq("qz"), eq("host-1"), anyString(), eq(null)))
                .thenAnswer(inv -> new GameSession("gen", "qz", inv.getArgument(2),
                        inv.getArgument(1), "LOBBY", 0, null, null, null, Instant.now()));
        GameRoomManager mgr = manager();
        try (var mocked = org.mockito.Mockito.mockStatic(com.sprintjudge.util.Ids.class)) {
            mocked.when(com.sprintjudge.util.Ids::pin).thenReturn("123456", "654321");
            // Pre-occupy the first pin so the generator loops exactly once.
            registryOf(mgr).put(123456,
                    new GameRoom("s9", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.STANDARD));
            GameSession created = mgr.createRoom("qz", "host-1");
            assertEquals("654321", created.pinCode());
        }
    }

    @Test
    void busyRejectToDepartedPlayerSendsNowhere() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        GameRoomManager mgr = manager();
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any()))
                .thenAnswer(inv -> {
                    mgr.kickPlayer("123456", player.uuid());
                    return java.util.concurrent.CompletableFuture.completedFuture(false);
                });
        armActive(mgr, "oj1");
        mgr.submit("123456", "oj1", player.uuid(), "python", Json.readTree("{\"source\":\"x\"}"));
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
                new com.sprintjudge.domain.models.Submission("s1", "s1", "q1", "A", "ua",
                        "{}", 900, true, null, 1, Instant.now()),
                new com.sprintjudge.domain.models.Submission("s2", "s1", "q2", "A", "ua",
                        "{}", 0, false, null, 1, Instant.now())));
        GameRoomManager mgr = manager();
        mgr.join("123456", "A", "sa", "player", null);
        mgr.endGame("123456");
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).broadcast(anyCollection(), sent.capture());
        Object review = sent.getAllValues().stream()
                .filter(o -> o instanceof com.sprintjudge.domain.dto.GameReview)
                .findFirst().orElseThrow();
        var gr = (com.sprintjudge.domain.dto.GameReview) review;
        assertEquals("q1", gr.classStats().easiestQuestionId());
        assertEquals("q2", gr.classStats().hardestQuestionId());
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
        room.softRemove(room.players().get(0).uuid());
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
        var player = mgr.join("123456", "A", "sa", "player", null);
        mgr.flushLeaderboardDelta("123456");
        mgr.kickPlayer("123456", player.uuid());
        org.mockito.Mockito.clearInvocations(ws);
        mgr.flushLeaderboardDelta("123456");
        verify(ws, never()).broadcastRaw(any(), anyString());
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
        var alice = mgr.join("123456", "Alice", "sa", "player", null);
        var bob = mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        assertEquals("q1", roomOf(mgr).currentQuestionId());
        mgr.submit("123456", "q1", alice.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", bob.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(100, roomOf(mgr).getPlayer(alice.uuid()).score());
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        assertEquals("q2", roomOf(mgr).currentQuestionId());
        mgr.submit("123456", "q2", alice.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q2", bob.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(210, roomOf(mgr).getPlayer(alice.uuid()).score());
        assertEquals(210, roomOf(mgr).getPlayer(bob.uuid()).score());
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        mgr.nextQuestion("123456");
        verify(sessionRepository).updateStatus("s1", "ENDED");
        assertThrows(IllegalArgumentException.class, () -> mgr.getRoomState("123456"));
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
        var a = mgr.join("123456", "A", "s1", "player", null);
        var b = mgr.join("123456", "B", "s2", "player", null);
        mgr.join("123456", "C", "s3", "player", null);
        mgr.join("123456", "D", "s4", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q3", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q3", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        var lb = roomOf(mgr).leaderboard();
        assertEquals(a.uuid(), lb.get(0).uuid());
        assertTrue(roomOf(mgr).getPlayer(a.uuid()).score() >= roomOf(mgr).getPlayer(b.uuid()).score());
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
        var alice = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.join("123456", "Bob", "sb", "player", null);
        when(submissionRepository.findBySession("s1")).thenReturn(List.of(
                new com.sprintjudge.domain.models.Submission("id1", "s1", "q1", "Alice", alice.uuid(), "{}", 100, true, null, 1, java.time.Instant.now()),
                new com.sprintjudge.domain.models.Submission("id2", "s1", "q2", "Alice", alice.uuid(), "{}", 110, true, null, 1, java.time.Instant.now())));
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", alice.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", alice.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        org.mockito.Mockito.clearInvocations(ws);
        mgr.nextQuestion("123456");
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
        Object review = sent.getAllValues().stream()
                .filter(o -> o instanceof com.sprintjudge.domain.dto.GameReview).findFirst().orElseThrow();
        var gr = (com.sprintjudge.domain.dto.GameReview) review;
        assertEquals(2, gr.questions().size());
        assertTrue(gr.players().stream().anyMatch(p -> p.totalScore() == 210));
        assertEquals(2, gr.classStats().totalQuestions());
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
        var alice = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", alice.uuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
        assertEquals(0, roomOf(mgr).streakOf(alice.uuid()));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", alice.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(1, roomOf(mgr).streakOf(alice.uuid()));
        assertEquals(100, roomOf(mgr).getPlayer(alice.uuid()).score());
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        var b = mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        org.mockito.Mockito.clearInvocations(scheduler);
        org.mockito.Mockito.clearInvocations(ws);
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        verify(scheduler, never()).markDirty(anyInt(), any());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        verify(sessionRepository).updateStatus("s1", "ENDED");
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
        assertTrue(sent.getAllValues().stream().anyMatch(o -> o instanceof com.sprintjudge.domain.dto.GameReview));
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(500, roomOf(mgr).getPlayer(a.uuid()).score());
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(1050, roomOf(mgr).getPlayer(a.uuid()).score());
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        var b = mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        verify(scheduler, org.mockito.Mockito.atLeast(3)).markDirty(eq(123456), any());
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        verify(roundTimer, org.mockito.Mockito.atLeastOnce()).schedule(eq(123456), anyLong(), any());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        assertEquals("q2", roomOf(mgr).currentQuestionId());
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(1890, roomOf(mgr).getPlayer(a.uuid()).score());
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
        assertEquals(0, roomOf(mgr).getPlayer(a.uuid()).score());
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
        assertEquals(0, roomOf(mgr).getPlayer(a.uuid()).score());
        assertEquals(0, roomOf(mgr).streakOf(a.uuid()));
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        var b = mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        verify(roundTimer, times(1)).schedule(eq(123456), anyLong(), any());
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(840, roomOf(mgr).getPlayer(a.uuid()).score());
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
        var p1 = new Player("uuid-A1", "A1", 0, "s-a1", true);
        var p2 = new Player("uuid-A2", "A2", 0, "s-a2", true);
        var p3 = new Player("uuid-B1", "B1", 0, "s-b1", true);
        room.addPlayer(p1); room.addPlayer(p2); room.addPlayer(p3);
        var alpha = mgr.createTeam("123456", "Alpha");
        var beta = mgr.createTeam("123456", "Beta");
        mgr.joinTeam("123456", alpha.id(), p1.uuid());
        mgr.joinTeam("123456", alpha.id(), p2.uuid());
        mgr.joinTeam("123456", beta.id(), p3.uuid());
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", p1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", p2.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", p3.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        room.applyTeamScore(alpha.id(), 200);
        room.applyTeamScore(alpha.id(), 200);
        room.applyTeamScore(beta.id(), 200);
        assertEquals(400, room.getTeam(alpha.id()).score());
        assertEquals(200, room.getTeam(beta.id()).score());
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", room.status());
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", p1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        room.applyTeamScore(alpha.id(), 220);
        assertEquals(620, room.getTeam(alpha.id()).score());
        assertEquals(2, mgr.getTeams("123456").size());
    }

    @Test
    void battleFullGameTwoPlayersPairsThenScoresThenEnds() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(300);
        GameRoom room = roomOf(mgr);
        var p1 = new Player("uuid-P1", "P1", 0, "s-p1", true);
        var p2 = new Player("uuid-P2", "P2", 0, "s-p2", true);
        room.addPlayer(p1); room.addPlayer(p2);
        mgr.startBattle("123456");
        assertEquals("ACTIVE", room.status());
        assertEquals(1, mgr.getBattleMatches("123456").size());
        assertEquals(1, mgr.getBracket("123456").size());
        mgr.submit("123456", "q1", p1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", p2.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(300, room.getPlayer(p1.uuid()).score());
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", room.status());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", room.status());
        mgr.submit("123456", "q2", p1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(630, room.getPlayer(p1.uuid()).score());
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        assertEquals(0, roomOf(mgr).currentQuestionIndex());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        assertEquals(1, roomOf(mgr).currentQuestionIndex());
        assertEquals("q2", roomOf(mgr).currentQuestionId());
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
        assertEquals("REVIEW", roomOf(mgr).status());
        verify(writeBuffer, never()).flush();
    }

    @Test
    void forceSubmitThenSubmitIsLockedWithRoundLockedError() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.forceSubmit("123456");
        org.mockito.Mockito.clearInvocations(ws);
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(eq("sa"), sent.capture());
        assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).message().contains("Round is locked"));
    }

    @Test
    void forceSubmitInLobbyIsIgnoredWithoutFlush() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.forceSubmit("123456");
        assertEquals("LOBBY", roomOf(mgr).status());
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        long examEnd = roomOf(mgr).currentQuestionEndEpochMs();
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
        assertEquals("q2", roomOf(mgr).currentQuestionId());
        assertTrue(roomOf(mgr).currentQuestionEndEpochMs() >= examEnd - 1000);
    }

    @Test
    void practiceForceSubmitMidRoundThenNextQuestionAutoSchedules() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        verify(roundTimer, times(1)).schedule(eq(123456), anyLong(), any());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", roomOf(mgr).status());
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
        var p = new Player("uuid-X", "X", 0, "sx", true);
        room.addPlayer(p);
        var t = mgr.createTeam("123456", "Alpha");
        mgr.joinTeam("123456", t.id(), p.uuid());
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", p.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", room.status());
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", room.status());
        assertEquals(1, mgr.getTeams("123456").size());
        assertEquals(t.id(), mgr.getTeams("123456").get(0).id());
    }

    @Test
    void battleForceSubmitMidRoundThenNextQuestionKeepsBracket() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(120);
        GameRoom room = roomOf(mgr);
        room.addPlayer(new Player("u1", "A", 0, "s1", true));
        room.addPlayer(new Player("u2", "B", 0, "s2", true));
        mgr.startBattle("123456");
        mgr.submit("123456", "q1", "u1", "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", room.status());
        int matchesBefore = mgr.getBattleMatches("123456").size();
        mgr.nextQuestion("123456");
        assertEquals("ACTIVE", room.status());
        assertEquals(matchesBefore, mgr.getBattleMatches("123456").size());
    }

    @Test
    void extendTimerTriplePlus300CapsAtBasePlus300AndThirdAppliesZero() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        long base = roomOf(mgr).questionEndBaseEpochMs();
        mgr.extendTimer("123456", 300);
        mgr.extendTimer("123456", 300);
        org.mockito.Mockito.clearInvocations(ws);
        mgr.extendTimer("123456", 300);
        assertEquals(base + 300_000, roomOf(mgr).currentQuestionEndEpochMs());
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, times(1)).broadcast(anyCollection(), msg.capture());
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
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
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
        assertEquals(roomOf(mgr).questionEndBaseEpochMs() + 300_000, roomOf(mgr).currentQuestionEndEpochMs());
    }

    @Test
    void extendTimerSinglePlus300AppliesFullExtension() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        long before = roomOf(mgr).currentQuestionEndEpochMs();
        mgr.extendTimer("123456", 300);
        assertEquals(before + 300_000, roomOf(mgr).currentQuestionEndEpochMs());
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), msg.capture());
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(300L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
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
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(60L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
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
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
    }

    @Test
    void extendTimerZeroSecondsBroadcastsZeroWithoutMovingDeadline() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        long before = roomOf(mgr).currentQuestionEndEpochMs();
        mgr.extendTimer("123456", 0);
        assertEquals(before, roomOf(mgr).currentQuestionEndEpochMs());
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), msg.capture());
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
    }

    @Test
    void kickMidRoundThenSubmitFromKickedSeatBurnsNoAttempt() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        mgr.kickPlayer("123456", a.uuid());
        org.mockito.Mockito.clearInvocations(ws, writeBuffer);
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        verify(writeBuffer, never()).offer(any());
        assertEquals(0, roomOf(mgr).attemptCount("q1", a.uuid()));
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        var b = mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        mgr.kickPlayer("123456", a.uuid());
        mgr.submit("123456", "q1", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(100, roomOf(mgr).getPlayer(b.uuid()).score());
        assertNull(roomOf(mgr).getPlayer(a.uuid()));
    }

    @Test
    void kickMidRoundRemovesBoardRowSoLeaderboardShrinks() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.join("123456", "Bob", "sb", "player", null);
        assertEquals(2, roomOf(mgr).leaderboard().size());
        mgr.startQuestion("123456");
        mgr.kickPlayer("123456", a.uuid());
        assertEquals(1, roomOf(mgr).leaderboard().size());
        assertEquals(1, mgr.getRoomState("123456").players().size());
    }

    @Test
    void leaveAllPlayersThenSweepEvictsIdleLobbyRoom() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        var b = mgr.join("123456", "Bob", "sb", "player", null);
        mgr.leave("123456", a.uuid());
        mgr.leave("123456", b.uuid());
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        var b = mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        mgr.leave("123456", a.uuid());
        mgr.leave("123456", b.uuid());
        setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
        mgr.sweepIdleRooms();
        assertEquals(0, mgr.activeRooms());
    }

    @Test
    void leaveOneOfTwoKeepsRoomAliveThroughSweep() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.join("123456", "Bob", "sb", "player", null);
        mgr.leave("123456", a.uuid());
        setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
        mgr.sweepIdleRooms();
        assertEquals(1, mgr.activeRooms());
    }

    @Test
    void battleOddPlayerOutSendsWaitingNoticeToLeftover() {
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
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, times(1)).send(anyString(), sent.capture());
        assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).message().contains("Odd player"));
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
        verify(ws, never()).send(anyString(), any());
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
        var p1 = new Player("u1", "A", 0, "s1", true);
        var p2 = new Player("u2", "B", 0, "s2", true);
        room.addPlayer(p1); room.addPlayer(p2);
        var t = mgr.createTeam("123456", "Alpha");
        mgr.joinTeam("123456", t.id(), p1.uuid());
        mgr.joinTeam("123456", t.id(), p2.uuid());
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", p1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", p2.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        room.applyTeamScore(t.id(), 100);
        room.applyTeamScore(t.id(), 100);
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", p1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q2", p2.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        room.applyTeamScore(t.id(), 110);
        room.applyTeamScore(t.id(), 110);
        assertEquals(420, room.getTeam(t.id()).score());
    }

    @Test
    void examSubmitArmsLiveBoardWhileReviewFlushStaysSuppressed() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(700);
        var p = mgr.join("123456", "Alice", "sa", "player", null);
        GameRoom room = roomOf(mgr);
        room.setStatus("ACTIVE");
        room.setCurrentQuestionId("q1");
        room.setCurrentQuestionStartEpochMs(java.time.Instant.now().toEpochMilli());
        org.mockito.Mockito.clearInvocations(scheduler);
        mgr.submit("123456", "q1", p.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
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
        var alice = mgr.join("123456", "Alice", "sa", "player", null);
        String token = alice.token();
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", alice.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(250, roomOf(mgr).getPlayer(alice.uuid()).score());
        mgr.leave("123456", alice.uuid());
        assertFalse(roomOf(mgr).getPlayer(alice.uuid()).connected());
        var back = mgr.join("123456", "Alice", "sa-new", "player", token);
        assertEquals(alice.uuid(), back.uuid());
        assertEquals("sa-new", back.sessionId());
        assertEquals(250, roomOf(mgr).getPlayer(alice.uuid()).score());
        assertEquals(1, roomOf(mgr).attemptCount("q1", alice.uuid()));
    }

    @Test
    void hostDoubleJoinRejectedWithAlreadyConnected() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Host", "sh", "host", null);
        assertThrows(IllegalStateException.class, () -> mgr.join("123456", "Host2", "sh2", "host", null));
        assertEquals(1, mgr.getRoomState("123456").players().size());
    }

    @Test
    void submitAfterEndedIsIgnoredWithoutScoring() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.endGame("123456");
        org.mockito.Mockito.clearInvocations(writeBuffer, ws);
        try {
            mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
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
        var a = mgr.join("123456", "A", "s1", "player", null);
        mgr.join("123456", "B", "s2", "player", null);
        mgr.join("123456", "C", "s3", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(210, roomOf(mgr).getPlayer(a.uuid()).score());
        assertEquals(0, roomOf(mgr).getPlayer(mgr.getRoomState("123456").players().get(1).uuid()).score());
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q3", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(3, roomOf(mgr).streakOf(a.uuid()));
        assertEquals(330, roomOf(mgr).getPlayer(a.uuid()).score());
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        for (int i = 0; i < 5; i++) mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(5, roomOf(mgr).attemptCount("q1", a.uuid()));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        assertEquals(0, roomOf(mgr).attemptCount("q2", a.uuid()));
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(1, roomOf(mgr).attemptCount("q2", a.uuid()));
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
        var host = mgr.join("123456", "Host", "sh", "host", null);
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
        var review = sent.getAllValues().stream().filter(o -> o instanceof com.sprintjudge.domain.dto.GameReview)
                .map(o -> (com.sprintjudge.domain.dto.GameReview) o).findFirst().orElseThrow();
        assertTrue(review.players().stream().noneMatch(p -> p.playerUuid().equals(host.uuid())));
        assertEquals(1, review.classStats().totalPlayers());
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
        var a = mgr.join("123456", "A", "s1", "player", null);
        var b = mgr.join("123456", "B", "s2", "player", null);
        var c = mgr.join("123456", "C", "s3", "player", null);
        var d = mgr.join("123456", "D", "s4", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", c.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", d.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q3", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
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
        var a = mgr.join("123456", "A", "s1", "player", null);
        var b = mgr.join("123456", "B", "s2", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q3", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
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
        var a1 = new Player("a1", "A1", 0, "s-a1", true);
        var a2 = new Player("a2", "A2", 0, "s-a2", true);
        var b1 = new Player("b1", "B1", 0, "s-b1", true);
        room.addPlayer(a1); room.addPlayer(a2); room.addPlayer(b1);
        var alpha = mgr.createTeam("123456", "Alpha");
        var beta = mgr.createTeam("123456", "Beta");
        mgr.joinTeam("123456", alpha.id(), a1.uuid());
        mgr.joinTeam("123456", alpha.id(), a2.uuid());
        mgr.joinTeam("123456", beta.id(), b1.uuid());
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", a2.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", b1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        room.applyTeamScore(alpha.id(), 200);
        room.applyTeamScore(beta.id(), 100);
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", a1.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        room.applyTeamScore(alpha.id(), 110);
        assertTrue(room.getTeam(alpha.id()).score() > room.getTeam(beta.id()).score());
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
        assertEquals(2, mgr.getBracket("123456").size());
        mgr.submit("123456", "q1", "u1", "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", "u3", "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        mgr.nextQuestion("123456");
        mgr.submit("123456", "q2", "u1", "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(168, room.getPlayer("u1").score());
    }

    @Test
    void extendTimerFivePlus60ThenPlus1CapsLastToZero() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        for (int i = 0; i < 5; i++) mgr.extendTimer("123456", 60);
        org.mockito.Mockito.clearInvocations(ws);
        mgr.extendTimer("123456", 60);
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, times(1)).broadcast(anyCollection(), msg.capture());
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
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
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(150L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
        org.mockito.Mockito.clearInvocations(ws);
        mgr.extendTimer("123456", 150);
        ArgumentCaptor<Object> msg2 = ArgumentCaptor.forClass(Object.class);
        verify(ws, times(1)).broadcast(anyCollection(), msg2.capture());
        Object capped = msg2.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(0L, ((com.sprintjudge.domain.dto.TimerUpdate) capped).extendSec());
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
        assertEquals("REVIEW", roomOf(mgr).status());
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
        assertEquals("ACTIVE", roomOf(mgr).status());
    }

    @Test
    void nextQuestionFromLobbyStartsFirstWithoutIncrement() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Alice", "sa", "player", null);
        assertEquals(0, roomOf(mgr).currentQuestionIndex());
        mgr.nextQuestion("123456");
        assertEquals(0, roomOf(mgr).currentQuestionIndex());
        assertEquals("q1", roomOf(mgr).currentQuestionId());
        assertEquals("ACTIVE", roomOf(mgr).status());
    }

    @Test
    void sweepIdleReviewRoomWithNoConnectionsEvicts() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        mgr.leave("123456", a.uuid());
        setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
        mgr.sweepIdleRooms();
        assertEquals(0, mgr.activeRooms());
    }

    @Test
    void codingSubmitMidGameRoutesToProcessorThenForceSubmitReveals() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(
                new com.sprintjudge.domain.models.Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, java.time.Instant.now())));
        com.sprintjudge.domain.models.Question oj = new com.sprintjudge.domain.models.Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, java.time.Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any()))
                .thenAnswer(inv -> {
                    CodingOutcomeConsumer h = inv.getArgument(10);
                    h.accept(inv.getArgument(4), 400, true, 5, 5, null);
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                });
        GameRoomManager mgr = manager();
        var p = mgr.join("123456", "Cody", "sc", "player", null);
        GameRoom room = roomOf(mgr);
        room.setStatus("ACTIVE");
        room.setCurrentQuestionId("oj1");
        room.setCurrentQuestionStartEpochMs(java.time.Instant.now().toEpochMilli());
        mgr.submit("123456", "oj1", p.uuid(), "python", Json.readTree("{\"source\":\"print(1)\"}"));
        assertEquals(400, room.getPlayer(p.uuid()).score());
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", room.status());
    }

    @Test
    void rejoinWithBadTokenMidGameCreatesFreshSeatWithoutStealingScore() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.leave("123456", a.uuid());
        var fresh = mgr.join("123456", "Bob", "sb", "player", "bogus-token");
        assertFalse(fresh.uuid().equals(a.uuid()));
        assertEquals(100, roomOf(mgr).getPlayer(a.uuid()).score());
        assertEquals(0, roomOf(mgr).getPlayer(fresh.uuid()).score());
    }

    @Test
    void submitAfterEndedRoomStillActiveThrowsLockErrorInsteadOfScoring() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "ENDED", 500, GameRoom.GameMode.STANDARD));
        var p = new Player("u1", "A", 0, "s1", true);
        roomOf(mgr).addPlayer(p);
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        mgr.submit("123456", "q1", p.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(eq("s1"), sent.capture());
        assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).message().contains("Round is locked"));
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
        var host = mgr.join("123456", "Host", "sh", "host", null);
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.kickPlayer("123456", host.uuid());
        assertNull(roomOf(mgr).hostUuid());
        assertEquals(100, roomOf(mgr).getPlayer(a.uuid()).score());
        assertEquals("ACTIVE", roomOf(mgr).status());
    }

    @Test
    void leaveHostMidGameClearsHostWithoutEndingRound() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        var host = mgr.join("123456", "Host", "sh", "host", null);
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.leave("123456", host.uuid());
        assertNull(roomOf(mgr).hostUuid());
        assertEquals("ACTIVE", roomOf(mgr).status());
    }

    @Test
    void examReviewContentsIncludeAnswerKeyAndPlayerAnswers() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(700);
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        when(submissionRepository.findBySession("s1")).thenReturn(List.of(
                new com.sprintjudge.domain.models.Submission("id1", "s1", "q1", "Alice", a.uuid(), "{}", 700, true, null, 1, java.time.Instant.now())));
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");
        org.mockito.Mockito.clearInvocations(ws);
        mgr.endGame("123456");
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), sent.capture());
        var review = sent.getAllValues().stream().filter(o -> o instanceof com.sprintjudge.domain.dto.GameReview)
                .map(o -> (com.sprintjudge.domain.dto.GameReview) o).findFirst().orElseThrow();
        assertEquals(1, review.questions().size());
        assertNotNull(review.questions().get(0).answer());
        assertEquals(1, review.players().size());
        assertEquals(700, review.players().get(0).totalScore());
    }

    @Test
    void trueFalseSubmitScoresCorrectWithSpeedBonus() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var tf = new com.sprintjudge.domain.models.Question("tf1", "qz", "TF", "D", "TRUE_FALSE", null, 20, 80,
                Json.write(java.util.Map.of("correctIndex", 1)), 0, java.time.Instant.now());
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(tf));
        when(questionRepository.findById("tf1")).thenReturn(Optional.of(tf));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(75);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "tf1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":1}"));
        assertEquals(75, roomOf(mgr).getPlayer(a.uuid()).score());
        verify(writeBuffer).offer(any());
    }

    @Test
    void numericSubmitWrongSavesZeroAndResetsStreak() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var num = new com.sprintjudge.domain.models.Question("n1", "qz", "N", "D", "NUMERIC", null, 20, 80,
                Json.write(java.util.Map.of("answer", 42)), 0, java.time.Instant.now());
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(num));
        when(questionRepository.findById("n1")).thenReturn(Optional.of(num));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);
        when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        roomOf(mgr).bumpStreak(a.uuid());
        roomOf(mgr).bumpStreak(a.uuid());
        mgr.submit("123456", "n1", a.uuid(), "python", Json.readTree("{\"value\":0}"));
        assertEquals(0, roomOf(mgr).streakOf(a.uuid()));
        assertEquals(0, roomOf(mgr).getPlayer(a.uuid()).score());
    }

    @Test
    void sevenConsecutiveCorrectCapsStreakBonusAtFiveSteps() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        for (int i = 0; i < 7; i++) mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(7, roomOf(mgr).streakOf(a.uuid()));
        int[] round = roomOf(mgr).roundOf(a.uuid());
        assertEquals(100, round[0]);
        assertEquals(50, round[1]);
        assertEquals(900, roomOf(mgr).getPlayer(a.uuid()).score());
    }

    @Test
    void duplicateTeamNamesGetDistinctIds() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
        var t1 = mgr.createTeam("123456", "Alpha");
        var t2 = mgr.createTeam("123456", "Alpha");
        assertFalse(t1.id().equals(t2.id()));
        assertEquals("Alpha", t1.name());
        assertEquals("Alpha", t2.name());
        assertEquals(2, mgr.getTeams("123456").size());
    }

    @Test
    void playerSwitchingTeamsLeavesOldMembership() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
        GameRoom room = roomOf(mgr);
        var p = new Player("u1", "A", 0, "s1", true);
        room.addPlayer(p);
        var alpha = mgr.createTeam("123456", "Alpha");
        var beta = mgr.createTeam("123456", "Beta");
        mgr.joinTeam("123456", alpha.id(), p.uuid());
        assertEquals(alpha.id(), room.teamIdOf(p.uuid()));
        mgr.joinTeam("123456", beta.id(), p.uuid());
        assertEquals(beta.id(), room.teamIdOf(p.uuid()));
        assertTrue(room.getTeam(beta.id()).memberUuids().contains(p.uuid()));
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
        assertEquals(2, mgr.getBracket("123456").size());
    }

    @Test
    void extendTimerLargeNegativeShrinksDeadline() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        long before = roomOf(mgr).currentQuestionEndEpochMs();
        mgr.extendTimer("123456", -120);
        assertEquals(before - 120_000, roomOf(mgr).currentQuestionEndEpochMs());
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), msg.capture());
        Object update = msg.getAllValues().stream()
                .filter(m -> m instanceof com.sprintjudge.domain.dto.TimerUpdate).findFirst().orElseThrow();
        assertEquals(-120L, ((com.sprintjudge.domain.dto.TimerUpdate) update).extendSec());
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
        var p = mgr.join("123456", "Alice", "sa", "player", null);
        org.mockito.Mockito.clearInvocations(ws);
        mgr.sendFullLeaderboard("123456", p.sessionId());
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
        var review = sent.getAllValues().stream().filter(o -> o instanceof com.sprintjudge.domain.dto.GameReview)
                .map(o -> (com.sprintjudge.domain.dto.GameReview) o).findFirst().orElseThrow();
        assertEquals(0, review.classStats().totalPlayers());
        assertEquals(1, review.questions().size());
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
        assertEquals("ACTIVE", roomOf(mgr).status());
        assertEquals("q2", roomOf(mgr).currentQuestionId());
    }

    @Test
    void examExtendTimerCapsAtBasePlus300() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        long base = roomOf(mgr).questionEndBaseEpochMs();
        mgr.extendTimer("123456", 600);
        assertEquals(base + 300_000, roomOf(mgr).currentQuestionEndEpochMs());
    }

    @Test
    void standardSubmitFreezesBoardWhilePracticeLiveBoards() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(100);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        org.mockito.Mockito.clearInvocations(scheduler);
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        verify(scheduler, never()).markDirty(anyInt(), any());
        GameRoomManager mgr2 = manager();
        registryOf(mgr2).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        var b = mgr2.join("123456", "Bob", "sb", "player", null);
        GameRoom r2 = registryOf(mgr2).get(123456);
        r2.setStatus("ACTIVE");
        r2.setCurrentQuestionId("q1");
        r2.setCurrentQuestionStartEpochMs(java.time.Instant.now().toEpochMilli());
        org.mockito.Mockito.clearInvocations(scheduler);
        mgr2.submit("123456", "q1", b.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        verify(scheduler, org.mockito.Mockito.atLeastOnce()).markDirty(eq(123456), any());
    }

    @Test
    void multipleSelectPartialCreditFlowsToEngine() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var ms = new com.sprintjudge.domain.models.Question("ms1", "qz", "M", "D", "MULTIPLE_SELECT", null, 30, 120,
                Json.write(java.util.Map.of("correctIndices", java.util.List.of(0, 2))), 0, java.time.Instant.now());
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(ms));
        when(questionRepository.findById("ms1")).thenReturn(Optional.of(ms));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.5);
        when(scoringEngine.scoreSelection(eq(0.5), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(55);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "ms1", a.uuid(), "python", Json.readTree("{\"selectedIndices\":[0]}"));
        assertEquals(55, roomOf(mgr).getPlayer(a.uuid()).score());
        verify(writeBuffer).offer(any());
    }

    @Test
    void fillBlankCorrectSubmitScoresFull() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var fb = new com.sprintjudge.domain.models.Question("fb1", "qz", "F", "D", "FILL_BLANK", null, 25, 90,
                Json.write(java.util.Map.of("answer", "paris")), 0, java.time.Instant.now());
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(fb));
        when(questionRepository.findById("fb1")).thenReturn(Optional.of(fb));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(85);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "fb1", a.uuid(), "python", Json.readTree("{\"text\":\"paris\"}"));
        assertEquals(85, roomOf(mgr).getPlayer(a.uuid()).score());
    }

    @Test
    void createTeamAfterGameStartedStillTracked() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        var t = mgr.createTeam("123456", "LateTeam");
        assertNotNull(t.id());
        assertEquals(1, mgr.getTeams("123456").size());
        assertEquals("ACTIVE", roomOf(mgr).status());
    }

    @Test
    void joinTeamAfterRoundStartedStillAllowed() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.TEAM));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoom room = roomOf(mgr);
        var p = new Player("u99", "Z", 0, "sz", true);
        room.addPlayer(p);
        var t = mgr.createTeam("123456", "Alpha");
        mgr.startQuestion("123456");
        var joined = mgr.joinTeam("123456", t.id(), p.uuid());
        assertTrue(joined.memberUuids().contains(p.uuid()));
    }

    @Test
    void battleWithHostAndOddPlayersNotifiesOnlyLeftover() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.BATTLE));
        GameRoom room = roomOf(mgr);
        var host = new Player("host-9", "Host", 0, "sh", true);
        room.addHost(host);
        room.setHostUuid(host.uuid());
        room.addPlayer(new Player("u1", "A", 0, "s1", true));
        room.addPlayer(new Player("u2", "B", 0, "s2", true));
        room.addPlayer(new Player("u3", "C", 0, "s3", true));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startBattle("123456");
        assertEquals(1, mgr.getBattleMatches("123456").size());
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, times(1)).send(anyString(), sent.capture());
        assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).message().contains("Odd player"));
    }

    @Test
    void submitWithNullResponseScoresViaEvaluation() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);
        when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", null);
        verify(writeBuffer).offer(any());
        assertEquals(0, roomOf(mgr).getPlayer(a.uuid()).score());
    }

    @Test
    void secondSubmitSameRoundAddsStreakBonusToTotal() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(200);
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", a.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(420, roomOf(mgr).getPlayer(a.uuid()).score());
        assertEquals(2, roomOf(mgr).attemptCount("q1", a.uuid()));
    }

    @Test
    void leaveDuringReviewKeepsReviewStatus() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        mgr.forceSubmit("123456");
        mgr.leave("123456", a.uuid());
        assertEquals("REVIEW", roomOf(mgr).status());
        assertFalse(roomOf(mgr).getPlayer(a.uuid()).connected());
    }

    @Test
    void kickDuringReviewRemovesSeatButKeepsReview() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        GameRoomManager mgr = manager();
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        mgr.forceSubmit("123456");
        mgr.kickPlayer("123456", a.uuid());
        assertEquals("REVIEW", roomOf(mgr).status());
        assertNull(roomOf(mgr).getPlayer(a.uuid()));
    }

    @Test
    void autoPilotSubmitFreezesBoardUntilReviewFlush() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.AUTO_PILOT));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(300);
        var p = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.startQuestion("123456");
        org.mockito.Mockito.clearInvocations(scheduler);
        mgr.submit("123456", "q1", p.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        verify(scheduler, never()).markDirty(anyInt(), any());
        org.mockito.Mockito.clearInvocations(ws);
        mgr.forceSubmit("123456");
        assertEquals("REVIEW", roomOf(mgr).status());
        ArgumentCaptor<Object> reviewMsg = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.atLeastOnce()).broadcast(anyCollection(), reviewMsg.capture());
        assertTrue(reviewMsg.getAllValues().stream().anyMatch(m -> m instanceof com.sprintjudge.domain.dto.RoundResult));
    }

    @Test
    void examSubmitWrongStillArmsLiveBoard() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.EXAM));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);
        when(scoringEngine.scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
        var p = mgr.join("123456", "Alice", "sa", "player", null);
        GameRoom room = roomOf(mgr);
        room.setStatus("ACTIVE");
        room.setCurrentQuestionId("q1");
        room.setCurrentQuestionStartEpochMs(java.time.Instant.now().toEpochMilli());
        org.mockito.Mockito.clearInvocations(scheduler);
        mgr.submit("123456", "q1", p.uuid(), "python", Json.readTree("{\"selectedIndex\":3}"));
        verify(scheduler, times(1)).markDirty(eq(123456), any());
        assertEquals(0, room.getPlayer(p.uuid()).score());
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
        var a = mgr.join("123456", "Alice", "sa", "player", null);
        mgr.join("123456", "Bob", "sb", "player", null);
        mgr.startQuestion("123456");
        mgr.kickPlayer("123456", a.uuid());
        mgr.leave("123456", "ghost-uuid");
        assertEquals("ACTIVE", roomOf(mgr).status());
        assertEquals(1, mgr.getRoomState("123456").players().size());
    }

    @Test
    void practiceJoinAfterStartStillScoresLive() {
        GameRoomManager mgr = manager();
        registryOf(mgr).put(123456, new GameRoom("s1", "qz", "123456", "LOBBY", 500, GameRoom.GameMode.PRACTICE));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(120);
        var early = mgr.join("123456", "Early", "se", "player", null);
        mgr.startQuestion("123456");
        var late = mgr.join("123456", "Late", "sl", "player", null);
        GameRoom room = roomOf(mgr);
        mgr.submit("123456", "q1", late.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(120, room.getPlayer(late.uuid()).score());
        assertEquals(0, room.getPlayer(early.uuid()).score());
    }
}
