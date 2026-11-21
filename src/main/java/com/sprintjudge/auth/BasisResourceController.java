package com.sprintjudge.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Minimal basis-auth resource endpoint demonstrating bearer-token validation.
 */
@RestController
public class BasisResourceController {

	private final BasisTokenVerifier verifier;
	private final BasisRevocationStore revocations;

	public BasisResourceController(BasisTokenVerifier verifier, BasisRevocationStore revocations) {
		this.verifier = verifier;
		this.revocations = revocations;
	}

	@GetMapping("/api/public/basis/whoami")
	public ResponseEntity<?> whoami(@RequestHeader(value = "Authorization", required = false) String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return unauthorized();
		}
		JWTClaimsSet claims = verifier.verify(authorization.substring(7), "at+jwt");
		if (claims == null || revocations.isDenied(String.valueOf(claims.getClaim("jti")))) {
			return unauthorized();
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("sub", claims.getSubject());
		body.put("scope", claims.getClaim("scope"));
		body.put("permissions", claims.getClaim("permissions"));
		return ResponseEntity.ok(body);
	}

	private static ResponseEntity<Map<String, String>> unauthorized() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", "invalid_token");
		body.put("error_description", "Invalid access token");
		return ResponseEntity.status(401).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
				.body(body);
	}
}
