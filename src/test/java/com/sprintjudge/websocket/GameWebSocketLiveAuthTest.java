package com.sprintjudge.websocket;

import com.sprintjudge.service.GameRoomManager;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Host authority is re-read from the captured HttpSession on every message. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class GameWebSocketLiveAuthTest {

	@Mock
	GameRoomManager roomManager;
	@Mock
	JoinRateLimiter rateLimiter;
	@Mock
	WebSocketSessionManager sessions;
	@Mock
	Session session;

	private final Map<String, Object> props = new HashMap<>();

	@BeforeEach
	void setUp() {
		lenient().when(session.getUserProperties()).thenReturn(props);
		lenient().when(session.getId()).thenReturn("sess");
		lenient().doAnswer(inv -> null).when(sessions).sendRaw(anyString(), anyString());
	}

	private Authentication adminAuth() {
		return new UsernamePasswordAuthenticationToken("admin", "n/a",
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
	}

	private ServerEndpointConfig stagedConfig(MockHttpSession httpSession, Authentication auth) {
		HandshakeRequest request = mock(HandshakeRequest.class);
		when(request.getHeaders()).thenReturn(Map.of());
		when(request.getHttpSession()).thenReturn(httpSession);
		if (auth != null)
			when(request.getUserPrincipal()).thenReturn(auth);
		ServerEndpointConfig sec = mock(ServerEndpointConfig.class);
		when(sec.getUserProperties()).thenReturn(new HashMap<>());
		new SecureHandshakeConfigurator().modifyHandshake(sec, request, mock(HandshakeResponse.class));
		return sec;
	}

	private GameWebSocket open(ServerEndpointConfig sec) {
		GameWebSocket ws = new GameWebSocket(roomManager, rateLimiter, sessions);
		ws.onOpen(session, sec);
		props.put("role", "host");
		props.put("pin", "123456");
		return ws;
	}

	private String lastMessage() {
		ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
		verify(sessions).sendRaw(org.mockito.ArgumentMatchers.eq("sess"), cap.capture());
		return cap.getValue();
	}

	@Test
	void stagedConnectionCarriesCapturedHttpSession() {
		MockHttpSession httpSession = new MockHttpSession();
		ServerEndpointConfig sec = stagedConfig(httpSession, adminAuth());
		SecureHandshakeConfigurator.StagedConnection staged = SecureHandshakeConfigurator.consumeStaged(sec);
		assertSame(httpSession, staged.getHttpSession());
		assertTrue(staged.isAdmin());
	}

	@Test
	void logoutRevokesHostPowersButLiveContextGrantsThem() {
		MockHttpSession httpSession = new MockHttpSession();
		httpSession.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(adminAuth()));
		GameWebSocket ws = open(stagedConfig(httpSession, adminAuth()));

		ws.onMessage(session, "{\"type\":\"END_GAME\"}");
		verify(roomManager).endGame("123456");

		httpSession.removeAttribute("SPRING_SECURITY_CONTEXT");
		clearInvocations(roomManager, sessions);
		ws.onMessage(session, "{\"type\":\"END_GAME\"}");
		verify(roomManager, never()).endGame(anyString());
		assertTrue(lastMessage().contains("Forbidden"), lastMessage());
	}

	@Test
	void anonymousLiveContextRevokesHostPowers() {
		MockHttpSession httpSession = new MockHttpSession();
		httpSession.setAttribute("SPRING_SECURITY_CONTEXT",
				new SecurityContextImpl(new org.springframework.security.authentication.AnonymousAuthenticationToken(
						"key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))));
		GameWebSocket ws = open(stagedConfig(httpSession, adminAuth()));

		ws.onMessage(session, "{\"type\":\"KICK_PLAYER\",\"playerUuid\":\"u-1\"}");
		verify(roomManager, never()).kickPlayer(anyString(), anyString());
		assertTrue(lastMessage().contains("Forbidden"));
	}

	@Test
	void nonAdminLiveContextRevokesHostPowers() {
		MockHttpSession httpSession = new MockHttpSession();
		httpSession.setAttribute("SPRING_SECURITY_CONTEXT",
				new SecurityContextImpl(new UsernamePasswordAuthenticationToken("user", "n/a",
						List.of(new SimpleGrantedAuthority("ROLE_USER")))));
		GameWebSocket ws = open(stagedConfig(httpSession, adminAuth()));

		ws.onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
		verify(roomManager, never()).nextQuestion(anyString());
		assertTrue(lastMessage().contains("Forbidden"));
	}

	@Test
	void liveAdminContextGrantsHostPowersWithoutHandshakeFlag() {
		MockHttpSession httpSession = new MockHttpSession();
		// No auth at handshake; the context becomes admin only afterwards.
		GameWebSocket ws = open(stagedConfig(httpSession, null));
		assertEquals(Boolean.FALSE, props.get(SecureHandshakeConfigurator.ADMIN));
		httpSession.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(adminAuth()));

		ws.onMessage(session, "{\"type\":\"NEXT_QUESTION\"}");
		verify(roomManager).nextQuestion("123456");
	}
}
