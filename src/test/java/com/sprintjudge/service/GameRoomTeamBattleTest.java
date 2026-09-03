package com.sprintjudge.service;

import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.util.Json;
import com.sprintjudge.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GameRoomTeamBattleTest {

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
                Json.write(Map.of("correctIndex", 0)), 0, Instant.now());
    }

    private void seedTwoPlayers(GameRoomManager mgr) {
        lenient().when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        mgr.join("123456", "Alice", "s1", "player", null);
        mgr.join("123456", "Bob", "s2", "player", null);
    }

    @Test
    void roomCreateTeamAssignsSequentialIds() {
        GameRoom room = new GameRoom("s", "q", "pin", "LOBBY");
        var t1 = room.createTeam("Alpha");
        var t2 = room.createTeam("Beta");
        assertTrue(t1.id().startsWith("team-"));
        assertTrue(t2.id().startsWith("team-"));
        assertTrue(t1.memberUuids().isEmpty());
        assertEquals(0, t1.score());
    }

    @Test
    void roomJoinTeamAddsMember() {
        GameRoom room = new GameRoom("s", "q", "pin", "LOBBY");
        var t = room.createTeam("Alpha");
        var updated = room.joinTeam(t.id(), "uuid-1");
        assertNotNull(updated);
        assertTrue(updated.memberUuids().contains("uuid-1"));
        assertEquals(t.id(), room.teamIdOf("uuid-1"));
    }

    @Test
    void roomJoinUnknownTeamReturnsNull() {
        GameRoom room = new GameRoom("s", "q", "pin", "LOBBY");
        assertNull(room.joinTeam("ghost", "uuid-1"));
    }

    @Test
    void roomTeamIdOfMissingReturnsNull() {
        GameRoom room = new GameRoom("s", "q", "pin", "LOBBY");
        room.createTeam("Alpha");
        assertNull(room.teamIdOf("nobody"));
    }

    @Test
    void roomGetTeamAndAllTeams() {
        GameRoom room = new GameRoom("s", "q", "pin", "LOBBY");
        var t = room.createTeam("Alpha");
        assertEquals("Alpha", room.getTeam(t.id()).name());
        assertNull(room.getTeam("ghost"));
        assertEquals(1, room.allTeams().size());
    }

    @Test
    void roomApplyTeamScoreUnknownReturnsZero() {
        GameRoom room = new GameRoom("s", "q", "pin", "LOBBY");
        assertEquals(0, room.applyTeamScore("ghost", 100));
    }

    @Test
    void roomBracketRoundTrip() {
        GameRoom room = new GameRoom("s", "q", "pin", "LOBBY");
        assertTrue(room.bracket().isEmpty());
        assertTrue(room.battleMatches().isEmpty());
        room.addBattleMatch(new GameRoom.BattleMatch("m1", "a", "b", "q1", null, null, false, false, null));
        assertEquals(1, room.battleMatches().size());
        room.setBracket(java.util.List.<String[]>of(new String[]{"a", "b"}));
        assertEquals(1, room.bracket().size());
    }

    @Test
    void managerCreateAndGetTeamsHappyPath() {
        GameRoomManager mgr = manager();
        lenient().when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        mgr.join("123456", "Host", "sh", "host", null);
        assertTrue(mgr.getTeams("123456").isEmpty());
        var team = mgr.createTeam("123456", "Alpha");
        assertNotNull(team);
        assertEquals("Alpha", team.name());
        assertEquals(1, mgr.getTeams("123456").size());
    }

    @Test
    void managerJoinTeamHappyPath() {
        GameRoomManager mgr = manager();
        lenient().when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var player = mgr.join("123456", "Alice", "s1", "player", null);
        var team = mgr.createTeam("123456", "Alpha");
        var updated = mgr.joinTeam("123456", team.id(), player.uuid());
        assertTrue(updated.memberUuids().contains(player.uuid()));
    }

    @Test
    void managerTeamGuardsRejectMissingPin() {
        GameRoomManager mgr = manager();
        assertThrows(IllegalArgumentException.class, () -> mgr.createTeam("000000", "A"));
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("000000", "t", "u"));
        assertThrows(IllegalArgumentException.class, () -> mgr.getTeams("000000"));
        assertThrows(IllegalArgumentException.class, () -> mgr.getBracket("000000"));
        assertThrows(IllegalArgumentException.class, () -> mgr.getBattleMatches("000000"));
    }

    @Test
    void managerStartBattleHappyPathBuildsBracket() {
        GameRoomManager mgr = manager();
        lenient().when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        lenient().when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        seedTwoPlayers(mgr);
        mgr.startBattle("123456");
        assertEquals(1, mgr.getBattleMatches("123456").size());
        assertEquals(1, mgr.getBracket("123456").size());
    }

    @Test
    void managerStartBattleGuardsRejectMissingPin() {
        assertThrows(IllegalArgumentException.class, () -> manager().startBattle("000000"));
    }

    @Test
    void managerStartBattleWithNoQuestionsThrows() {
        GameRoomManager mgr = manager();
        lenient().when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
        seedTwoPlayers(mgr);
        assertThrows(IllegalStateException.class, () -> mgr.startBattle("123456"));
    }

    @Test
    void managerStartBattleOddPlayersLeavesOneUnpaired() {
        GameRoomManager mgr = manager();
        lenient().when(questionRepository.findByQuiz("qz")).thenReturn(List.of(mcq("q1")));
        lenient().when(questionRepository.findById("q1")).thenReturn(Optional.of(mcq("q1")));
        lenient().when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        mgr.join("123456", "P1", "s1", "player", null);
        mgr.join("123456", "P2", "s2", "player", null);
        mgr.join("123456", "P3", "s3", "player", null);
        mgr.startBattle("123456");
        assertEquals(1, mgr.getBattleMatches("123456").size());
        assertEquals(1, mgr.getBracket("123456").size());
    }

    @Test
    void managerGetBracketInitiallyEmpty() {
        GameRoomManager mgr = manager();
        lenient().when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        mgr.join("123456", "Alice", "s1", "player", null);
        assertTrue(mgr.getBracket("123456").isEmpty());
        assertTrue(mgr.getBattleMatches("123456").isEmpty());
    }

    @Test
    void teamJoinUnknownTeamThrowsThroughManager() {
        GameRoomManager mgr = manager();
        lenient().when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session("123456")));
        var player = mgr.join("123456", "Alice", "s1", "player", null);
        assertThrows(IllegalArgumentException.class, () -> mgr.joinTeam("123456", "ghost", player.uuid()));
    }
}
