package com.openquiz.service;

import com.openquiz.domain.dto.QuestionStart;
import com.openquiz.domain.dto.RoundResult;
import com.openquiz.domain.dto.TimerUpdate;
import com.openquiz.domain.models.GameSession;
import com.openquiz.domain.models.Question;
import com.openquiz.repository.GameSessionRepository;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.QuizRepository;
import com.openquiz.repository.SubmissionRepository;
import com.openquiz.util.Json;
import com.openquiz.websocket.WebSocketSessionManager;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @SuppressWarnings("unchecked")
    private final org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> processorProvider =
            (org.springframework.beans.factory.ObjectProvider<SubmissionProcessor>) org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
    @Mock WebSocketSessionManager ws;
    @Mock EvaluationService evaluationService;
    @Mock AdminSettingsService settingsService;
    @Mock BroadcastScheduler scheduler;
    @Mock SubmissionWriteBuffer writeBuffer;

    private GameRoomManager manager() {
        lenient().when(processorProvider.getObject()).thenReturn(submissionProcessor);
        return new GameRoomManager(sessionRepository, quizRepository, questionRepository,
                submissionRepository, scoringEngine, processorProvider, ws,
                evaluationService, settingsService, scheduler, writeBuffer);
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
        mgr.join("123456", "Seed", "seed-sess", "player");
    }

    // ---------- join ----------

    @Test
    void joinRejectsInvalidPin() {
        when(sessionRepository.findByPin("000000")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> manager().join("000000", "Alice", "sess-1", "player"));
        verify(ws, never()).broadcast(any(), any());
    }

    @Test
    void joinSanitizesHostileNicknames() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Player p = manager().join("123456", "<script>x</script>", "sess", "player");
        assertTrue(p.name().matches("[A-Za-z0-9 _\\-]*"));
    }

    @Test
    void secondHostIsRejected() {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        mgr.join("123456", "Host", "sess-h", "host");
        assertThrows(IllegalStateException.class,
                () -> mgr.join("123456", "Impostor", "sess-i", "host"));
    }

    @Test
    void roomRejectsPlayersBeyondCapacity() throws Exception {
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        GameRoomManager mgr = manager();
        java.lang.reflect.Field f = GameRoomManager.class.getDeclaredField("maxPlayers");
        f.setAccessible(true);
        f.setInt(mgr, 3);
        for (int i = 0; i < 3; i++) {
            mgr.join("123456", "P" + i, "s" + i, "player");
        }
        assertThrows(IllegalStateException.class,
                () -> mgr.join("123456", "Extra", "s4", "player"));
    }

    // ---------- selection submit ----------

    @Test
    void submitScoresSelectionAndSaves() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(submissionRepository.findBySessionQuestion(anyString(), anyString())).thenReturn(List.of());
        when(scoringEngine.scoreSelection(eq(1.0), anyLong(), anyLong(), anyInt(), any())).thenReturn(900);

        var player = mgr.join("123456", "Alice", "sess-1", "player");
        mgr.submit("123456", "q1", player.uuid(), "python",
                Json.readTree("{\"selectedIndex\":0}"));

        ArgumentCaptor<com.openquiz.domain.models.Submission> saved =
                ArgumentCaptor.forClass(com.openquiz.domain.models.Submission.class);
        verify(writeBuffer).offer(saved.capture());
        assertEquals(900, saved.getValue().scoreEarned());
        assertTrue(saved.getValue().correct());

        // ROOM_STATE is immediate; the leaderboard goes through the 16 ms coalescer.
        verify(ws, times(1)).broadcast(any(), any());
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).markDirty(eq(123456), task.capture());
        task.getValue().run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> ids =
                ArgumentCaptor.forClass(java.util.Collection.class);
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
        when(submissionRepository.findBySessionQuestion(anyString(), anyString())).thenReturn(List.of());

        var player = mgr.join("123456", "Bob", "sess-b", "player");
        mgr.submit("123456", "q1", player.uuid(), "python",
                Json.readTree("{\"selectedIndex\":3}"));

        ArgumentCaptor<com.openquiz.domain.models.Submission> saved =
                ArgumentCaptor.forClass(com.openquiz.domain.models.Submission.class);
        verify(writeBuffer).offer(saved.capture());
        assertEquals(0, saved.getValue().scoreEarned());
        assertEquals(false, saved.getValue().correct());
        // Fraction flows into the engine, which hard-zeroes it internally.
        verify(scoringEngine).scoreSelection(eq(0.0), anyLong(), anyLong(), anyInt(), any());
        verify(scheduler).markDirty(eq(123456), any());
    }

    @Test
    void partialCreditScalesTheRawSpeedScore() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        String multi = Json.write(Map.of("correctIndices", List.of(0, 1)));
        when(questionRepository.findById("m1")).thenReturn(Optional.of(
                new Question("m1", "qz", "M", "D", "MULTIPLE_SELECT", null, 30, 100, multi, 0, Instant.now())));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(0.75);
        when(submissionRepository.findBySessionQuestion(anyString(), anyString())).thenReturn(List.of());
        when(scoringEngine.scoreSelection(eq(0.75), anyLong(), anyLong(), anyInt(), any())).thenReturn(800);

        var player = mgr.join("123456", "Cara", "sess-p", "player");
        mgr.submit("123456", "m1", player.uuid(), "python",
                Json.readTree("{\"selectedIndices\":[0]}"));

        ArgumentCaptor<com.openquiz.domain.models.Submission> saved =
                ArgumentCaptor.forClass(com.openquiz.domain.models.Submission.class);
        verify(writeBuffer).offer(saved.capture());
        assertEquals(600, saved.getValue().scoreEarned());
        assertEquals(false, saved.getValue().correct());
    }

    @Test
    void codingSubmitsRouteToProcessorNotSelectionPath() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_FULL", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Cody", "sess-c", "player");
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any())).thenReturn(true);

        mgr.submit("123456", "oj1", player.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));

        verify(submissionProcessor).processCoding(eq("s1"), eq("oj1"), eq("Cody"),
                eq(player.uuid()), eq("python"), eq("print(1)"), any());
        verify(writeBuffer, never()).offer(any());
        verify(ws, never()).send(anyString(), any());
    }

    @Test
    void saturatedJudgeAnswersWithFriendlyRetry() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        Question oj = new Question("oj1", "qz", "OJ", "D", "OJ_PATCH", null, 60, 500, "{}", 0, Instant.now());
        when(questionRepository.findById("oj1")).thenReturn(Optional.of(oj));
        var player = mgr.join("123456", "Pat", "sess-j", "player");
        when(submissionProcessor.processCoding(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any())).thenReturn(false);

        mgr.submit("123456", "oj1", player.uuid(), "python",
                Json.readTree("{\"source\":\"print(1)\",\"language\":\"python\"}"));

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(ws).send(eq(player.sessionId()), sent.capture());
        assertTrue(((com.openquiz.domain.dto.ErrorMessage) sent.getValue()).message()
                .contains("Judge queue is busy"));
    }

    // ---------- question lifecycle ----------

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
        verify(ws).broadcast(any(), msg.capture());
        assertEquals("ROUND_RESULT", ((RoundResult) msg.getValue()).type());
    }

    @Test
    void kickRemovesPlayerAndNotifiesTheirSession() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var p = mgr.join("123456", "Bad", "sess-k", "player");
        org.mockito.Mockito.clearInvocations(ws);

        mgr.kickPlayer("123456", p.uuid());

        verify(ws).send(eq("sess-k"), any());
        assertEquals(0, mgr.getRoomState("123456").players().size());
    }

    @Test
    void leaveDropsDisconnectedPlayer() {
        GameRoomManager mgr = manager();
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var p = mgr.join("123456", "Ghost", "sess-g", "player");
        org.mockito.Mockito.clearInvocations(ws);

        mgr.leave("123456", p.uuid());

        assertEquals(0, mgr.getRoomState("123456").players().size());
    }

    // ---------- creation ----------

    @Test
    void createRoomGeneratesSixDigitPinAndPersistsLobby() {
        when(quizRepository.findById("qz")).thenReturn(Optional.of(
                new com.openquiz.domain.models.Quiz("qz", "T", "", null, Instant.now(), false)));
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
        mgr.join("123456", "A", "sa", "player");
        mgr.join("123456", "B", "sb", "player");

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
        var p = mgr.join("123456", "Sync", "sess-r", "player");
        org.mockito.Mockito.clearInvocations(ws);

        mgr.sendFullLeaderboard("123456", p.sessionId());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(ws).sendRaw(eq("sess-r"), payload.capture());
        assertTrue(payload.getValue().contains("LEADERBOARD_DELTA"));
        assertTrue(payload.getValue().contains("\"resync\":true"));
        assertTrue(payload.getValue().contains("Sync"));
    }
}
