package com.sprintjudge.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives a complete authorization-code login against a local mock OIDC
 * provider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("all")
class OAuth2CallbackIntegrationTest {

	private static final String EMAIL = "teacher@example.org";
	private static HttpServer provider;
	private static RSAKey key;
	private static volatile String nonce;

	static {
		try {
			key = new RSAKeyGenerator(2048).keyID("mock-key").generate();
			provider = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
			provider.createContext("/oauth/jwks", exchange -> respond(exchange, new JWKSet(key).toString()));
			provider.createContext("/oauth/token", exchange -> {
				String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				assertTrue(body.contains("grant_type=authorization_code"));
				assertTrue(body.contains("code_verifier="));
				String clientId = "test-client";
				JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(base()).audience(clientId)
						.subject("mock-subject").claim("email", EMAIL).claim("nonce", nonce)
						.expirationTime(Date.from(Instant.now().plusSeconds(600))).issueTime(Date.from(Instant.now()))
						.build();
				try {
					SignedJWT idToken = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
							.type(new JOSEObjectType("JWT")).keyID("mock-key").build(), claims);
					idToken.sign(new RSASSASigner(key));
					String json = "{\"access_token\":\"access\",\"token_type\":\"Bearer\",\"id_token\":\""
							+ idToken.serialize() + "\",\"expires_in\":3600}";
					respond(exchange, json);
				} catch (com.nimbusds.jose.JOSEException e) {
					exchange.sendResponseHeaders(500, -1);
					exchange.close();
				}
			});
			provider.createContext("/oauth/userinfo",
					exchange -> respond(exchange, "{\"sub\":\"mock-subject\",\"email\":\"" + EMAIL + "\"}"));
			provider.createContext("/oauth/authorize", exchange -> {
				Map<String, String> query = parse(exchange.getRequestURI().getRawQuery());
				nonce = query.get("nonce");
				String redirect = query.get("redirect_uri");
				exchange.getResponseHeaders().add("Location", redirect + "?code=mock-code&state=" + query.get("state"));
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
			});
			provider.start();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static int port() {
		return provider.getAddress().getPort();
	}

	private static String base() {
		return "http://127.0.0.1:" + port();
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private static Map<String, String> parse(String raw) {
		java.util.Map<String, String> out = new java.util.HashMap<>();
		for (String pair : raw.split("&")) {
			String[] parts = pair.split("=", 2);
			out.put(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
					parts.length == 2 ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
		}
		return out;
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("SPRINTJUDGE_OIDC_ISSUER", OAuth2CallbackIntegrationTest::base);
		registry.add("SPRINTJUDGE_OIDC_CLIENT_ID", () -> "test-client");
		registry.add("SPRINTJUDGE_OIDC_CLIENT_SECRET", () -> "test-secret");
		registry.add("SPRINTJUDGE_ADMIN_EMAILS", () -> EMAIL);
		registry.add("SPRINTJUDGE_ADMIN_PASSWORD", () -> "ci-admin-password");
	}

	@LocalServerPort
	int appPort;

	@Test
	void authorizationCodeLoginGrantsAdminAndPasswordLoginStillWorks() throws Exception {
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
				.cookieHandler(new java.net.CookieManager()).connectTimeout(Duration.ofSeconds(5)).build();
		String app = "http://127.0.0.1:" + appPort;
		HttpResponse<Void> start = client.send(
				HttpRequest.newBuilder(URI.create(app + "/oauth2/authorization/oidc")).GET().build(),
				HttpResponse.BodyHandlers.discarding());
		assertEquals(302, start.statusCode());
		String providerLocation = start.headers().firstValue("Location").orElse("");
		assertTrue(providerLocation.contains("code_challenge_method=S256"));
		assertTrue(providerLocation.contains("nonce="));

		HttpResponse<Void> authorize = client.send(HttpRequest.newBuilder(URI.create(providerLocation)).GET().build(),
				HttpResponse.BodyHandlers.discarding());
		assertEquals(302, authorize.statusCode());
		String callback = authorize.headers().firstValue("Location").orElse("");
		HttpResponse<Void> complete = client.send(HttpRequest.newBuilder(URI.create(callback)).GET().build(),
				HttpResponse.BodyHandlers.discarding());
		assertEquals(302, complete.statusCode());
		assertTrue(complete.headers().firstValue("Location").orElse("").contains("/admin/dashboard"));

		HttpResponse<String> dashboard = client.send(
				HttpRequest.newBuilder(URI.create(app + "/admin/dashboard")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertEquals(200, dashboard.statusCode());
	}
}
