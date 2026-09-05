package com.sprintjudge.websocket;

import com.sprintjudge.service.GameRoom;
import com.sprintjudge.service.GameRoomManager;
import com.sprintjudge.service.Player;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameWebSocketTeamBattleTest {

    @Mock GameRoomManager roomManager;
    @Mock JoinRateLimiter rateLimiter;
    @Mock WebSocketSessionManager sessions;
    @Mock Session session;
    @Mock RemoteEndpoint.Basic remote;

    private final Map<String, Object> props = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(session.getUserProperties()).thenReturn(props);
        lenient().when(session.getBasicRemote()).thenReturn(remote);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session.getId()).thenReturn("sess");
        lenient().doAnswer(inv -> null).when(remote).sendText(anyString());
        lenient().doAnswer(inv -> null).when(sessions).sendRaw(eq("sess"), anyString());
        Map<String, Object> handshake = new HashMap<>();
        handshake.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.FALSE);
        handshake.put(SecureHandshakeConfigurator.REMOTE_ADDR, "unresolved");
        EndpointConfig cfg = org.mockito.Mockito.mock(EndpointConfig.class);
        lenient().when(cfg.getUserProperties()).thenReturn(handshake);
        new GameWebSocket(roomManager, rateLimiter, sessions).onOpen(session, cfg);
    }

    private GameWebSocket ws() {
        return new GameWebSocket(roomManager, rateLimiter, sessions);
    }

    private String lastMessage() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(sessions).sendRaw(eq("sess"), cap.capture());
        return cap.getValue();
    }

    private void asHost() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
    }

    @Test
    void createTeamHappyPathSendsTeamCreated() {
        asHost();
        when(roomManager.createTeam("123456", "Alpha"))
                .thenReturn(new GameRoom.Team("team-1", "Alpha", Set.of(), 0));
        ws().onMessage(session, "{\"type\":\"CREATE_TEAM\",\"name\":\"Alpha\"}");
        verify(roomManager).createTeam("123456", "Alpha");
        assertTrue(lastMessage().contains("TEAM_CREATED"));
        assertTrue(lastMessage().contains("team-1"));
    }

    @Test
    void createTeamDefaultsNameWhenMissing() {
        asHost();
        when(roomManager.createTeam(eq("123456"), anyString()))
                .thenReturn(new GameRoom.Team("team-1", "Team", Set.of(), 0));
        ws().onMessage(session, "{\"type\":\"CREATE_TEAM\"}");
        verify(roomManager).createTeam("123456", "Team");
    }

    @Test
    void createTeamForbiddenForPlayer() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("role", "player");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"CREATE_TEAM\",\"name\":\"Alpha\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).createTeam(anyString(), anyString());
    }

    @Test
    void createTeamForbiddenWhenUnauthenticated() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.FALSE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"CREATE_TEAM\",\"name\":\"Alpha\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).createTeam(anyString(), anyString());
    }

    @Test
    void joinTeamHappyPathSendsTeamJoined() {
        props.put("pin", "123456");
        props.put("playerUuid", "uuid-9");
        when(roomManager.joinTeam("123456", "team-1", "uuid-9"))
                .thenReturn(new GameRoom.Team("team-1", "Alpha", Set.of("uuid-9"), 0));
        ws().onMessage(session, "{\"type\":\"JOIN_TEAM\",\"teamId\":\"team-1\"}");
        verify(roomManager).joinTeam("123456", "team-1", "uuid-9");
        assertTrue(lastMessage().contains("TEAM_JOINED"));
    }

    @Test
    void joinTeamWithoutUuidNeverCallsManager() {
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"JOIN_TEAM\",\"teamId\":\"team-1\"}");
        verify(roomManager, never()).joinTeam(anyString(), anyString(), anyString());
    }

    @Test
    void joinTeamWithBlankTeamIdNeverCallsManager() {
        props.put("pin", "123456");
        props.put("playerUuid", "uuid-9");
        ws().onMessage(session, "{\"type\":\"JOIN_TEAM\",\"teamId\":\"  \"}");
        verify(roomManager, never()).joinTeam(anyString(), anyString(), anyString());
    }

    @Test
    void joinTeamNullResultSendsNothing() {
        props.put("pin", "123456");
        props.put("playerUuid", "uuid-9");
        when(roomManager.joinTeam("123456", "ghost", "uuid-9")).thenReturn(null);
        ws().onMessage(session, "{\"type\":\"JOIN_TEAM\",\"teamId\":\"ghost\"}");
        verify(sessions, never()).sendRaw(eq("sess"), contains("TEAM_JOINED"));
    }

    @Test
    void getTeamsHappyPathSendsTeamList() {
        props.put("pin", "123456");
        when(roomManager.getTeams("123456")).thenReturn(
                List.of(new GameRoom.Team("team-1", "Alpha", Set.of(), 0)));
        ws().onMessage(session, "{\"type\":\"GET_TEAMS\"}");
        assertTrue(lastMessage().contains("TEAM_LIST"));
    }

    @Test
    void getTeamsWithoutPinSendsJoinFirstError() {
        ws().onMessage(session, "{\"type\":\"GET_TEAMS\"}");
        assertTrue(lastMessage().contains("Join a room first"));
    }

    @Test
    void startBattleHappyPathForwardsToManager() {
        asHost();
        ws().onMessage(session, "{\"type\":\"START_BATTLE\"}");
        verify(roomManager).startBattle("123456");
    }

    @Test
    void startBattleForbiddenForPlayer() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("role", "player");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"START_BATTLE\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).startBattle(anyString());
    }

    @Test
    void getBracketHappyPathSendsBracket() {
        props.put("pin", "123456");
        when(roomManager.getBracket("123456")).thenReturn(java.util.List.<String[]>of(new String[]{"a", "b"}));
        ws().onMessage(session, "{\"type\":\"GET_BRACKET\"}");
        assertTrue(lastMessage().contains("BRACKET"));
    }

    @Test
    void getBracketWithoutPinSendsJoinFirstError() {
        ws().onMessage(session, "{\"type\":\"GET_BRACKET\"}");
        assertTrue(lastMessage().contains("Join a room first"));
    }

    @Test
    void joinTeamPlayerObjectAvailable() {
        var p = new Player("uuid-9", "Bob", 0, "sess", true);
        org.junit.jupiter.api.Assertions.assertEquals("uuid-9", p.uuid());
    }
}
