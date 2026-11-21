package com.sprintjudge.websocket;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class SecureHandshakeConfiguratorExtraTest {

	@AfterEach
	void clearContext() throws Exception {
		setContext(null);
	}

	private static void setContext(Object value) throws Exception {
		Field field = SecureHandshakeConfigurator.class.getDeclaredField("context");
		field.setAccessible(true);
		field.set(null, value);
	}

	private static GenericWebApplicationContext contextWith(String property, String value) {
		MockEnvironment env = new MockEnvironment();
		env.setProperty(property, value);
		GenericWebApplicationContext ctx = new GenericWebApplicationContext(new MockServletContext());
		ctx.setEnvironment(env);
		ctx.refresh();
		return ctx;
	}

	@Test
	void noSpringContextMeansNoExplicitAllowlist() throws Exception {
		setContext(null);
		assertEquals("", SecureHandshakeConfigurator.allowedOrigins());
		assertFalse(SecureHandshakeConfigurator.originAllowed("https://other.example", "quiz.example.org"));
	}

	@Test
	void nullEnvironmentPropertyIsTreatedAsEmptyAllowlist() throws Exception {
		GenericWebApplicationContext ctx = new GenericWebApplicationContext(new MockServletContext());
		ctx.refresh();
		setContext(ctx);
		assertEquals("", SecureHandshakeConfigurator.allowedOrigins());
	}

	@Test
	void springConfiguredOriginPropertyIsHonoured() throws Exception {
		org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
		env.setProperty("sprintjudge.cors.allowed-origins", "https://configured.example");
		env.setProperty("SPRINTJUDGE_CORS_ALLOWED_ORIGINS", "https://raw.example");
		GenericWebApplicationContext ctx = new GenericWebApplicationContext(new MockServletContext());
		ctx.setEnvironment(env);
		ctx.refresh();
		setContext(ctx);
		assertEquals("https://configured.example", SecureHandshakeConfigurator.allowedOrigins());
		assertTrue(SecureHandshakeConfigurator.originAllowed("https://configured.example", "quiz.example.org"));
	}

	@Test
	void springAuthenticationPrincipalGrantsHostRole() throws Exception {
		org.springframework.security.core.Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
				"admin", "secret",
				List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
		jakarta.websocket.server.HandshakeRequest request = org.mockito.Mockito
				.mock(jakarta.websocket.server.HandshakeRequest.class);
		org.mockito.Mockito.when(request.getHeaders()).thenReturn(Map.of("X-Forwarded-For", List.of("1.2.3.4")));
		org.mockito.Mockito.when(request.getUserPrincipal()).thenReturn(auth);
		java.util.Map<String, Object> props = new java.util.HashMap<>();
		jakarta.websocket.server.ServerEndpointConfig sec = org.mockito.Mockito
				.mock(jakarta.websocket.server.ServerEndpointConfig.class);
		org.mockito.Mockito.when(sec.getUserProperties()).thenReturn(props);
		new SecureHandshakeConfigurator().modifyHandshake(sec, request,
				org.mockito.Mockito.mock(jakarta.websocket.HandshakeResponse.class));
		assertEquals(Boolean.TRUE, props.get(SecureHandshakeConfigurator.ADMIN));
		com.sprintjudge.websocket.SecureHandshakeConfigurator.StagedConnection staged = com.sprintjudge.websocket.SecureHandshakeConfigurator
				.consumeStaged(sec);
		assertEquals("1.2.3.4", staged.getIp());
	}

	@Test
	void explicitAllowlistMatchesFullOrigin() throws Exception {
		setContext(
				contextWith("SPRINTJUDGE_CORS_ALLOWED_ORIGINS", "https://app.example.org, https://admin.example.org"));
		assertTrue(SecureHandshakeConfigurator.originAllowed("https://app.example.org", "quiz.example.org"));
		assertTrue(SecureHandshakeConfigurator.originAllowed("https://admin.example.org", "quiz.example.org"));
		assertFalse(SecureHandshakeConfigurator.originAllowed("https://evil.example", "quiz.example.org"));
	}

	@Test
	void nullOriginHeaderAndEmptyAllowlistEntriesAreIgnored() throws Exception {
		setContext(contextWith("SPRINTJUDGE_CORS_ALLOWED_ORIGINS", ", ,"));
		assertFalse(SecureHandshakeConfigurator.originAllowed("https://other.example", "quiz.example.org"));
		assertFalse(SecureHandshakeConfigurator.originAllowed("https://quiz.example.org:443", null));
		assertFalse(SecureHandshakeConfigurator.originAllowed("https://quiz.example.org:443", "   "));
	}

	@Test
	void malformedOriginIsRejected() {
		assertFalse(SecureHandshakeConfigurator.originAllowed("://broken", "quiz.example.org"));
		assertFalse(SecureHandshakeConfigurator.originAllowed("http://", "quiz.example.org"));
		assertFalse(SecureHandshakeConfigurator.originAllowed("mailto:someone@example.org", "quiz.example.org"));
		try {
			SecureHandshakeConfigurator.originAllowed("http://[bad", "quiz.example.org");
		} catch (RuntimeException ignored) {
			// URI.create may throw; both paths must fail closed.
		}
	}

	@Test
	void sameHostAndLoopbackOriginsAreAllowed() {
		assertTrue(SecureHandshakeConfigurator.originAllowed("https://quiz.example.org", "quiz.example.org:443"));
		assertTrue(SecureHandshakeConfigurator.originAllowed("http://localhost:5173", "127.0.0.1:8080"));
		assertTrue(SecureHandshakeConfigurator.originAllowed("http://127.0.0.1:3000", "quiz.example.org"));
	}

	@Test
	void headerHelpersHandleMissingAndEmptyValues() throws Exception {
		jakarta.websocket.server.HandshakeRequest request = org.mockito.Mockito
				.mock(jakarta.websocket.server.HandshakeRequest.class);
		org.mockito.Mockito.when(request.getHeaders())
				.thenReturn(Map.of("Origin", List.of(), "Host", List.of("quiz.example.org")));
		java.lang.reflect.Method first = SecureHandshakeConfigurator.class.getDeclaredMethod("firstHeader",
				jakarta.websocket.server.HandshakeRequest.class, String.class);
		first.setAccessible(true);
		java.lang.reflect.Method last = SecureHandshakeConfigurator.class.getDeclaredMethod("lastHeader",
				jakarta.websocket.server.HandshakeRequest.class, String.class);
		last.setAccessible(true);
		assertEquals(null, first.invoke(new SecureHandshakeConfigurator(), request, "Origin"));
		assertEquals(null, last.invoke(new SecureHandshakeConfigurator(), request, "Missing"));
	}
}
