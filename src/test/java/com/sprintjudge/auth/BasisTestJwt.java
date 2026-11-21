package com.sprintjudge.auth;

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

final class BasisTestJwt {

	static RSAKey key() throws Exception {
		return new RSAKeyGenerator(2048).keyID("test-key").generate();
	}

	static String jwks(RSAKey key) {
		return new JWKSet(key).toString();
	}

	static String signWithKid(RSAKey key, String kid, String typ, String issuer, String audience, String subject,
			Instant expiry, Map<String, Object> extra) throws Exception {
		JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
				.expirationTime(Date.from(expiry == null ? Instant.now().plusSeconds(120) : expiry));
		if (subject != null)
			builder.subject(subject);
		for (Map.Entry<String, Object> entry : extra.entrySet()) {
			builder.claim(entry.getKey(), entry.getValue());
		}
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(typ)).keyID(kid).build(),
				builder.build());
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	static String signNoType(RSAKey key, String issuer, String audience) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
				.expirationTime(Date.from(Instant.now().plusSeconds(120))).build();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	static String signNoExpiry(RSAKey key, String typ, String issuer, String audience, String subject,
			Map<String, Object> extra) throws Exception {
		JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issuer(issuer).audience(audience);
		if (subject != null)
			builder.subject(subject);
		for (Map.Entry<String, Object> entry : extra.entrySet()) {
			builder.claim(entry.getKey(), entry.getValue());
		}
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(typ)).keyID("test-key").build(),
				builder.build());
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	static String sign(RSAKey key, String typ, String issuer, String audience, String subject,
			Map<String, Object> extra) throws Exception {
		return signWithKid(key, "test-key", typ, issuer, audience, subject, null, extra);
	}
}
