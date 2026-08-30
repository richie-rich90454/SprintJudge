package com.sprintjudge.service;

import com.sprintjudge.domain.dto.ErrorMessage;
import com.sprintjudge.domain.dto.QuestionStart;
import com.sprintjudge.domain.dto.RoomState;
import com.sprintjudge.domain.dto.RoundResult;
import com.sprintjudge.domain.dto.SubmissionResult;
import com.sprintjudge.domain.dto.TimerUpdate;
import com.sprintjudge.service.CodingOutcomeConsumer;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.service.GameRoom;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRoomManagerTest {

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
    @Captor ArgumentCaptor<java.util.Collection<String>> ids;
    @Captor ArgumentCaptor<Runnable> runnableCaptor;

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
                Json.write(Map.of("correctIndex", 0)), 0, Instant.now());
    }

    /** Lifecycle tests need a live room in the manager's registry before driving rounds. */
    private void seedRoom(GameRoomManager mgr) {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        lenient().when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
        mgr.join("123456", "Seed", "seed-sess", "player", null);
    }

    /** Puts the room into an ACTIVE round for the given question so submit() clears its lock gate. */
    private void armRound(GameRoomManager mgr, String questionId) {
        try {
            var f = GameRoomManager.class.getDeclaredField("registry");
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
        assertThrows(IllegalArgumentException.class,
                () -> manager().join("000000", "Alice", "sess-1", "player", null));
        verify(ws, never()).broadcast(any(), any());
    }

    @Test
    void joinSanitizesHostileNicknames() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Player p = manager().join("123456", "<script>x</script>", "sess", "player", null);
        assertTrue(p.name().matches("[A-Za-z0-9 _\\-]*"));
    }

    @Test
    void joinRecreatesRoomWhenEvictedFromRegistry() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        var p = mgr.join("123456", "Alice", "sess-1", "player", null);
        assertNotNull(p);
        assertEquals(1, mgr.activeRooms());
    }

    @Test
    void joinToEndedGameThrows() {
        GameSession ended = new GameSession("s1", "qz", "123456", "host", "ENDED", 0, null, null, null, Instant.now());
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(ended));
        assertThrows(IllegalStateException.class,
                () -> manager().join("123456", "Alice", "sess-1", "player", null));
    }

    @Test
    void joinWithOnlyInvalidCharsFallsBackToPlayer() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var p = manager().join("123456", "###", "sess-1", "player", null);
        assertEquals("Player", p.name());
    }

    @Test
    void secondHostIsRejected() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Host", "sess-h", "host", null);
        assertThrows(IllegalStateException.class,
                () -> mgr.join("123456", "Impostor", "sess-i", "host", null));
    }

    /** Regression: a join must arm the coalescer so its score-0 delta ships. */
    @Test
    void joinMarksLeaderboardDirty() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        manager().join("123456", "Alice", "sess-a", "player", null);
        verify(scheduler).markDirty(eq(123456), any());
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
        assertThrows(IllegalStateException.class,
                () -> mgr.join("123456", "Extra", "s4", "player", null));
    }

    // ---------- selection submit ----------

    @Test
    void submitScoresSelectionAndSaves() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);

        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

        var player = mgr.join("123456", "Alice", "sess-1", "player", null);
        armRound(mgr, "q1");
        mgr.submit("123456", "q1", player.uuid(), "python",
                Json.readTree("{\"selectedIndex\":0}"));

        ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved =
                ArgumentCaptor.forClass(com.sprintjudge.domain.models.Submission.class);
        verify(writeBuffer).offer(saved.capture());
        assertEquals(900, saved.getValue().scoreEarned());
        assertTrue(saved.getValue().correct());

        // submit does not broadcast ROOM_STATE immediately; the leaderboard delta is coalesced.
        verify(ws, never()).broadcast(any(), any());
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(2)).markDirty(eq(123456), task.capture());   // join + submit
        task.getAllValues().get(1).run();

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
    void wrongSelectionSavesZeroAndStillMarksDirty() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.0);


        var player = mgr.join("123456", "Bob", "sess-b", "player", null);
        armRound(mgr, "q1");
        mgr.submit("123456", "q1", player.uuid(), "python",
                Json.readTree("{\"selectedIndex\":3}"));

        ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved =
                ArgumentCaptor.forClass(com.sprintjudge.domain.models.Submission.class);
        verify(writeBuffer).offer(saved.capture());
        assertEquals(0, saved.getValue().scoreEarned());
        assertEquals(false, saved.getValue().correct());
        // Fraction flows into the engine, which hard-zeroes it internally.
        verify(scoringEngine).scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), anyInt(), any());
        verify(scheduler, atLeastOnce()).markDirty(eq(123456), any());
    }

    @Test
    void partialCreditScalesTheRawSpeedScore() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        String multi = Json.write(Map.of("correctIndices", List.of(0, 1)));
        when(questionRepository.findById("m1")).thenReturn(Optional.of(
                new Question("m1", "qz", "M", "D", "MULTIPLE_SELECT", null, 30, 100, multi, 0, Instant.now())));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.75);

        when(scoringEngine.scoreSelection(eq(0.75), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(800);

        var player = mgr.join("123456", "Cara", "sess-p", "player", null);
        armRound(mgr, "m1");
        mgr.submit("123456", "m1", player.uuid(), "python",
                Json.readTree("{\"selectedIndices\":[0]}"));

        ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved =
                ArgumentCaptor.forClass(com.sprintjudge.domain.models.Submission.class);
        verify(writeBuffer).offer(saved.capture());
        assertEquals(800, saved.getValue().scoreEarned());
        assertEquals(false, saved.getValue().correct());
    }

    @Test
    void codingSubmitsRouteToProcessorNotSelectionPath() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        armRound(mgr, "oj1");

        mgr.submit("123456", "oj1", player.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));

        verify(submissionProcessor).processCoding(eq("s1"), eq("123456"), eq("oj1"), eq("Cody"),
                eq(player.uuid()), eq("python"), eq("print(1)"), anyInt(), any(), anyLong(), any());
        verify(writeBuffer, never()).offer(any());
        verify(ws, never()).send(anyString(), any());
    }

    @Test
    void saturatedJudgeAnswersWithFriendlyRetry() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_PATCH", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Pat", "sess-j", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(false));
        armRound(mgr, "oj1");

        mgr.submit("123456", "oj1", player.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(eq(player.sessionId()), sent.capture());
        assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).message()
                .contains("Judge queue is busy"));
    }

    // ---------- question lifecycle ----------

    /** Regression: the first "Start round" must begin at question 0, not skip it. */
    @Test
    void nextQuestionFromLobbyStartsFirstQuestion() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1"), mcq("q2")));

        mgr.nextQuestion("123456");

        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        QuestionStart start = (QuestionStart) msg.getAllValues().stream()
                .filter(m -> m instanceof QuestionStart).findFirst().orElseThrow();
        assertEquals("q1", start.question().id());
        assertEquals("ACTIVE", mgr.getRoomState("123456").status());
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
        assertTrue(msg.getAllValues().stream().anyMatch(m -> m instanceof QuestionStart));
    }

    @Test
    void startQuestionWithoutQuizQuestionsFailsCleanly() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> mgr.startQuestion("123456"));
    }

    @Test
    void nextQuestionPastTheLastEndsGameAndEvictsRoom() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));

        mgr.startQuestion("123456");
        mgr.nextQuestion("123456");

        verify(sessionRepository).updateStatus("s1", "ENDED");
        assertThrows(IllegalArgumentException.class, () -> mgr.getRoomState("123456"));
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
        TimerUpdate update = (TimerUpdate) msg.getAllValues().stream()
                .filter(m -> m instanceof TimerUpdate).findFirst().orElseThrow();
        assertEquals(30, update.extendSec());
        assertTrue(update.newEndEpochMs() > System.currentTimeMillis());
    }

    @Test
    void forceSubmitFlushesBufferThenRevealsRoundResult() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        org.mockito.Mockito.clearInvocations(ws);

        mgr.forceSubmit("123456");

        verify(writeBuffer).flush();                       // accuracy boundary
        verify(sessionRepository).updateStatus("s1", "REVIEW");
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        assertTrue(msg.getAllValues().stream()
                .anyMatch(m -> m instanceof RoundResult r && "ROUND_RESULT".equals(r.type())));
    }

    @Test
    void kickRemovesPlayerAndNotifiesTheirSession() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var p = mgr.join("123456", "Bad", "sess-k", "player", null);
        org.mockito.Mockito.clearInvocations(ws);

        mgr.kickPlayer("123456", p.uuid());

        verify(ws).send(eq("sess-k"), any());
        assertEquals(0, mgr.getRoomState("123456").players().size());
    }

    @Test
    void leaveDropsDisconnectedPlayer() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var p = mgr.join("123456", "Ghost", "sess-g", "player", null);
        org.mockito.Mockito.clearInvocations(ws);

        mgr.leave("123456", p.uuid());

        var state = mgr.getRoomState("123456");
        assertEquals(1, state.players().size());
        assertTrue(state.players().stream().noneMatch(RoomState.PlayerInfo::connected));
    }

    // ---------- creation ----------

    @Test
    void createRoomGeneratesSixDigitPinAndPersistsLobby() {
        when(quizRepository.findById("qz")).thenReturn(Optional.of(
                new com.sprintjudge.domain.models.Quiz("qz", "T", "", null, Instant.now(), false)));
        lenient().when(sessionRepository.findByPin(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.create(eq("qz"), eq("host-1"), anyString(), eq(null)))
                .thenAnswer(inv -> new GameSession("gen", "qz", inv.getArgument(2),
                        inv.getArgument(1), "LOBBY", 0, null, null, null, Instant.now()));

        GameSession created = manager().createRoom("qz", "host-1");

        assertEquals("LOBBY", created.status());
        assertTrue(created.pinCode().matches("\\d{6}"));
    }

    // ---------- state shape ----------

    @Test
    void getRoomStateExposesStatusCountAndPlayers() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("a"), mcq("b")));
        mgr.join("123456", "A", "sa", "player", null);
        mgr.join("123456", "B", "sb", "player", null);

        var state = mgr.getRoomState("123456");

        assertEquals("ROOM_STATE", state.type());
        assertEquals("LOBBY", state.status());
        assertEquals(2, state.questionCount());
        assertEquals(2, state.players().size());
    }

    @Test
    void submitForUnknownPlayerIsIgnored() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));

        mgr.submit("123456", "q1", "ghost-uuid", "python",
                Json.readTree("{\"selectedIndex\":0}"));

        verify(writeBuffer, never()).offer(any());
    }

    @Test
    void resyncSendsAuthoritativeSnapshotToRequester() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var p = mgr.join("123456", "Sync", "sess-r", "player", null);
        org.mockito.Mockito.clearInvocations(ws);

        mgr.sendFullLeaderboard("123456", p.sessionId());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(ws).sendRaw(eq("sess-r"), payload.capture());
        assertTrue(payload.getValue().contains("LEADERBOARD_DELTA"));
        assertTrue(payload.getValue().contains("\"resync\":true"));
        assertTrue(payload.getValue().contains("Sync"));
    }

    // ---------- helpers for the new coverage tests ----------

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

    // ---------- join: room-recovery paths ----------

    @Test
    void joinRejectsEndedRoom() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(
                new GameSession("s1", "qz", "123456", "host", "ENDED", 0, null, null, null, Instant.now())));
        assertThrows(IllegalStateException.class,
                () -> manager().join("123456", "Alice", "sess", "player", null));
    }

    @Test
    void joinCreatesRoomWhenAbsentButSessionExists() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        Player p = mgr.join("123456", "Alice", "sess-a", "player", null);
        assertNotNull(p);
        assertDoesNotThrow(() -> mgr.getRoomState("123456"));
    }

    @Test
    void joinReclaimsDisconnectedSeatByToken() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        var p = mgr.join("123456", "Alice", "sess-a", "player", null);
        roomOf(mgr).softRemove(p.uuid());                 // simulate disconnect
        var reclaimed = mgr.join("123456", "Alice", "sess-b", "player", p.token());
        assertEquals(p.uuid(), reclaimed.uuid());
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
        assertEquals("Player", p.name());
    }

    // ---------- submit: lock / missing / limit branches ----------

    @Test
    void submitLockedRoundSendsErrorAndStops() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        var player = mgr.join("123456", "Alice", "sess-1", "player", null);
        // status is LOBBY (round not armed) -> lock gate fires
        mgr.submit("123456", "q1", player.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(anyString(), sent.capture());
        assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).message().contains("Round is locked"));
    }

    @Test
    void submitMissingQuestionIsIgnored() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var player = mgr.join("123456", "Alice", "sess-1", "player", null);
        armRound(mgr, "q1");
        // questionRepository.findById("q1") defaults to empty -> q == null -> return
        mgr.submit("123456", "q1", player.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        verify(writeBuffer, never()).offer(any());
        verify(ws, never()).send(anyString(), any());
    }

    @Test
    void submitAttemptLimitSendsError() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        var player = mgr.join("123456", "Alice", "sess-1", "player", null);
        armRound(mgr, "q1");
        // Pre-fill the per-(question,player) attempt counter to the cap.
        try {
            var af = GameRoom.class.getDeclaredField("attempts");
            af.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.concurrent.ConcurrentHashMap<String, Integer> map =
                    (java.util.concurrent.ConcurrentHashMap<String, Integer>) af.get(roomOf(mgr));
            map.put("q1 " + player.uuid(), 50);
        } catch (Exception e) { throw new RuntimeException(e); }
        mgr.submit("123456", "q1", player.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(anyString(), sent.capture());
        assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).message().contains("Attempt limit reached"));
    }

    @Test
    void twoConsecutiveCorrectAnswersTriggerStreakBonus() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), anyInt(), any())).thenReturn(900);

        var player = mgr.join("123456", "Alice", "sess-1", "player", null);
        armRound(mgr, "q1");
        mgr.submit("123456", "q1", player.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.submit("123456", "q1", player.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

        ArgumentCaptor<com.sprintjudge.domain.models.Submission> saved =
                ArgumentCaptor.forClass(com.sprintjudge.domain.models.Submission.class);
        verify(writeBuffer, org.mockito.Mockito.times(2)).offer(saved.capture());
        assertEquals(900, saved.getAllValues().get(0).scoreEarned());   // no streak yet
        assertEquals(990, saved.getAllValues().get(1).scoreEarned());   // +10% streak bonus
    }

    // ---------- submit: coding language gating ----------

    @Test
    void codingRejectsDisallowedLanguage() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL",
                java.util.List.of("java"), 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        armRound(mgr, "oj1");
        mgr.submit("123456", "oj1", player.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\"}"));
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(eq(player.sessionId()), sent.capture());
        assertTrue(((com.sprintjudge.domain.dto.ErrorMessage) sent.getValue()).message().contains("Language not allowed"));
        verify(submissionProcessor, never()).processCoding(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any());
    }

    @Test
    void codingAllowsMatchingLanguage() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL",
                java.util.List.of("python"), 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        armRound(mgr, "oj1");
        mgr.submit("123456", "oj1", player.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\"}"));
        verify(submissionProcessor).processCoding(eq("s1"), eq("123456"), eq("oj1"), eq("Cody"),
                eq(player.uuid()), eq("python"), eq("print(1)"), anyInt(), any(), anyLong(), any());
    }

    @Test
    void codingWithNullResponseUsesEmptySource() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        armRound(mgr, "oj1");
        mgr.submit("123456", "oj1", player.uuid(), "python", null);
        verify(submissionProcessor).processCoding(eq("s1"), eq("123456"), eq("oj1"), eq("Cody"),
                eq(player.uuid()), eq("python"), eq(""), anyInt(), any(), anyLong(), any());
    }

    // ---------- host / leave / kick branches ----------

    @Test
    void leaveHostClearsHostUuid() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        var host = mgr.join("123456", "Host", "sess-h", "host", null);
        mgr.leave("123456", host.uuid());
        assertNull(roomOf(mgr).hostUuid());
    }

    @Test
    void kickHostAlsoClearsHostUuid() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        var host = mgr.join("123456", "Host", "sess-h", "host", null);
        mgr.kickPlayer("123456", host.uuid());
        assertNull(roomOf(mgr).hostUuid());
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
        var p = mgr.join("123456", "A", "sa", "player", null);
        roomOf(mgr).softRemove(p.uuid());           // disconnect -> connectedCount 0
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
        mgr.startQuestion("123456");                // ACTIVE, not idle-eligible
        mgr.sweepIdleRooms();
        assertDoesNotThrow(() -> mgr.getRoomState("123456"));
    }

    @Test
    void broadcastLeaderboardForMissingRoomReturnsWithoutSend() {
        GameRoomManager mgr = manager();
        mgr.broadcastLeaderboard("000000");         // key 0, no room
        verify(scheduler).markDirty(eq(0), runnableCaptor.capture());
        runnableCaptor.getValue().run();            // flushLeaderboardDelta(0) -> room null
        verify(ws, never()).broadcastRaw(any(), any());
    }

    @Test
    void sendFullLeaderboardEmptyBoardSendsNoResync() {
        when(quizRepository.findById("qz")).thenReturn(Optional.of(
                new Quiz("qz", "T", "", null, Instant.now(), false)));
        lenient().when(sessionRepository.findByPin(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.create(eq("qz"), eq("host"), anyString(), eq(null)))
                .thenAnswer(inv -> new GameSession("gen", "qz", inv.getArgument(2),
                        "host", "LOBBY", 0, null, null, null, Instant.now()));
        GameRoomManager mgr = manager();
        GameSession created = mgr.createRoom("qz", "host");
        mgr.sendFullLeaderboard(created.pinCode(), "sess");
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
        runnableCaptor.getValue().run();            // now < end -> returns early
        assertEquals("ACTIVE", mgr.getRoomState("123456").status());
    }

    @Test
    void onTimerExpiredNoOpWhenRoomNotActive() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        verify(roundTimer).schedule(eq(123456), anyLong(), runnableCaptor.capture());
        setField(roomOf(mgr), "status", "REVIEW");
        runnableCaptor.getValue().run();            // status != ACTIVE -> returns
        assertEquals("REVIEW", mgr.getRoomState("123456").status());
    }

    @Test
    void onTimerExpiredTransitionsToReviewWhenGenuinelyExpired() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        verify(roundTimer).schedule(eq(123456), anyLong(), runnableCaptor.capture());
        setField(roomOf(mgr), "currentQuestionEndEpochMs", System.currentTimeMillis() - 10_000);
        runnableCaptor.getValue().run();            // now >= end -> transition
        assertEquals("REVIEW", mgr.getRoomState("123456").status());
        verify(sessionRepository).updateStatus("s1", "REVIEW");
    }

    @Test
    void onTimerExpiredNoOpWhenRoomWasRemoved() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");
        verify(roundTimer).schedule(eq(123456), anyLong(), runnableCaptor.capture());
        registryOf(mgr).remove(123456);             // room gone before fire
        runnableCaptor.getValue().run();            // registry.get -> null -> return
        verify(sessionRepository, never()).updateStatus(anyString(), eq("REVIEW"));
    }

    // ---------- createRoom branches ----------

    @Test
    void createRoomThrowsWhenQuizNotFound() {
        when(quizRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> manager().createRoom("missing", "host"));
    }

    /** Forces the PIN-collision retry loop: first findByPin collides, second is free. */
    @Test
    void createRoomRetriesOnPinCollision() {
        when(quizRepository.findById("qz")).thenReturn(Optional.of(
                new Quiz("qz", "T", "", null, Instant.now(), false)));
        when(sessionRepository.findByPin(anyString()))
                .thenReturn(Optional.of(session("123456")))   // collide -> retry
                .thenReturn(Optional.empty());                // free -> exit
        when(sessionRepository.create(eq("qz"), eq("host"), anyString(), eq(null)))
                .thenAnswer(inv -> new GameSession("gen", "qz", inv.getArgument(2),
                        "host", "LOBBY", 0, null, null, null, Instant.now()));

        GameSession created = manager().createRoom("qz", "host");

        assertEquals("LOBBY", created.status());
        assertTrue(created.pinCode().matches("\\d{6}"));
        verify(sessionRepository, atLeast(2)).findByPin(anyString());   // loop re-ran
    }

    // ---------- join: host-at-capacity guard ----------

    @Test
    void joinHostRejectedWhenRoomAtCapacity() throws Exception {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("maxPlayers");
        f.setAccessible(true);
        f.setInt(mgr, 1);
        mgr.join("123456", "P", "s0", "player", null);   // fills the single slot
        assertThrows(IllegalStateException.class,
                () -> mgr.join("123456", "Host", "sh", "host", null));
    }

    // ---------- leave: missing-room guard ----------

    @Test
    void leaveOnMissingRoomIsNoOp() {
        manager().leave("000000", "x");
        verify(ws, never()).broadcast(any(), any());
        verify(ws, never()).broadcastRaw(any(), any());
    }

    // ---------- submit: active-but-wrong-question lock + null-player error send ----------

    @Test
    void submitActiveButWrongQuestionIdIsLocked() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findById("q2")).thenReturn(Optional.of(mcq("q2")));
        mgr.join("123456", "Alice", "sess-1", "player", null);
        armRound(mgr, "q1");                 // status ACTIVE, currentQuestionId = q1
        mgr.submit("123456", "q2", "ghost-uuid", "python",
                Json.readTree("{\"selectedIndex\":0}"));   // wrong qid, unknown player
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(any(), sent.capture());
        assertTrue(((ErrorMessage) sent.getValue()).message().contains("Round is locked"));
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
        mgr.submit("123456", "oj1", "ghost-uuid", "python",
                Json.readTree("{\"source\":\"x\"}"));   // no such player -> return early
        verify(submissionProcessor, never()).processCoding(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any());
        verify(ws, never()).send(anyString(), any());
    }

    // ---------- submit coding: handler applies score + notifies ----------

    @Test
    void submitCodingHandlerAppliesScoreAndNotifies() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any()))
                .thenAnswer(inv -> {
                    CodingOutcomeConsumer h = inv.getArgument(10);
                    h.accept(player.uuid(), 500, true, 5, 5, null);    // correct path -> streak bonus 0, score 500
                    h.accept(player.uuid(), 100, false, 2, 5, null);   // wrong path -> resetStreak, score 100
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                });
        armRound(mgr, "oj1");
        mgr.submit("123456", "oj1", player.uuid(), "python",
                Json.readTree("{\"source\":\"x\"}"));

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws, org.mockito.Mockito.times(2)).send(eq(player.sessionId()), sent.capture());
        java.util.List<Object> all = sent.getAllValues();
        assertTrue(all.stream().anyMatch(o -> o instanceof SubmissionResult r && r.score() == 500));
        assertTrue(all.stream().anyMatch(o -> o instanceof SubmissionResult r && r.score() == 100
                && !r.allPassed()));
        verify(scheduler, atLeastOnce()).markDirty(eq(123456), any());   // broadcastLeaderboard
    }

    // ---------- startQuestion when index already past the last question ----------

    @Test
    void startQuestionAtEndEndsGameImmediately() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        setField(roomOf(mgr), "currentQuestionIndex", 1);   // already past size 1
        mgr.startQuestion("123456");
        verify(sessionRepository).updateStatus("s1", "ENDED");
        assertThrows(IllegalArgumentException.class, () -> mgr.getRoomState("123456"));
    }

    // ---------- private-method branches reached via reflection ----------

    @Test
    void sendRoundResultMissingRoomReturns() throws Exception {
        GameRoomManager mgr = manager();
        var m = GameRoomManager.class.getDeclaredMethod("sendRoundResult", String.class, boolean.class);
        m.setAccessible(true);
        m.invoke(mgr, "000000", true);   // registry miss -> return
        verify(ws, never()).broadcast(any(), any());
    }

    @Test
    void sendRoundResultPastQuestionIndexReturns() throws Exception {
        GameRoomManager mgr = manager();
        seedRoom(mgr);   // findByQuiz("qz") -> empty list via lenient stub
        var m = GameRoomManager.class.getDeclaredMethod("sendRoundResult", String.class, boolean.class);
        m.setAccessible(true);
        m.invoke(mgr, "123456", false);   // idx 0 >= size 0 -> return
        verify(ws, never()).broadcast(any(), any());
    }

    @Test
    void sendRoundResultNonRevealedBuildsRoundResult() throws Exception {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        var p = mgr.join("123456", "A", "sa", "player", null);
        setField(roomOf(mgr), "status", "REVIEW");
        roomOf(mgr).setCurrentQuestionIndex(0);
        var m = GameRoomManager.class.getDeclaredMethod("sendRoundResult", String.class, boolean.class);
        m.setAccessible(true);
        m.invoke(mgr, "123456", false);   // non-revealed -> answer null, body runs
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        RoundResult rr = (RoundResult) msg.getAllValues().stream()
                .filter(x -> x instanceof RoundResult).findFirst().orElseThrow();
        assertEquals(false, rr.revealed());
        assertNull(rr.correctAnswer());
        assertTrue(rr.scores().stream().anyMatch(s -> s.uuid().equals(p.uuid())));
    }

    @Test
    void deltaJsonNullBatchReturnsNull() throws Exception {
        GameRoomManager mgr = manager();
        var m = GameRoomManager.class.getDeclaredMethod("deltaJson",
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
        var m = GameRoomManager.class.getDeclaredMethod("playerSessionIds", String.class);
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
        var m = GameRoomManager.class.getDeclaredMethod("sessionIdOf", GameRoom.class, String.class);
        m.setAccessible(true);
        assertNull(m.invoke(mgr, roomOf(mgr), "ghost-uuid"));
    }

    @Test
    void startAndStopSweepRun() throws Exception {
        GameRoomManager mgr = manager();
        var start = GameRoomManager.class.getDeclaredMethod("startSweep");
        start.setAccessible(true);
        start.invoke(mgr);
        var stop = GameRoomManager.class.getDeclaredMethod("stopSweep");
        stop.setAccessible(true);
        stop.invoke(mgr);
    }

    // ---------- extra branch coverage ----------

    /** Drives the Runnable scheduled by extendTimer so its lambda body (onTimerExpired) runs. */
    @Test
    void extendTimerSchedulesAndFiresExpiry() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        mgr.startQuestion("123456");            // ACTIVE
        mgr.extendTimer("123456", 30);
        verify(roundTimer, times(2)).schedule(eq(123456), anyLong(), runnableCaptor.capture());
        runnableCaptor.getValue().run();        // fire the timer's onTimerExpired lambda
        assertEquals("ACTIVE", mgr.getRoomState("123456").status());  // now < end -> no-op
    }

    /** toDto must handle a question whose config is null (falls back to Map.of()). */
    @Test
    void startQuestionWithNullConfigBuildsDto() {
        GameRoomManager mgr = manager();
        seedRoom(mgr);
        Question q = new Question("q1", "qz", "T", "D", "MCQ", null, 30, 100, null, 0, Instant.now());
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q));
        mgr.startQuestion("123456");
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        assertTrue(msg.getAllValues().stream().anyMatch(m -> m instanceof QuestionStart));
    }

    /** A LOBBY room with a still-connected player must survive the idle sweep. */
    @Test
    void sweepKeepsLobbyRoomWithConnectedPlayer() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        mgr.join("123456", "A", "sa", "player", null);   // connected -> connectedCount != 0
        setField(roomOf(mgr), "lastActivityMs", System.currentTimeMillis() - 31L * 60_000);
        mgr.sweepIdleRooms();
        assertEquals(1, mgr.activeRooms());               // not removed
    }

    /** Empty (non-null) languagesAllowed list skips the language gate entirely. */
    @Test
    void codingWithEmptyLanguageListIsAllowed() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL",
                java.util.List.of(), 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Cody", "sess-c", "player", null);
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), any(), anyLong(), any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        armRound(mgr, "oj1");
        mgr.submit("123456", "oj1", player.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\"}"));
        verify(submissionProcessor).processCoding(eq("s1"), eq("123456"), eq("oj1"), eq("Cody"),
                eq(player.uuid()), eq("python"), eq("print(1)"), anyInt(), any(), anyLong(), any());
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
        var player = mgr.join("123456", "Alice", "sess-1", "player", null);
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        armRound(mgr, "q1");
        mgr.submit("123456", "q1", player.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));
        mgr.forceSubmit("123456");   // -> transitionToReview -> sendRoundResult(true)
        ArgumentCaptor<Object> msg = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeastOnce()).broadcast(any(), msg.capture());
        RoundResult rr = (RoundResult) msg.getAllValues().stream()
                .filter(m -> m instanceof RoundResult).findFirst().orElseThrow();
        assertTrue(rr.scores().stream().anyMatch(s -> s.uuid().equals(player.uuid()) && s.correct()));
    }

    /** A LOBBY room whose players disconnected but have not yet idled out survives the sweep. */
    @Test
    void sweepKeepsFreshLobbyWithDisconnectedPlayer() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var p = mgr.join("123456", "A", "sa", "player", null);
        roomOf(mgr).softRemove(p.uuid());          // connectedCount() == 0
        mgr.sweepIdleRooms();                      // idleMs still < TTL -> keep
        assertEquals(1, mgr.activeRooms());
    }
}
