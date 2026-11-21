package com.sprintjudge.auth;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

/**
 * Verifies basis-auth backchannel JWTs (logout and access-token revocation)
 * against the issuer JWKS. Only RS256 signed tokens with the expected type,
 * issuer, resource audience and valid expiry are accepted.
 */
@Component
public class BasisTokenVerifier {

	private static final long CACHE_MS = 5 * 60 * 1000L;

	private final BasisJwkSource source;
	private final String issuer;
	private final String audience;
	private volatile JWKSet cached;
	private volatile long cachedAt;

	@org.springframework.beans.factory.annotation.Autowired
	public BasisTokenVerifier(org.springframework.core.env.Environment environment) {
		String base = environment.getProperty("BASIS_AUTH_ISSUER", "");
		this.issuer = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		this.audience = environment.getProperty("BASIS_AUTH_RESOURCE", "");
		this.source = new HttpBasisJwkSource(this.issuer.isEmpty() ? "http://localhost" : this.issuer);
	}

	BasisTokenVerifier(BasisJwkSource source, String issuer, String audience) {
		this.source = source;
		this.issuer = issuer;
		this.audience = audience;
	}

	/** Returns verified claims, or null for any invalid or unexpected token. */
	public JWTClaimsSet verify(String token, String expectedTyp) {
		if (token == null || token.isBlank() || expectedTyp == null || issuer.isEmpty() || audience.isEmpty())
			return null;
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm()))
				return null;
			com.nimbusds.jose.JOSEObjectType type = jwt.getHeader().getType();
			if (type == null || !expectedTyp.equals(type.getType()))
				return null;
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			if (!issuer.equals(claims.getIssuer()))
				return null;
			if (!claims.getAudience().contains(audience))
				return null;
			Date expiry = claims.getExpirationTime();
			Instant now = Instant.now();
			// Small clock-skew allowance only; a 30s grace kept revoked/expired
			// tokens usable for half a minute after their stated expiry.
			if (expiry == null || expiry.toInstant().isBefore(now.minusSeconds(5)))
				return null;
			Date notBefore = claims.getNotBeforeTime();
			if (notBefore != null && notBefore.toInstant().isAfter(now.plusSeconds(5)))
				return null;
			JWK key = keys().getKeyByKeyId(jwt.getHeader().getKeyID());
			if (!(key instanceof RSAKey))
				return null;
			if (!jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier((RSAPublicKey) ((RSAKey) key).toPublicKey())))
				return null;
			return claims;
		} catch (Exception e) {
			return null;
		}
	}

	private synchronized JWKSet keys() throws java.text.ParseException {
		long now = System.currentTimeMillis();
		if (cached == null || now - cachedAt > CACHE_MS) {
			cached = JWKSet.parse(source.load());
			cachedAt = now;
		}
		return cached;
	}
}
