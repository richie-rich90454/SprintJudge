package com.sprintjudge.websocket;

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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameWebSocketExtraTest {

    @Mock GameRoomManager roomManager;
    @Mock JoinRateLimiter rateLimiter;
    @Mock WebSocketSessionManager sessions;
    @Mock Session session;
    @Mock RemoteEndpoint.Basic remote;

    private final Map<String, Object> props = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(session.getUserProperties()).thenReturn(props);
        lenient().when(session.getBasicRemote()).thenReturn(remote);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session.getId()).thenReturn("sess");
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

    private void joinAsPlayer() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-9", "Bob", 0, "s", true);
        lenient().when(roomManager.join(anyString(), anyString(), anyString(), eq("player"), any())).thenReturn(p);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Bob\"}");
        org.mockito.Mockito.clearInvocations(sessions);
    }

    @Test
    void joinNonNumericPinRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"abc123\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Invalid PIN"));
        verify(rateLimiter, never()).tryJoin(anyString());
    }

    @Test
    void joinShortPinRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"12345\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Invalid PIN"));
    }

    @Test
    void joinLongPinRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"1234567\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Invalid PIN"));
    }

    @Test
    void joinTeamIllegalArgumentSendsError() {
        props.put("pin", "123456");
        props.put("playerUuid", "uuid-9");
        when(roomManager.joinTeam("123456", "t1", "uuid-9"))
                .thenThrow(new IllegalArgumentException("no such team"));
        ws().onMessage(session, "{\"type\":\"JOIN_TEAM\",\"teamId\":\"t1\"}");
        assertTrue(lastMessage().contains("no such team"));
    }

    @Test
    void joinTeamIllegalStateSendsError() {
        props.put("pin", "123456");
        props.put("playerUuid", "uuid-9");
        when(roomManager.joinTeam("123456", "t1", "uuid-9"))
                .thenThrow(new IllegalStateException("battle started"));
        ws().onMessage(session, "{\"type\":\"JOIN_TEAM\",\"teamId\":\"t1\"}");
        assertTrue(lastMessage().contains("battle started"));
    }

    @Test
    void submitManagerErrorSendsError() {
        joinAsPlayer();
        doThrow(new IllegalArgumentException("round closed")).when(roomManager)
                .submit(eq("123456"), eq("q1"), eq("uuid-9"), eq("python"), any());
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"python\",\"response\":{}}");
        assertTrue(lastMessage().contains("round closed"));
    }

    @Test
    void hostActionThrowingBecomesInternalError() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        doThrow(new RuntimeException("db down")).when(roomManager).nextQuestion("123456");
        ws().onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
        assertTrue(lastMessage().contains("Internal error"));
    }

    @Test
    void arrayMessageIsMalformed() {
        ws().onMessage(session, "[1,2,3]");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void nullTypeNodeIsMalformed() {
        ws().onMessage(session, "{\"type\":null}");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void getTeamsManagerErrorBecomesInternalError() {
        props.put("pin", "123456");
        when(roomManager.getTeams("123456")).thenThrow(new RuntimeException("x"));
        ws().onMessage(session, "{\"type\":\"GET_TEAMS\"}");
        assertTrue(lastMessage().contains("Internal error"));
    }

    @Test
    void joinForwardsRejoinToken() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-1", "Al", 0, "sess", true);
        when(roomManager.join(eq("123456"), eq("Al"), eq("sess"), eq("player"), eq("tok-1"))).thenReturn(p);
        ws().onMessage(session,
                "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Al\",\"rejoinToken\":\"tok-1\"}");
        assertTrue(lastMessage().contains("JOINED"));
        verify(rateLimiter).recordSuccess("unresolved");
    }

    @Test
    void onErrorCleansUp() {
        props.put("pin", "123456");
        props.put("playerUuid", "uuid-1");
        ws().onError(session, new RuntimeException("boom"));
        verify(sessions).unregister("sess");
        verify(roomManager).leave("123456", "uuid-1");
    }

    @Test
    void pingWhileJoinedStillPongs() {
        joinAsPlayer();
        ws().onMessage(session, "{\"type\":\"PING\"}");
        assertTrue(lastMessage().contains("PONG"));
    }

    @Test
    void endGameForbiddenWhenRoleMissing() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"END_GAME\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).endGame(anyString());
    }

    @Test
    void createTeamManagerErrorBecomesInternalError() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        when(roomManager.createTeam(anyString(), anyString())).thenThrow(new RuntimeException("x"));
        ws().onMessage(session, "{\"type\":\"CREATE_TEAM\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Internal error"));
    }

    @Test
    void joinSamePinDoesNotLeave() {
        props.put("pin", "123456");
        props.put("playerUuid", "uuid-1");
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-1", "Al", 0, "sess", true);
        when(roomManager.join(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(p);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Al\"}");
        verify(roomManager, never()).leave(anyString(), anyString());
        assertEquals("123456", props.get("pin"));
    }

    private jakarta.websocket.server.ServerEndpointConfig stagedConfig(
            Map<String, java.util.List<String>> headers, java.security.Principal principal) {
        jakarta.websocket.server.ServerEndpointConfig sec =
                org.mockito.Mockito.mock(jakarta.websocket.server.ServerEndpointConfig.class);
        Map<String, Object> secProps = new HashMap<>();
        lenient().when(sec.getUserProperties()).thenReturn(secProps);
        jakarta.websocket.server.HandshakeRequest req =
                org.mockito.Mockito.mock(jakarta.websocket.server.HandshakeRequest.class);
        lenient().when(req.getHeaders()).thenReturn(headers);
        lenient().when(req.getUserPrincipal()).thenReturn(principal);
        lenient().when(req.getHttpSession()).thenReturn(null);
        new SecureHandshakeConfigurator().modifyHandshake(sec, req,
                org.mockito.Mockito.mock(jakarta.websocket.HandshakeResponse.class));
        return sec;
    }

    @Test
    void onOpenConsumesStagedAuth() {
        java.security.Principal principal = org.mockito.Mockito.mock(java.security.Principal.class);
        var sec = stagedConfig(Map.of("X-Forwarded-For", java.util.List.of("1.2.3.4")), principal);
        new GameWebSocket(roomManager, rateLimiter, sessions).onOpen(session, sec);
        assertEquals(Boolean.TRUE, props.get(SecureHandshakeConfigurator.AUTHENTICATED));
        assertEquals("1.2.3.4", props.get(SecureHandshakeConfigurator.REMOTE_ADDR));
    }

    @Test
    void onOpenConsumesUnstagedAsAnonymous() {
        var sec = stagedConfig(Map.of(), null);
        org.mockito.Mockito.clearInvocations(sessions);
        new GameWebSocket(roomManager, rateLimiter, sessions).onOpen(session, sec);
        assertEquals(Boolean.FALSE, props.get(SecureHandshakeConfigurator.AUTHENTICATED));
        assertEquals("unresolved", props.get(SecureHandshakeConfigurator.REMOTE_ADDR));
        verify(sessions).register("sess", session);
    }

    @Test
    void onOpenFallsBackToContainerPrincipal() {
        var sec = stagedConfig(Map.of(), null);
        java.security.Principal principal = org.mockito.Mockito.mock(java.security.Principal.class);
        lenient().when(session.getUserPrincipal()).thenReturn(principal);
        new GameWebSocket(roomManager, rateLimiter, sessions).onOpen(session, sec);
        assertEquals(Boolean.TRUE, props.get(SecureHandshakeConfigurator.AUTHENTICATED));
        assertEquals("unresolved", props.get(SecureHandshakeConfigurator.REMOTE_ADDR));
    }
}
