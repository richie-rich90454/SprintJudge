package com.sprintjudge.websocket;

import com.sprintjudge.service.GameRoomManager;
import com.sprintjudge.service.Player;
import com.sprintjudge.util.Json;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security behaviors of the game endpoint: schema validation, role guards,
 * rate limiting hooks, payload caps.
 */
@ExtendWith(MockitoExtension.class)
class GameWebSocketSecurityTest {

    @Mock GameRoomManager roomManager;
    @Mock JoinRateLimiter rateLimiter;
    @Mock Session session;
    @Mock RemoteEndpoint.Basic remote;

    private final Map<String, Object> props = new HashMap<>();
    private final Map<String, Object> handshake = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(session.getUserProperties()).thenReturn(props);
        lenient().when(session.getBasicRemote()).thenReturn(remote);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session.getId()).thenReturn("sess");
        lenient().doAnswer(inv -> { inv.getArgument(0); return null; })
                .when(remote).sendText(anyString());
        // Mirror what SecureHandshakeConfigurator stores for an anonymous upgrade.
        handshake.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.FALSE);
        handshake.put(SecureHandshakeConfigurator.REMOTE_ADDR, "unresolved");
        EndpointConfig cfg = org.mockito.Mockito.mock(EndpointConfig.class);
        lenient().when(cfg.getUserProperties()).thenReturn(handshake);
        // getEndpointInstance now resolves via Spring; unit tests construct directly.
        new GameWebSocket(roomManager, rateLimiter).onOpen(session, cfg);
    }

    private GameWebSocket ws() {
        return new GameWebSocket(roomManager, rateLimiter);
    }

    private String lastMessage() {
        try {
            ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
            verify(remote).sendText(cap.capture());
            return cap.getValue();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- lifecycle ----------

    @Test
    void onOpenCopiesHandshakeIdentity() {
        assertEquals(Boolean.FALSE, props.get("oq.authenticated"));
        assertEquals("unresolved", props.get("oq.remoteAddr"));
    }

    @Test
    void authenticatedHandshakeIsFlagged() {
        Map<String, Object> h = new HashMap<>();
        h.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        h.put(SecureHandshakeConfigurator.REMOTE_ADDR, "10.0.0.7");
        EndpointConfig cfg = org.mockito.Mockito.mock(EndpointConfig.class);
        when(cfg.getUserProperties()).thenReturn(h);
        new GameWebSocket(roomManager, rateLimiter).onOpen(session, cfg);
        assertEquals(Boolean.TRUE, props.get("oq.authenticated"));
        assertEquals("10.0.0.7", props.get("oq.remoteAddr"));
    }

    // ---------- schema validation ----------

    @Test
    void malformedJsonIsRejected() {
        ws().onMessage(session, "{nope");
        assertTrue(lastMessage().contains("ERROR"));
    }

    @Test
    void missingTypeIsRejected() {
        ws().onMessage(session, "{\"pin\":\"123456\"}");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void unknownTypeIsRejected() {
        ws().onMessage(session, "{\"type\":\"SELF_DESTRUCT\"}");
        assertTrue(lastMessage().contains("Unknown message type"));
    }

    // ---------- JOIN ----------

    @Test
    void joinRequiresPinAndName() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\"}");
        assertTrue(lastMessage().contains("requires 'pin' and 'name'"));
    }

    @Test
    void unauthenticatedHostJoinIsRefused() {
        props.put("oq.authenticated", Boolean.FALSE);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"role\":\"host\",\"pin\":\"123456\",\"name\":\"H\"}");
        assertTrue(lastMessage().contains("Host role requires admin authentication"));
        verify(rateLimiter, never()).recordSuccess(anyString());
    }

    @Test
    void blockedAddressGetsRateLimitError() {
        when(rateLimiter.tryJoin("unresolved")).thenReturn(false);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Too many attempts"));
    }

    @Test
    void failedJoinRecordsFailureAndReportsError() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        doThrow(new IllegalArgumentException("Invalid PIN")).when(roomManager)
                .join(eq("000000"), anyString(), anyString(), eq("player"));
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"000000\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Invalid PIN"));
        verify(rateLimiter).recordFailure("unresolved");
    }

    @Test
    void successfulJoinStoresIdentityAndReturnsJoined() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-1", "Alice", 0, "sess", true);
        when(roomManager.join(eq("123456"), eq("Alice"), eq("sess"), eq("player"))).thenReturn(p);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Alice\"}");
        assertTrue(lastMessage().contains("\"type\":\"JOINED\""));
        assertEquals("uuid-1", props.get("playerUuid"));
        assertEquals("123456", props.get("pin"));
        assertEquals("player", props.get("role"));
        verify(rateLimiter).recordSuccess("unresolved");
    }

    // ---------- SUBMIT ----------

    private void joinAsPlayer() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-9", "Bob", 0, "s", true);
        lenient().when(roomManager.join(anyString(), anyString(), anyString(), eq("player"))).thenReturn(p);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Bob\"}");
        org.mockito.Mockito.clearInvocations(remote);
    }

    @Test
    void submitBeforeJoinIsRejected() {
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q\",\"response\":{}}");
        assertTrue(lastMessage().contains("Join a room first"));
    }

    @Test
    void submitRequiresQuestionIdAndResponse() {
        joinAsPlayer();
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"\"}");
        assertTrue(lastMessage().contains("requires 'questionId'"));
    }

    @Test
    void submitDefaultsLanguageToPythonWhenAbsent() {
        joinAsPlayer();
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"qz\",\"response\":{\"selectedIndex\":0}}");
        verify(roomManager).submit(eq("123456"), eq("qz"), eq("uuid-9"), eq("python"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void forceSubmitAlsoForbiddenForPlayers() {
        joinAsPlayer();
        ws().onMessage(session, "{\"type\":\"FORCE_SUBMIT\"}");
        assertTrue(lastMessage().contains("Forbidden"));
    }

    @Test
    void nextQuestionForwardedByHostSession() {
        props.put("oq.authenticated", Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
        verify(roomManager).nextQuestion("123456");
    }

    @Test
    void endGameForwardedByHostSession() {
        props.put("oq.authenticated", Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"END_GAME\"}");
        verify(roomManager).endGame("123456");
    }

    @Test
    void submitRejectsUnknownLanguage() {
        joinAsPlayer();
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"ruby\",\"response\":{}}");
        assertTrue(lastMessage().contains("Unsupported language"));
    }

    @Test
    void submitRejectsOversizedSource() {
        joinAsPlayer();
        String big = "x".repeat(70_000);
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"python\","
                + "\"response\":{\"source\":\"" + big + "\"}}");
        assertTrue(lastMessage().contains("64KB limit"));
        verify(roomManager, never()).submit(anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    // ---------- host commands ----------

    @Test
    void adminCommandsAreForbiddenForPlayers() {
        joinAsPlayer();
        ws().onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).nextQuestion(anyString());
    }

    @Test
    void hostCommandRequiresHostRoleEvenWhenAuthenticated() {
        props.put("oq.authenticated", Boolean.TRUE);   // authed but never joined as host
        ws().onMessage(session, "{\"type\":\"END_GAME\"}");
        assertTrue(lastMessage().contains("Forbidden"));
    }

    @Test
    void extendTimerSecondsAreClampedHigh() {
        props.put("oq.authenticated", Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"EXTEND_TIMER\",\"seconds\":99999}");
        verify(roomManager).extendTimer("123456", 300);
    }

    @Test
    void extendTimerSecondsAreClampedLow() {
        props.put("oq.authenticated", Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"EXTEND_TIMER\",\"seconds\":-50}");
        verify(roomManager).extendTimer("123456", 1);
    }

    @Test
    void kickForwardsPlayerUuidForHost() {
        props.put("oq.authenticated", Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"KICK_PLAYER\",\"playerUuid\":\"u-42\"}");
        verify(roomManager).kickPlayer("123456", "u-42");
    }
}
