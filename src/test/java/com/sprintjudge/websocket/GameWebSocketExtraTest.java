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

    private void mxJoinPlayer(String pin, String name) {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-9", name, 0, "s", true);
        lenient().when(roomManager.join(eq(pin), eq(name), anyString(), eq("player"), any()))
                .thenReturn(p);
        ws().onMessage(session,
                "{\"type\":\"JOIN\",\"pin\":\"" + pin + "\",\"name\":\"" + name + "\"}");
        org.mockito.Mockito.clearInvocations(sessions);
    }

    private void mxJoinHost() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-h", "Host", 0, "s", true);
        lenient().when(roomManager.join(eq("123456"), eq("Host"), anyString(), eq("host"), any()))
                .thenReturn(p);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Host\",\"role\":\"host\"}");
        org.mockito.Mockito.clearInvocations(sessions);
    }

    private java.util.List<String> mxAllMessages() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(sessions, org.mockito.Mockito.atLeastOnce()).sendRaw(eq("sess"), cap.capture());
        return cap.getAllValues();
    }

    @Test
    void mxPlayerJoinSubmitHappyFlow() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"python\",\"response\":{\"choice\":\"A\"}}");
        verify(roomManager).submit(eq("123456"), eq("q1"), eq("uuid-9"), eq("python"), any());
    }

    @Test
    void mxHostNextForceEndSequence() {
        mxJoinHost();
        ws().onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
        ws().onMessage(session, "{\"type\":\"FORCE_SUBMIT\"}");
        ws().onMessage(session, "{\"type\":\"END_GAME\"}");
        verify(roomManager).nextQuestion("123456");
        verify(roomManager).forceSubmit("123456");
        verify(roomManager).endGame("123456");
    }

    @Test
    void mxPlayerNextQuestionForbidden() {
        mxJoinPlayer("123456", "Bob");
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        ws().onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).nextQuestion(anyString());
    }

    @Test
    void mxPlayerForceSubmitForbidden() {
        mxJoinPlayer("123456", "Bob");
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        ws().onMessage(session, "{\"type\":\"FORCE_SUBMIT\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).forceSubmit(anyString());
    }

    @Test
    void mxPlayerEndGameForbidden() {
        mxJoinPlayer("123456", "Bob");
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        ws().onMessage(session, "{\"type\":\"END_GAME\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).endGame(anyString());
    }

    @Test
    void mxUnauthenticatedHostNextForbidden() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.FALSE);
        props.put("role", "host");
        props.put("pin", "123456");
        ws().onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
        assertTrue(lastMessage().contains("Forbidden"));
        verify(roomManager, never()).nextQuestion(anyString());
    }

    @Test
    void mxTruncatedJsonIsInternalError() {
        ws().onMessage(session, "{\"type\":\"JOIN\",");
        assertTrue(lastMessage().contains("Internal error"));
    }

    @Test
    void mxEmptyStringIsMissingType() {
        ws().onMessage(session, "");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void mxNumberMessageIsMalformed() {
        ws().onMessage(session, "42");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void mxStringMessageIsMalformed() {
        ws().onMessage(session, "\"hello\"");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void mxEmptyObjectIsMalformed() {
        ws().onMessage(session, "{}");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void mxBlankTypeIsMalformed() {
        ws().onMessage(session, "{\"type\":\"  \"}");
        assertTrue(lastMessage().contains("missing 'type'"));
    }

    @Test
    void mxUnknownTypeReportsName() {
        ws().onMessage(session, "{\"type\":\"FROBNICATE\"}");
        assertTrue(lastMessage().contains("Unknown message type"));
        assertTrue(lastMessage().contains("FROBNICATE"));
    }

    @Test
    void mxJoinBlankPinRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("JOIN requires"));
        verify(rateLimiter, never()).tryJoin(anyString());
    }

    @Test
    void mxJoinBlankNameRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"   \"}");
        assertTrue(lastMessage().contains("JOIN requires"));
        verify(rateLimiter, never()).tryJoin(anyString());
    }

    @Test
    void mxJoinMissingPinRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("JOIN requires"));
        verify(rateLimiter, never()).tryJoin(anyString());
    }

    @Test
    void mxJoinMissingNameRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\"}");
        assertTrue(lastMessage().contains("JOIN requires"));
        verify(rateLimiter, never()).tryJoin(anyString());
    }

    @Test
    void mxJoinNullPinRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":null,\"name\":\"A\"}");
        assertTrue(lastMessage().contains("JOIN requires"));
    }

    @Test
    void mxJoinSixSpacesPinRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"      \",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("JOIN requires"));
    }

    @Test
    void mxJoinFiveDigitsPlusLetterRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"12345a\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Invalid PIN"));
    }

    @Test
    void mxJoinLetterPlusFiveDigitsRejected() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"a12345\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Invalid PIN"));
    }

    @Test
    void mxJoinZeroPinShapeAccepted() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-0", "Zed", 0, "s", true);
        when(roomManager.join(eq("000000"), eq("Zed"), anyString(), eq("player"), any())).thenReturn(p);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"000000\",\"name\":\"Zed\"}");
        assertTrue(lastMessage().contains("JOINED"));
    }

    @Test
    void mxHostJoinAuthenticatedHappy() {
        props.put(SecureHandshakeConfigurator.AUTHENTICATED, Boolean.TRUE);
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player p = new Player("uuid-h", "Host", 0, "s", true);
        when(roomManager.join(eq("123456"), eq("Host"), anyString(), eq("host"), any())).thenReturn(p);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"Host\",\"role\":\"host\"}");
        assertTrue(lastMessage().contains("JOINED"));
        assertEquals("host", props.get("role"));
    }

    @Test
    void mxHostJoinUnauthenticatedRefused() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"H\",\"role\":\"host\"}");
        assertTrue(lastMessage().contains("Host role requires admin"));
        verify(rateLimiter, never()).tryJoin(anyString());
    }

    @Test
    void mxHostJoinUppercaseRoleRefusedWhenAnonymous() {
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"H\",\"role\":\"HOST\"}");
        assertTrue(lastMessage().contains("Host role requires admin"));
    }

    @Test
    void mxJoinRateLimitedSendsError() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(false);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"123456\",\"name\":\"A\"}");
        assertTrue(lastMessage().contains("Too many attempts"));
        verify(roomManager, never()).join(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void mxDoubleJoinSwitchesRoomAndLeavesOld() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player first = new Player("uuid-9", "Bob", 0, "s", true);
        when(roomManager.join(eq("111111"), eq("Bob"), anyString(), eq("player"), any())).thenReturn(first);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"111111\",\"name\":\"Bob\"}");
        Player second = new Player("uuid-9", "Bob", 0, "s", true);
        when(roomManager.join(eq("222222"), eq("Bob"), anyString(), eq("player"), any())).thenReturn(second);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"222222\",\"name\":\"Bob\"}");
        verify(roomManager).leave("111111", "uuid-9");
        assertEquals("222222", props.get("pin"));
    }

    @Test
    void mxFailedSecondJoinKeepsOldSeat() {
        when(rateLimiter.tryJoin(anyString())).thenReturn(true);
        Player first = new Player("uuid-9", "Bob", 0, "s", true);
        when(roomManager.join(eq("111111"), eq("Bob"), anyString(), eq("player"), any())).thenReturn(first);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"111111\",\"name\":\"Bob\"}");
        org.mockito.Mockito.clearInvocations(roomManager);
        when(roomManager.join(eq("222222"), eq("Bob"), anyString(), eq("player"), any()))
                .thenThrow(new IllegalArgumentException("room full"));
        lenient().when(roomManager.join(eq("111111"), eq("Bob"), anyString(), eq("player"), any()))
                .thenReturn(first);
        ws().onMessage(session, "{\"type\":\"JOIN\",\"pin\":\"222222\",\"name\":\"Bob\"}");
        verify(roomManager, never()).leave(anyString(), anyString());
    }

    @Test
    void mxSubmitUnsupportedLanguageRejected() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"ruby\",\"response\":{}}");
        assertTrue(lastMessage().contains("Unsupported language"));
        verify(roomManager, never()).submit(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void mxSubmitGoLanguageRejected() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"go\",\"response\":{}}");
        assertTrue(lastMessage().contains("Unsupported language"));
    }

    @Test
    void mxSubmitUppercaseLanguageAccepted() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"PYTHON\",\"response\":{}}");
        verify(roomManager).submit(eq("123456"), eq("q1"), eq("uuid-9"), eq("python"), any());
    }

    @Test
    void mxSubmitOversizedSourceRejected() {
        mxJoinPlayer("123456", "Bob");
        String big = "x".repeat(65537);
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"python\","
                + "\"response\":{\"source\":\"" + big + "\"}}");
        assertTrue(lastMessage().contains("Source exceeds 64KB"));
        verify(roomManager, never()).submit(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void mxSubmitBoundarySourceAccepted() {
        mxJoinPlayer("123456", "Bob");
        String edge = "x".repeat(65536);
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"python\","
                + "\"response\":{\"source\":\"" + edge + "\"}}");
        verify(roomManager).submit(eq("123456"), eq("q1"), eq("uuid-9"), eq("python"), any());
    }

    @Test
    void mxSubmitChoiceResponseWithoutSourceForwards() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"python\",\"response\":{\"choice\":\"B\"}}");
        verify(roomManager).submit(eq("123456"), eq("q1"), eq("uuid-9"), eq("python"), any());
    }

    @Test
    void mxSubmitMissingQuestionIdRejected() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"language\":\"python\",\"response\":{}}");
        assertTrue(lastMessage().contains("SUBMIT requires"));
    }

    @Test
    void mxSubmitMissingResponseRejected() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session, "{\"type\":\"SUBMIT\",\"questionId\":\"q1\"}");
        assertTrue(lastMessage().contains("SUBMIT requires"));
    }

    @Test
    void mxSubmitBeforeJoinRejected() {
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q1\",\"language\":\"python\",\"response\":{}}");
        assertTrue(lastMessage().contains("Join a room first"));
        verify(roomManager, never()).submit(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void mxOnCloseWithoutJoinUnregistersOnly() {
        org.mockito.Mockito.clearInvocations(sessions);
        ws().onClose(session);
        verify(sessions).unregister("sess");
        verify(roomManager, never()).leave(anyString(), anyString());
    }

    @Test
    void mxOnCloseAfterJoinLeavesRoom() {
        mxJoinPlayer("123456", "Bob");
        org.mockito.Mockito.clearInvocations(sessions);
        ws().onClose(session);
        verify(sessions).unregister("sess");
        verify(roomManager).leave("123456", "uuid-9");
    }

    @Test
    void mxOnErrorWithoutJoinUnregistersOnly() {
        org.mockito.Mockito.clearInvocations(sessions);
        ws().onError(session, new RuntimeException("net-reset"));
        verify(sessions).unregister("sess");
        verify(roomManager, never()).leave(anyString(), anyString());
    }

    @Test
    void mxOnErrorAfterJoinLeavesRoom() {
        mxJoinPlayer("123456", "Bob");
        org.mockito.Mockito.clearInvocations(sessions);
        org.mockito.Mockito.clearInvocations(roomManager);
        ws().onError(session, new RuntimeException("net-reset"));
        verify(sessions).unregister("sess");
        verify(roomManager).leave("123456", "uuid-9");
    }

    @Test
    void mxPingBeforeJoinStillPongs() {
        ws().onMessage(session, "{\"type\":\"PING\"}");
        assertTrue(lastMessage().contains("PONG"));
    }

    @Test
    void mxPingPongExactType() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session, "{\"type\":\"PING\"}");
        assertTrue(lastMessage().contains("\"type\":\"PONG\""));
    }

    @Test
    void mxGetTeamsBeforeJoinRejected() {
        ws().onMessage(session, "{\"type\":\"GET_TEAMS\"}");
        assertTrue(lastMessage().contains("Join a room first"));
        verify(roomManager, never()).getTeams(anyString());
    }

    @Test
    void mxGetTeamsAfterJoinListsTeams() {
        mxJoinPlayer("123456", "Bob");
        when(roomManager.getTeams("123456")).thenReturn(
                java.util.List.of(new com.sprintjudge.service.GameRoom.Team("t1", "A",
                        java.util.Set.of(), 0)));
        ws().onMessage(session, "{\"type\":\"GET_TEAMS\"}");
        assertTrue(lastMessage().contains("TEAM_LIST"));
    }

    @Test
    void mxGetBracketBeforeJoinRejected() {
        ws().onMessage(session, "{\"type\":\"GET_BRACKET\"}");
        assertTrue(lastMessage().contains("Join a room first"));
        verify(roomManager, never()).getBracket(anyString());
    }

    @Test
    void mxGetBracketAfterJoinSendsBracket() {
        mxJoinPlayer("123456", "Bob");
        when(roomManager.getBracket("123456"))
                .thenReturn(java.util.List.<String[]>of(new String[]{"a", "b"}));
        ws().onMessage(session, "{\"type\":\"GET_BRACKET\"}");
        assertTrue(lastMessage().contains("BRACKET"));
    }

    @Test
    void mxGetBracketManagerErrorBecomesInternalError() {
        props.put("pin", "123456");
        when(roomManager.getBracket("123456")).thenThrow(new RuntimeException("db"));
        ws().onMessage(session, "{\"type\":\"GET_BRACKET\"}");
        assertTrue(lastMessage().contains("Internal error"));
    }

    @Test
    void mxResyncBeforeJoinRejected() {
        ws().onMessage(session, "{\"type\":\"RESYNC_LEADERBOARD\"}");
        assertTrue(lastMessage().contains("Join a room first"));
        verify(roomManager, never()).sendFullLeaderboard(anyString(), anyString());
    }

    @Test
    void mxResyncAfterJoinForwards() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session, "{\"type\":\"RESYNC_LEADERBOARD\"}");
        verify(roomManager).sendFullLeaderboard("123456", "sess");
    }

    @Test
    void mxKickAfterHostJoinForwards() {
        mxJoinHost();
        ws().onMessage(session, "{\"type\":\"KICK_PLAYER\",\"playerUuid\":\"uuid-9\"}");
        verify(roomManager).kickPlayer("123456", "uuid-9");
    }

    @Test
    void mxExtendTimerAfterHostJoinForwards() {
        mxJoinHost();
        ws().onMessage(session, "{\"type\":\"EXTEND_TIMER\",\"seconds\":45}");
        verify(roomManager).extendTimer("123456", 45);
    }

    @Test
    void mxExtendTimerClampedTo300() {
        mxJoinHost();
        ws().onMessage(session, "{\"type\":\"EXTEND_TIMER\",\"seconds\":99999}");
        verify(roomManager).extendTimer("123456", 300);
    }

    @Test
    void mxPlayerSequenceJoinSubmitPing() {
        mxJoinPlayer("123456", "Bob");
        ws().onMessage(session,
                "{\"type\":\"SUBMIT\",\"questionId\":\"q7\",\"language\":\"node\",\"response\":{}}");
        ws().onMessage(session, "{\"type\":\"PING\"}");
        verify(roomManager).submit(eq("123456"), eq("q7"), eq("uuid-9"), eq("node"), any());
        assertTrue(mxAllMessages().toString().contains("PONG"));
    }
}
