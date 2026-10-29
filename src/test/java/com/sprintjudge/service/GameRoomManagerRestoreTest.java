package com.sprintjudge.service;

import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.Player;
import com.sprintjudge.service.room.RoomRegistry;
import com.sprintjudge.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRoomManagerRestoreTest {

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

    private RoomRegistry registry(GameRoomManager m) {
        try {
            var f = GameRoomManager.class.getDeclaredField("registry");
            f.setAccessible(true);
            return (RoomRegistry) f.get(m);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void setMaxPlayers(GameRoomManager m, int v) {
        try {
            var f = GameRoomManager.class.getDeclaredField("maxPlayers");
            f.setAccessible(true);
            f.setInt(m, v);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private GameSession gs(String id, String quizId, String pin, String status, String mode, int idx) {
        return new GameSession(id, quizId, pin, "host", status, mode, idx, null, null, null, Instant.now());
    }

    @Test
    void emptySessions() {
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of());
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(0, registry(m).size());
    }

    @Test
    void nullSessions() {
        when(sessionRepository.findAllNonEnded()).thenReturn(null);
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(0, registry(m).size());
    }

    @Test
    void loadThrows() {
        when(sessionRepository.findAllNonEnded()).thenThrow(new RuntimeException("boom"));
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(0, registry(m).size());
    }

    @Test
    void restoresBasicRoom() {
        GameSession s = gs("sid1", "qz1", "123456", "ACTIVE", "TEAM", 2);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        lenient().when(submissionRepository.findBySession("sid1")).thenReturn(List.of());
        GameRoomManager m = manager();
        m.restoreRooms();
        GameRoom room = registry(m).get(123456);
        assertNotNull(room);
        assertEquals("sid1", room.sessionId());
        assertEquals("qz1", room.quizId());
        assertEquals("123456", room.pin());
        assertEquals("ACTIVE", room.status());
        assertEquals(2, room.currentQuestionIndex());
        assertEquals(GameRoom.GameMode.TEAM, room.gameMode());
    }

    @Test
    void nullStatusDefaultsLobby() {
        GameSession s = gs("sid2", "qz2", "111111", null, "STANDARD", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        lenient().when(submissionRepository.findBySession("sid2")).thenReturn(List.of());
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals("LOBBY", registry(m).get(111111).status());
    }

    @Test
    void nullGameModeDefaultsStandard() {
        GameSession s = gs("sid3", "qz3", "222222", "LOBBY", null, 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        lenient().when(submissionRepository.findBySession("sid3")).thenReturn(List.of());
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(GameRoom.GameMode.STANDARD, registry(m).get(222222).gameMode());
    }

    @Test
    void unknownGameModeDefaultsStandard() {
        GameSession s = gs("sid4", "qz4", "333333", "LOBBY", "NOPE", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        lenient().when(submissionRepository.findBySession("sid4")).thenReturn(List.of());
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(GameRoom.GameMode.STANDARD, registry(m).get(333333).gameMode());
    }

    @Test
    void blankGameModeDefaultsStandard() {
        GameSession s = gs("sid5", "qz5", "444444", "LOBBY", "   ", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        lenient().when(submissionRepository.findBySession("sid5")).thenReturn(List.of());
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(GameRoom.GameMode.STANDARD, registry(m).get(444444).gameMode());
    }

    @Test
    void skipsNullSession() {
        List<GameSession> ls = new java.util.ArrayList<>();
        ls.add(null);
        ls.add(gs("sid6", "qz6", "555555", "LOBBY", "STANDARD", 0));
        when(sessionRepository.findAllNonEnded()).thenReturn(ls);
        lenient().when(submissionRepository.findBySession("sid6")).thenReturn(List.of());
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(1, registry(m).size());
        assertNotNull(registry(m).get(555555));
    }

    @Test
    void skipsBlankPin() {
        GameSession s = gs("sid7", "qz7", "   ", "LOBBY", "STANDARD", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(0, registry(m).size());
    }

    @Test
    void skipsNullPin() {
        GameSession s = gs("sid7b", "qz7b", null, "LOBBY", "STANDARD", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(0, registry(m).size());
    }

    @Test
    void skipsInvalidPin() {
        GameSession s = gs("sid8", "qz8", "abc", "LOBBY", "STANDARD", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        GameRoomManager m = manager();
        m.restoreRooms();
        assertEquals(0, registry(m).size());
    }

    @Test
    void skipsAlreadyRegistered() {
        GameSession s = gs("sid9", "qz9", "666666", "LOBBY", "STANDARD", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        lenient().when(submissionRepository.findBySession("sid9")).thenReturn(List.of());
        GameRoomManager m = manager();
        // pre-register
        try {
            var f = GameRoomManager.class.getDeclaredField("registry");
            f.setAccessible(true);
            RoomRegistry r = (RoomRegistry) f.get(m);
            r.put(666666, new GameRoom("existing", "qz", "666666", "LOBBY"));
        } catch (Exception e) { throw new RuntimeException(e); }
        m.restoreRooms();
        assertEquals("existing", registry(m).get(666666).sessionId());
    }

    @Test
    void restoresSubmissionsAndScores() {
        GameSession s = gs("sid10", "qz10", "777777", "ACTIVE", "STANDARD", 1);
        Submission a = new Submission("sub1", "sid10", "q1", "Alice", "u1", "{}", 10, true, null, 1, Instant.now());
        Submission b = new Submission("sub2", "sid10", "q2", "Alice", "u1", "{}", 5, true, null, 1, Instant.now());
        Submission c = new Submission("sub3", "sid10", "q1", "Bob", "u2", "{}", 7, false, null, 1, Instant.now());
        Submission nullPlayer = new Submission("sub4", "sid10", "q1", "X", null, "{}", 3, false, null, 1, Instant.now());
        Submission blankPlayer = new Submission("sub5", "sid10", "q1", "Y", "   ", "{}", 3, false, null, 1, Instant.now());
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        when(submissionRepository.findBySession("sid10")).thenReturn(List.of(a, b, c, nullPlayer, blankPlayer));
        GameRoomManager m = manager();
        m.restoreRooms();
        GameRoom room = registry(m).get(777777);
        assertNotNull(room);
        // u1 score 15
        assertEquals(15L, room.board().scoreOf("u1"));
        // u2 score 7
        assertEquals(7L, room.board().scoreOf("u2"));
        // players connected false
        for (Player p : room.players()) {
            if (!p.uuid().equals("u1") && !p.uuid().equals("u2")) continue;
            // connected false
        }
        // check connected flag via getPlayer
        assertEquals(false, room.getPlayer("u1").connected());
        assertEquals(false, room.getPlayer("u2").connected());
    }

    @Test
    void blankPlayerNameDefaultsPlayer() {
        GameSession s = gs("sid11", "qz11", "888888", "LOBBY", "STANDARD", 0);
        Submission a = new Submission("sub6", "sid11", "q1", "   ", "u3", "{}", 4, true, null, 1, Instant.now());
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        when(submissionRepository.findBySession("sid11")).thenReturn(List.of(a));
        GameRoomManager m = manager();
        m.restoreRooms();
        GameRoom room = registry(m).get(888888);
        assertEquals("Player", room.getPlayer("u3").name());
    }

    @Test
    void nullPlayerNameDefaultsPlayer() {
        GameSession s = gs("sid12", "qz12", "999999", "LOBBY", "STANDARD", 0);
        Submission a = new Submission("sub7", "sid12", "q1", null, "u4", "{}", 4, true, null, 1, Instant.now());
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        when(submissionRepository.findBySession("sid12")).thenReturn(List.of(a));
        GameRoomManager m = manager();
        m.restoreRooms();
        GameRoom room = registry(m).get(999999);
        assertEquals("Player", room.getPlayer("u4").name());
    }

    @Test
    void zeroScoreSkipsApply() {
        GameSession s = gs("sid13", "qz13", "101010", "LOBBY", "STANDARD", 0);
        Submission a = new Submission("sub8", "sid13", "q1", "Zed", "u5", "{}", 0, false, null, 1, Instant.now());
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        when(submissionRepository.findBySession("sid13")).thenReturn(List.of(a));
        GameRoomManager m = manager();
        m.restoreRooms();
        GameRoom room = registry(m).get(101010);
        // player added at score 0; applyScore not called with 0 but score remains 0
        assertEquals(0L, room.board().scoreOf("u5"));
    }

    @Test
    void fullRoomSkipsPlayer() {
        GameSession s = gs("sid14", "qz14", "202020", "LOBBY", "STANDARD", 0);
        Submission a = new Submission("sub9", "sid14", "q1", "Zed", "u6", "{}", 5, true, null, 1, Instant.now());
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        when(submissionRepository.findBySession("sid14")).thenReturn(List.of(a));
        GameRoomManager m = manager();
        setMaxPlayers(m, 0);
        m.restoreRooms();
        GameRoom room = registry(m).get(202020);
        assertNull(room.getPlayer("u6"));
    }

    @Test
    void submissionRestoreThrowsCaught() {
        GameSession s = gs("sid15", "qz15", "303030", "LOBBY", "STANDARD", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        when(submissionRepository.findBySession("sid15")).thenThrow(new RuntimeException("boom"));
        GameRoomManager m = manager();
        m.restoreRooms();
        assertNotNull(registry(m).get(303030));
    }

    @Test
    void nullSubmissionsSkipped() {
        GameSession s = gs("sid16", "qz16", "404040", "LOBBY", "STANDARD", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        when(submissionRepository.findBySession("sid16")).thenReturn(null);
        GameRoomManager m = manager();
        m.restoreRooms();
        assertNotNull(registry(m).get(404040));
    }

    @Test
    void nullPlayerSubmissionSkipped() {
        GameSession s = gs("sid17", "qz17", "505050", "LOBBY", "STANDARD", 0);
        when(sessionRepository.findAllNonEnded()).thenReturn(List.of(s));
        List<Submission> ls2 = new java.util.ArrayList<>();
        ls2.add(null);
        when(submissionRepository.findBySession("sid17")).thenReturn(ls2);
        GameRoomManager m = manager();
        m.restoreRooms();
        assertNotNull(registry(m).get(505050));
    }
}
