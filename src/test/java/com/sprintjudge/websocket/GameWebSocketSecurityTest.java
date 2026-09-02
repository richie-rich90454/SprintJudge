package com.sprintjudge.websocket;

import com.sprintjudge.domain.dto.RoomState;
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

/**
 * Security behaviors of the game endpoint: schema validation, role guards,
 * rate limiting hooks, payload caps.
 */
@ExtendWith(MockitoExtension.class)
class GameWebSocketSecurityTest {

    @Mock GameRoomManager roomManager;
    @Mock JoinRateLimiter rateLimiter;
    @Mock WebSocketSessionManager sessions;
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
        // Capture sendRaw calls on the sessions mock
        lenient().doAnswer(inv -> null)
                .when(sessions).sendRaw(eq("sess"), anyString());
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

    // ---------- lifecycle ----------

    @Test
    void onOpenCopiesHandshakeIdentity() {
        // When config is not ServerEndpointConfig (test mock), falls back to "unresolved"
        assertEquals(Boolean.FALSE, props.get("oq.authenticated"));
        assertEquals("unresolved", props.get("oq.remoteAddr"));
    }

    @Test
    void onOpenRegistersSessionForBroadcast() {
        verify(sessions).register("sess", session);
    }

    @Test
    void onCloseUnregistersBeforeLeavingRoom() {
        props.put("pin", "123456");
        props.put("playerUuid", "uuid-1");
        ws().onClose(session);
        verify(sessions).unregister("sess");
        verify(roomManager).leave("123456", "uuid-1");
    }

