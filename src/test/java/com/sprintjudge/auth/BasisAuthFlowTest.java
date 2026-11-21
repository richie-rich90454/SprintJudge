package com.sprintjudge.auth;

import java.util.Map;

import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class BasisAuthFlowTest {

	private static final String ISSUER = "https://auth.example.org";
	private static final String AUDIENCE = "urn:sprintjudge:api";

	private BasisTokenVerifier verifier(RSAKey key) {
		return new BasisTokenVerifier(() -> BasisTestJwt.jwks(key), ISSUER, AUDIENCE);
	}

	private BasisBackchannelController controller(BasisTokenVerifier verifier, BasisSessionService sessions,
			BasisRevocationStore revocations) {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("BASIS_AUTH_RESOURCE", AUDIENCE);
		env.setProperty("BASIS_AUTH_RESOURCE_SECRET", "resource-secret");
		BasisInternalClient internal = (resource, secret, nonce, hash) -> assertNotNull(hash);
		return new BasisBackchannelController(verifier, sessions, revocations, internal, env);
	}

	@Test
	void webhookTestRespondsAcceptedAndDeliversProof() throws Exception {
		BasisBackchannelController controller = controller(verifier(BasisTestJwt.key()), new BasisSessionService(),
				new BasisRevocationStore());
		assertEquals(202,
				controller.receive(Map.of("action", "test", "nonce", "n", "challenge", "c")).getStatusCode().value());
		assertEquals(202, controller.receive(Map.of("action", "test")).getStatusCode().value());
		assertEquals(202, controller.receive(Map.of("action", "test", "nonce", "n")).getStatusCode().value());
	}

	@Test
	void logoutTokenEndsLocalSessions() throws Exception {
		RSAKey key = BasisTestJwt.key();
		BasisSessionService sessions = new BasisSessionService();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestedSessionId("s1");
		HttpSession created = request.getSession();
		sessions.sessionCreated(new jakarta.servlet.http.HttpSessionEvent(created));
		sessions.register(created.getId(), "user-1");
		assertEquals(1, sessions.sessionCount("user-1"));
		String token = BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "user-1",
				Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
		BasisBackchannelController controller = controller(verifier(key), sessions, new BasisRevocationStore());
		assertEquals(204, controller.receive(Map.of("logout_token", token)).getStatusCode().value());
		assertEquals(0, sessions.sessionCount("user-1"));
	}

	@Test
	void logoutTokenValidationFailures() throws Exception {
		RSAKey key = BasisTestJwt.key();
		BasisBackchannelController controller = controller(verifier(key), new BasisSessionService(),
				new BasisRevocationStore());
		assertEquals(401, controller.receive(Map.of("logout_token", "garbage")).getStatusCode().value());
		String noEvent = BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of());
		assertEquals(400, controller.receive(Map.of("logout_token", noEvent)).getStatusCode().value());
		String noSub = BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, null,
				Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
		assertEquals(400, controller.receive(Map.of("logout_token", noSub)).getStatusCode().value());
	}

	@Test
	void revocationDeniesJtiAndResourceEndpointRejectsIt() throws Exception {
		RSAKey key = BasisTestJwt.key();
		BasisRevocationStore store = new BasisRevocationStore();
		BasisBackchannelController controller = controller(verifier(key), new BasisSessionService(), store);
		long after = System.currentTimeMillis() / 1000L - 1;
		String token = BasisTestJwt.sign(key, "at-revocation+jwt", ISSUER, AUDIENCE, null,
				Map.of("access_token_jti", "jti-1", "valid_after", after));
		assertEquals(204, controller.receive(Map.of("action", "revoke_access_token", "revocation_token", token, "jti",
				"jti-1", "valid_after", String.valueOf(after))).getStatusCode().value());
		assertTrue(store.isDenied("jti-1"));
		BasisResourceController resource = new BasisResourceController(verifier(key), store);
		String access = BasisTestJwt.sign(key, "at+jwt", ISSUER, AUDIENCE, "user-1",
				Map.of("jti", "jti-1", "scope", "read", "permissions", java.util.List.of("member")));
		assertEquals(401, resource.whoami("Bearer " + access).getStatusCode().value());
	}

	@Test
	void revocationValidationFailures() throws Exception {
		RSAKey key = BasisTestJwt.key();
		BasisRevocationStore store = new BasisRevocationStore();
		BasisBackchannelController controller = controller(verifier(key), new BasisSessionService(), store);
		assertEquals(401, controller.receive(Map.of("action", "revoke_access_token", "revocation_token", "bad"))
				.getStatusCode().value());
		String missing = BasisTestJwt.sign(key, "at-revocation+jwt", ISSUER, AUDIENCE, null, Map.of());
		assertEquals(400, controller.receive(Map.of("action", "revoke_access_token", "revocation_token", missing))
				.getStatusCode().value());
		String noAfter = BasisTestJwt.sign(key, "at-revocation+jwt", ISSUER, AUDIENCE, null,
				Map.of("access_token_jti", "x"));
		assertEquals(400, controller.receive(Map.of("action", "revoke_access_token", "revocation_token", noAfter))
				.getStatusCode().value());
		long after = System.currentTimeMillis() / 1000L;
		String mismatch = BasisTestJwt.sign(key, "at-revocation+jwt", ISSUER, AUDIENCE, null,
				Map.of("access_token_jti", "a", "valid_after", after));
		assertEquals(400, controller.receive(Map.of("action", "revoke_access_token", "revocation_token", mismatch,
				"jti", "b", "valid_after", String.valueOf(after))).getStatusCode().value());
		assertEquals(400, controller.receive(Map.of("action", "revoke_access_token", "revocation_token", mismatch,
				"jti", "a", "valid_after", String.valueOf(after + 1))).getStatusCode().value());
		assertEquals(400, controller.receive(Map.of("action", "other")).getStatusCode().value());
	}

	@Test
	void resourceEndpointAcceptsValidTokenAndRejectsMissingOnes() throws Exception {
		RSAKey key = BasisTestJwt.key();
		BasisResourceController resource = new BasisResourceController(verifier(key), new BasisRevocationStore());
		assertEquals(401, resource.whoami(null).getStatusCode().value());
		assertEquals(401, resource.whoami("Basic abc").getStatusCode().value());
		assertEquals(401, resource.whoami("Bearer bad").getStatusCode().value());
		String access = BasisTestJwt.sign(key, "at+jwt", ISSUER, AUDIENCE, "user-1",
				Map.of("jti", "fresh", "scope", "read", "permissions", java.util.List.of("member")));
		org.springframework.http.ResponseEntity<?> response = resource.whoami("Bearer " + access);
		assertEquals(200, response.getStatusCode().value());
		assertEquals("user-1", ((Map<?, ?>) response.getBody()).get("sub"));
	}

	@Test
	void signInHandlerRegistersSessionForOidcAndOAuth2() throws Exception {
		BasisSessionService sessions = new BasisSessionService();
		BasisSignInSuccessHandler handler = new BasisSignInSuccessHandler(sessions);
		handler.configure();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestedSessionId("oidc-session");
		sessions.sessionCreated(new jakarta.servlet.http.HttpSessionEvent(request.getSession()));
		OidcIdToken idToken = new OidcIdToken("v", java.time.Instant.now(), java.time.Instant.now().plusSeconds(60),
				Map.of("sub", "oidc-user"));
		handler.onAuthenticationSuccess(request, new MockHttpServletResponse(),
				new UsernamePasswordAuthenticationToken(
						new DefaultOidcUser(java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken), null,
						java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		assertEquals(1, sessions.sessionCount("oidc-user"));

		MockHttpServletRequest second = new MockHttpServletRequest();
		second.setRequestedSessionId("oauth-session");
		sessions.sessionCreated(new jakarta.servlet.http.HttpSessionEvent(second.getSession()));
		handler.onAuthenticationSuccess(second, new MockHttpServletResponse(),
				new UsernamePasswordAuthenticationToken(
						new DefaultOAuth2User(java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
								Map.of("login", "gh-user"), "login"),
						null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		assertEquals(1, sessions.sessionCount("gh-user"));
	}

	@Test
	void sessionServiceHandlesRemovalAndNullRegistration() {
		BasisSessionService sessions = new BasisSessionService();
		sessions.register(null, "s");
		sessions.register("id", null);
		assertEquals(0, sessions.sessionCount("s"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		HttpSession session = request.getSession();
		sessions.sessionCreated(new jakarta.servlet.http.HttpSessionEvent(session));
		sessions.register(session.getId(), "s");
		sessions.sessionDestroyed(new jakarta.servlet.http.HttpSessionEvent(session));
		assertEquals(0, sessions.sessionCount("s"));
		sessions.logout("unknown");
	}

	@Test
	void revocationStoreKeepsEarliestValidAfterAndIgnoresBlankJti() throws Exception {
		BasisRevocationStore store = new BasisRevocationStore();
		store.deny(null, 1);
		store.deny("j", 2_000_000_000L);
		store.deny("j", 1_999_999_999L);
		assertEquals(1, store.size());
		assertFalse(store.isDenied("j"));
		assertFalse(store.isDenied(null));
		store.deny("j", System.currentTimeMillis() / 1000L - 1);
		assertTrue(store.isDenied("j"));
	}

	@Test
	void httpSourcesFetchFromLocalServer() throws Exception {
		RSAKey key = BasisTestJwt.key();
		HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/oauth/jwks", exchange -> {
			byte[] body = BasisTestJwt.jwks(key).getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.createContext("/internal/backchannel/validate", exchange -> {
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		try {
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			HttpBasisJwkSource jwks = new HttpBasisJwkSource(base + "/");
			assertNotNull(jwks.load());
			assertNotNull(jwks.load());
			new HttpBasisInternalClient(base + "/").validateWebhook("urn", "secret", "n\"q", "hash\\v");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void jwkSourceHandlesErrorStatusAndInterruption() throws Exception {
		HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/oauth/jwks", exchange -> {
			exchange.sendResponseHeaders(500, -1);
			exchange.close();
		});
		server.start();
		try {
			String url = "http://127.0.0.1:" + server.getAddress().getPort();
			try {
				new HttpBasisJwkSource(url).load();
			} catch (IllegalStateException expected) {
				assertTrue(expected.getMessage().contains("500"));
			}
			Thread.currentThread().interrupt();
			try {
				new HttpBasisJwkSource(url).load();
			} catch (IllegalStateException expected) {
				assertTrue(expected.getMessage().contains("interrupted"));
			} finally {
				Thread.interrupted();
			}
		} finally {
			server.stop(0);
		}
	}

	@Test
	void internalClientHandlesInterruption() {
		Thread.currentThread().interrupt();
		try {
			new HttpBasisInternalClient("http://127.0.0.1:1").validateWebhook("urn", "s", "n", "h");
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void revocationStoreIgnoresBlankJtiAndUnknownSessions() {
		BasisRevocationStore store = new BasisRevocationStore();
		store.deny("   ", 1);
		assertEquals(0, store.size());
		BasisSessionService sessions = new BasisSessionService();
		sessions.register("missing-session", "u");
		sessions.logout("u");
		assertEquals(0, sessions.sessionCount("u"));
	}

	@Test
	void signInHandlerCoversOAuth2SubjectVariants() throws Exception {
		BasisSessionService sessions = new BasisSessionService();
		BasisSignInSuccessHandler handler = new BasisSignInSuccessHandler(sessions);
		MockHttpServletRequest request = new MockHttpServletRequest();
		HttpSession session = request.getSession();
		sessions.sessionCreated(new jakarta.servlet.http.HttpSessionEvent(session));
		handler.onAuthenticationSuccess(request, new MockHttpServletResponse(),
				new UsernamePasswordAuthenticationToken(
						new DefaultOAuth2User(java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
								Map.of("sub", "oauth-sub"), "sub"),
						null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		assertEquals(1, sessions.sessionCount("oauth-sub"));
		MockHttpServletRequest nameless = new MockHttpServletRequest();
		handler.onAuthenticationSuccess(nameless, new MockHttpServletResponse(),
				new UsernamePasswordAuthenticationToken(
						new DefaultOAuth2User(java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
								Map.of("id", "x"), "id"),
						null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		MockHttpServletRequest other = new MockHttpServletRequest();
		handler.onAuthenticationSuccess(other, new MockHttpServletResponse(), new UsernamePasswordAuthenticationToken(
				"principal", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
	}

	@Test
	void logoutWithAlreadyInvalidatedSessionIsSafe() {
		BasisSessionService sessions = new BasisSessionService();
		HttpSession session = org.mockito.Mockito.mock(HttpSession.class);
		org.mockito.Mockito.when(session.getId()).thenReturn("throwing-session");
		org.mockito.Mockito.doThrow(new IllegalStateException()).when(session).invalidate();
		sessions.sessionCreated(new jakarta.servlet.http.HttpSessionEvent(session));
		sessions.register("throwing-session", "gone");
		sessions.logout("gone");
		assertEquals(0, sessions.sessionCount("gone"));
	}

	@Test
	void webhookProofFailureDoesNotFailRequest() {
		BasisInternalClient failing = (resource, secret, nonce, hash) -> {
			throw new IllegalStateException("internal unavailable");
		};
		MockEnvironment env = new MockEnvironment();
		env.setProperty("BASIS_AUTH_RESOURCE", AUDIENCE);
		env.setProperty("BASIS_AUTH_RESOURCE_SECRET", "secret");
		BasisBackchannelController controller = new BasisBackchannelController(verifierOrNull(),
				new BasisSessionService(), new BasisRevocationStore(), failing, env);
		assertEquals(202,
				controller.receive(Map.of("action", "test", "nonce", "n", "challenge", "c")).getStatusCode().value());
	}

	private BasisTokenVerifier verifierOrNull() {
		return new BasisTokenVerifier(() -> "{}", "https://auth.example.org", AUDIENCE);
	}

	@Test
	void revocationPrunesOldEntriesBeyondCap() throws Exception {
		BasisRevocationStore store = new BasisRevocationStore();
		java.lang.reflect.Field storedAt = BasisRevocationStore.class.getDeclaredField("storedAt");
		storedAt.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.Map<String, Long> timestamps = (java.util.Map<String, Long>) storedAt.get(store);
		long old = System.currentTimeMillis() - 25L * 60 * 60 * 1000;
		for (int i = 0; i < 5001; i++) {
			store.deny("old-" + i, 1);
			timestamps.put("old-" + i, old);
		}
		for (int i = 0; i < 30; i++) {
			store.deny("fresh-" + i, 1);
		}
		assertTrue(store.size() < 5031, "old revocations must be pruned");
	}

	@Test
	void sessionServicePrunesEmptySubjectLinks() {
		BasisSessionService sessions = new BasisSessionService();
		jakarta.servlet.http.HttpSession session = org.mockito.Mockito.mock(jakarta.servlet.http.HttpSession.class);
		org.mockito.Mockito.when(session.getId()).thenReturn("s-prune");
		sessions.sessionCreated(new jakarta.servlet.http.HttpSessionEvent(session));
		sessions.register("s-prune", "subject-prune");
		assertEquals(1, sessions.sessionCount("subject-prune"));
		sessions.sessionDestroyed(new jakarta.servlet.http.HttpSessionEvent(session));
		assertEquals(0, sessions.sessionCount("subject-prune"));
	}

	@Test
	void jwkSourceRejectsPlainHttpRemoteIssuer() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				new org.junit.jupiter.api.function.Executable() {
					@Override
					public void execute() {
						new HttpBasisJwkSource("http://auth.example.org");
					}
				});
		assertNotNull(new HttpBasisJwkSource("http://localhost:3000"));
	}

	@Test
	void httpSourcesSurviveFailures() {
		HttpBasisJwkSource source = new HttpBasisJwkSource("http://127.0.0.1:1");
		for (int i = 0; i < 2; i++) {
			try {
				source.load();
			} catch (IllegalStateException expected) {
				assertNotNull(expected.getMessage());
			}
		}
		HttpBasisInternalClient client = new HttpBasisInternalClient("http://127.0.0.1:1");
		client.validateWebhook("urn", "s", "n", "h");
		client.validateWebhook("urn", "s", "n", "h");
	}
}
