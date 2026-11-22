package com.sprintjudge.auth;

import java.util.Map;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("all")
class BasisTokenVerifierTest {

	private static final String ISSUER = "https://auth.example.org";
	private static final String AUDIENCE = "urn:sprintjudge:api";

	@Test
	void acceptsValidLogoutToken() throws Exception {
		RSAKey key = BasisTestJwt.key();
		String token = BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "user-1", Map.of());
		BasisTokenVerifier verifier = new BasisTokenVerifier(() -> BasisTestJwt.jwks(key), ISSUER, AUDIENCE);
		assertEquals("user-1", verifier.verify(token, "logout+jwt").getSubject());
	}

	@Test
	void rejectsWrongTypeIssuerAudienceAndSignature() throws Exception {
		RSAKey key = BasisTestJwt.key();
		RSAKey other = BasisTestJwt.key();
		BasisTokenVerifier verifier = new BasisTokenVerifier(() -> BasisTestJwt.jwks(key), ISSUER, AUDIENCE);
		assertNull(verifier.verify(BasisTestJwt.sign(key, "at+jwt", ISSUER, AUDIENCE, "u", Map.of()), "logout+jwt"));
		assertNull(verifier.verify(BasisTestJwt.sign(key, "logout+jwt", "https://evil", AUDIENCE, "u", Map.of()),
				"logout+jwt"));
		assertNull(verifier.verify(BasisTestJwt.sign(key, "logout+jwt", ISSUER, "urn:other", "u", Map.of()),
				"logout+jwt"));
		assertNull(
				verifier.verify(BasisTestJwt.sign(other, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of()), "logout+jwt"));
		String futureNbf = BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u",
				Map.of("nbf", System.currentTimeMillis() / 1000L + 3600L));
		assertNull(verifier.verify(futureNbf, "logout+jwt"));
		String pastNbf = BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u",
				Map.of("nbf", System.currentTimeMillis() / 1000L - 120L));
		assertNotNull(verifier.verify(pastNbf, "logout+jwt"));
	}

	@Test
	void rejectsBlankMalformedAndUnconfigured() throws Exception {
		RSAKey key = BasisTestJwt.key();
		BasisTokenVerifier verifier = new BasisTokenVerifier(() -> BasisTestJwt.jwks(key), ISSUER, AUDIENCE);
		assertNull(verifier.verify(null, "logout+jwt"));
		assertNull(verifier.verify("  ", "logout+jwt"));
		assertNull(verifier.verify("not.a.jwt", "logout+jwt"));
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).build();
		SignedJWT hmac = new SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256), claims);
		hmac.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32]));
		assertNull(verifier.verify(hmac.serialize(), "logout+jwt"));
		RSAKey local = BasisTestJwt.key();
		assertNull(new BasisTokenVerifier(() -> BasisTestJwt.jwks(local), ISSUER, AUDIENCE)
				.verify(BasisTestJwt.signNoType(local, ISSUER, AUDIENCE), "logout+jwt"));
		assertNull(verifier.verify(BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of()), null));
		BasisTokenVerifier empty = new BasisTokenVerifier(() -> BasisTestJwt.jwks(key), "", "");
		assertNull(empty.verify("x", "logout+jwt"));
		BasisTokenVerifier noAudience = new BasisTokenVerifier(() -> BasisTestJwt.jwks(key), ISSUER, "");
		assertNull(
				noAudience.verify(BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of()), "logout+jwt"));
	}

	@Test
	void environmentConstructorHandlesBlankAndTrailingSlashIssuer() {
		org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
		assertNull(new BasisTokenVerifier(env).verify("bad", "logout+jwt"));
		env.setProperty("BASIS_AUTH_ISSUER", "https://auth.example.org/");
		env.setProperty("BASIS_AUTH_RESOURCE", AUDIENCE);
		assertNull(new BasisTokenVerifier(env).verify("bad", "logout+jwt"));
	}

	@Test
	void rejectsExpiredTokenUnknownKeyAndNonRsaKey() throws Exception {
		RSAKey key = BasisTestJwt.key();
		BasisTokenVerifier verifier = new BasisTokenVerifier(() -> BasisTestJwt.jwks(key), ISSUER, AUDIENCE);
		String expired = BasisTestJwt.signWithKid(key, "test-key", "logout+jwt", ISSUER, AUDIENCE, "u",
				java.time.Instant.now().minusSeconds(120), Map.of());
		assertNull(verifier.verify(expired, "logout+jwt"));
		assertNull(verifier.verify(BasisTestJwt.signNoExpiry(key, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of()),
				"logout+jwt"));
		String unknownKid = BasisTestJwt.signWithKid(key, "other", "logout+jwt", ISSUER, AUDIENCE, "u", null, Map.of());
		assertNull(verifier.verify(unknownKid, "logout+jwt"));
		BasisTokenVerifier brokenKeys = new BasisTokenVerifier(() -> "not-json", ISSUER, AUDIENCE);
		assertNull(
				brokenKeys.verify(BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of()), "logout+jwt"));
		com.nimbusds.jose.jwk.ECKey ec = new com.nimbusds.jose.jwk.gen.ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256)
				.generate();
		BasisTokenVerifier ecKeys = new BasisTokenVerifier(() -> new com.nimbusds.jose.jwk.JWKSet(ec).toString(),
				ISSUER, AUDIENCE);
		assertNull(ecKeys.verify(BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of()), "logout+jwt"));
	}

	@Test
	void failedKeyFetchIsNegativeCached() throws Exception {
		RSAKey key = BasisTestJwt.key();
		int[] calls = {0};
		BasisTokenVerifier verifier = new BasisTokenVerifier(() -> {
			calls[0]++;
			throw new IllegalStateException("issuer down");
		}, ISSUER, AUDIENCE);
		String token = BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of());
		assertNull(verifier.verify(token, "logout+jwt"));
		assertNull(verifier.verify(token, "logout+jwt"));
		assertEquals(1, calls[0]);
	}

	@Test
	void cachesKeysUntilRefresh() throws Exception {
		RSAKey key = BasisTestJwt.key();
		int[] calls = {0};
		BasisTokenVerifier verifier = new BasisTokenVerifier(() -> {
			calls[0]++;
			return BasisTestJwt.jwks(key);
		}, ISSUER, AUDIENCE);
		String token = BasisTestJwt.sign(key, "logout+jwt", ISSUER, AUDIENCE, "u", Map.of());
		assertNotNull(verifier.verify(token, "logout+jwt"));
		assertNotNull(verifier.verify(token, "logout+jwt"));
		assertEquals(1, calls[0]);
		java.lang.reflect.Field field = BasisTokenVerifier.class.getDeclaredField("cachedAt");
		field.setAccessible(true);
		field.setLong(verifier, System.currentTimeMillis() - 10 * 60 * 1000L);
		assertNotNull(verifier.verify(token, "logout+jwt"));
		assertEquals(2, calls[0]);
	}
}