    @Test
    void authenticatedHandshakeIsFlagged() {
        java.security.Principal principal = org.mockito.Mockito.mock(java.security.Principal.class);
        when(session.getUserPrincipal()).thenReturn(principal);
        Map<String, Object> h = new HashMap<>();
        h.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        h.put(SecureHandshakeConfigurator.REMOTE_ADDR, "10.0.0.7");
        EndpointConfig cfg = org.mockito.Mockito.mock(EndpointConfig.class);
        lenient().when(cfg.getUserProperties()).thenReturn(h);
        new GameWebSocket(roomManager, rateLimiter, sessions).onOpen(session, cfg);
        assertEquals(Boolean.TRUE, props.get("oq.authenticated"));
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
                .join(eq("000000"), anyString(), anyString(), eq("player"), any());
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"000000\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Invalid PIN"));
        verify(rateLimiter).recordFailure("unresolved");
    }

    @Test
    void successfulJoinStoresIdentityAndReturnsJoined() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-1", "Alice", 0, "sess", true);
        when(roomManager.join(eq("123456"), eq("Alice"), eq("sess"), eq("player"), any())).thenReturn(p);
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
        lenient().when(roomManager.join(anyString(), anyString(), anyString(), eq("player"), any())).thenReturn(p);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Bob\"}");
        org.mockito.Mockito.clearInvocations(sessions);
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
        verify(roomManager).submit(eq("123456"), eq("qz"), eq("uuid-9"), eq("python"), any());
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
        verify(roomManager, never()).submit(anyString(), anyString(), anyString(), anyString(), any());
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
        props.put("oq.authenticated", Boolean.TRUE);
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

    // ---------- schema validation (remaining branches) ----------

    @Test
    void nonObjectMessageRejected() {
        ws().onMessage(session, "\"just a string\"");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void blankTypeRejected() {
        ws().onMessage(session, "{\"type\":\"  \"}");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    // ---------- onOpen (remaining branch) ----------

    @Test
    void onOpenWithoutRemoteAddrFallsBackToUnresolved() {
        Map<String, Object> h = new HashMap<>();
        h.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.FALSE);
        EndpointConfig cfg = org.mockito.Mockito.mock(EndpointConfig.class);
        lenient().when(cfg.getUserProperties()).thenReturn(h);
        new GameWebSocket(roomManager, rateLimiter, sessions).onOpen(session, cfg);
        assertEquals("unresolved", props.get(SecureHandshakeConfigurator.REMOTE_ADDR));
    }

    // ---------- JOIN (remaining branches) ----------

    @Test
    void joinMissingPinRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("requires 'pin' and 'name'"));
    }

    @Test
    void authenticatedHostJoinSucceeds() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-host", "Host", 0, "sess", true);
        when(roomManager.join(eq("123456"), eq("Host"), eq("sess"), eq("host"), any())).thenReturn(p);
        when(roomManager.getRoomState(anyString())).thenReturn(
                new RoomState("ROOM", "LOBBY", 0, null, java.util.List.of(), "STANDARD"));
        ws().onMessage(session, "{\"type\":\"JOIN\",\"role\":\"host\",\"pin\":\"123456\",\"name\":\"Host\"}");
        assertTrue(lastMessage().contains("\"type\":\"JOINED\""));
        assertEquals("host", props.get("role"));
        verify(rateLimiter).recordSuccess("unresolved");
    }

    @Test
    void joinReclaimsPreviousSeatBeforeRejoining() {
        props.put("pin", "oldpin");
        props.put("playerUuid", "olduuid");
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-new", "Al", 0, "sess", true);
        when(roomManager.join(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(p);
        when(roomManager.getRoomState(anyString())).thenReturn(
                new RoomState("ROOM", "LOBBY", 0, null, java.util.List.of(), "STANDARD"));
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Al\"}");
        verify(roomManager).leave("oldpin", "olduuid");
        verify(roomManager).join(eq("123456"), eq("Al"), eq("sess"), eq("player"), any());
    }

    @Test
    void joinReclaimLeaveFailureIsSwallowed() {
        props.put("pin", "oldpin");
        props.put("playerUuid", "olduuid");
        doThrow(new RuntimeException("leave failed")).when(roomManager).leave("oldpin", "olduuid");
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-new", "Al", 0, "sess", true);
        when(roomManager.join(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(p);
        when(roomManager.getRoomState(anyString())).thenReturn(
                new RoomState("ROOM", "LOBBY", 0, null, java.util.List.of(), "STANDARD"));
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Al\"}");
        assertTrue(lastMessage().contains("\"type\":\"JOINED\""));
    }

    @Test
    void failedJoinIllegalStateExceptionRecordsFailure() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        doThrow(new IllegalStateException("room closed")).when(roomManager)
                .join(eq("123456"), anyString(), anyString(), eq("player"), any());
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Al\"}");
        assertTrue(lastMessage().contains("room closed"));
        verify(rateLimiter).recordFailure("unresolved");
    }

    // ---------- SUBMIT (remaining branches) ----------

    @Test
    void submitMissingQuestionIdButResponsePresentRejected() {
        joinAsPlayer();
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"response\":{\"selectedIndex\":0}}");
        assertTrue(lastMessage().contains("requires 'questionId'"));
    }

    @Test
    void submitWithUuidButNoPinRejected() {
        props.put("playerUuid", "uuid-9");
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q\",\"response\":{}}");
        assertTrue(lastMessage().contains("Join a room first"));
    }

    @Test
    void submitWithPinButNoUuidRejected() {
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q\",\"response\":{}}");
        assertTrue(lastMessage().contains("Join a room first"));
    }

    @Test
    void submitPassesThroughExplicitLanguage() {
        joinAsPlayer();
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"java\",\"response\":{\"selectedIndex\":0}}");
        verify(roomManager).submit(eq("123456"), eq("q1"), eq("uuid-9"), eq("java"), any());
    }

    // ---------- host guard (remaining branch) ----------

    @Test
    void hostRoleWithoutAuthForbiddenAtHostGuard() {
        props.put("role", "host");
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.FALSE);
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).nextQuestion(anyString());
    }

    // ---------- remaining message types ----------

    @Test
    void pingReturnsPong() {
        ws().onMessage(session, "{\"type\":\"PING\"}");
        assertTrue(lastMessage().contains("\"type\":\"PONG\""));
    }

    @Test
    void resyncWithoutPinRejected() {
        ws().onMessage(session, "{\"type\":\"RESYNC_LEADERBOARD\"}");
        assertTrue(lastMessage().contains("Join a room first"));
    }

    @Test
    void resyncForwardsFullLeaderboard() {
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"RESYNC_LEADERBOARD\"}");
        verify(roomManager).sendFullLeaderboard("123456", "sess");
    }

    @Test
    void extendTimerDefaultsTo30WhenSecondsMissing() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"EXTEND_TIMER\"}");
        verify(roomManager).extendTimer("123456", 30);
    }

    @Test
    void kickDefaultsToEmptyUuidWhenMissing() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"KICK_PLAYER\"}");
        verify(roomManager).kickPlayer("123456", "");
    }

    // ---------- send() branches ----------

    @Test
    void sendSwallowsException() {
        doThrow(new RuntimeException("closed")).when(sessions).sendRaw(anyString(), anyString());
        ws().onMessage(session, "{\"type\":\"PING\"}");
        // Should not throw
    }

    // ---------- lifecycle (remaining branch) ----------

    @Test
    void onCloseWithoutPinSkipsLeave() {
        ws().onClose(session);
        verify(sessions).unregister("sess");
        verify(roomManager, never()).leave(anyString(), anyString());
    }

    @Test
    void onErrorIsNoop() {
        ws().onError(session, new RuntimeException("x"));
    }

    // ---------- additional branch coverage (onMessage) ----------

    @Test
    void submitWithSmallSourceForwardsToManager() {
        joinAsPlayer();
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"python\","
                + "\"response\":{\"source\":\"print(1)\"}}");
        verify(roomManager).submit(eq("123456"), eq("q1"), eq("uuid-9"), eq("python"), any());
    }

    @Test
    void onCloseWithPinButNoUuidSkipsLeave() {
        props.put("pin", "123456");
        ws().onClose(session);
        verify(sessions).unregister("sess");
        verify(roomManager, never()).leave(anyString(), anyString());
    }

    @Test
    void joinWithPrevPinButNoPrevUuidSkipsLeave() {
        props.put("pin", "oldpin");
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-new", "Al", 0, "sess", true);
        when(roomManager.join(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(p);
        when(roomManager.getRoomState(anyString())).thenReturn(
                new RoomState("ROOM", "LOBBY", 0, null, java.util.List.of(), "STANDARD"));
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Al\"}");
        verify(roomManager, never()).leave(anyString(), anyString());
        verify(roomManager).join(eq("123456"), eq("Al"), eq("sess"), eq("player"), any());
    }

    @Test
    void forceSubmitForwardedByHostSession() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"FORCE_SUBMIT\"}");
        verify(roomManager).forceSubmit("123456");
    }

    @Test
    void submitWithQuestionIdButNoResponseRejected() {
        joinAsPlayer();
        // questionId is non-blank so the second operand of the || is actually evaluated
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q1\"}");
        assertTrue(lastMessage().contains("requires 'questionId'"));
    }
}
