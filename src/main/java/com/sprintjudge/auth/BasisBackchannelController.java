package com.sprintjudge.auth;

import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jwt.JWTClaimsSet;

/** Receives basis-auth webhook verification, logout and revocation events. */
@RestController
public class BasisBackchannelController {

	private final BasisTokenVerifier verifier;
	private final BasisSessionService sessions;
	private final BasisRevocationStore revocations;
	private final BasisInternalClient internal;
	private final String resource;
	private final String resourceSecret;

	public BasisBackchannelController(BasisTokenVerifier verifier, BasisSessionService sessions,
			BasisRevocationStore revocations, BasisInternalClient internal, Environment environment) {
		this.verifier = verifier;
		this.sessions = sessions;
		this.revocations = revocations;
		this.internal = internal;
		this.resource = environment.getProperty("BASIS_AUTH_RESOURCE", "");
		this.resourceSecret = environment.getProperty("BASIS_AUTH_RESOURCE_SECRET", "");
	}

	@PostMapping({"/api/auth/basis/backchannel", "/api/auth/backchannel"})
	public ResponseEntity<Void> receive(@RequestParam Map<String, String> form) {
		String action = form.get("action");
		if ("test".equals(action)) {
			verifyWebhook(form.get("nonce"), form.get("challenge"));
			return ResponseEntity.accepted().build();
		}
		if ("revoke_access_token".equals(action)) {
			return revoke(form);
		}
		if (action != null) {
			return ResponseEntity.badRequest().build();
		}
		return logout(form.get("logout_token"));
	}

	private void verifyWebhook(String nonce, String challenge) {
		if (nonce == null || challenge == null)
			return;
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(resourceSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String hash = HexFormat.of().formatHex(mac.doFinal(challenge.getBytes(StandardCharsets.UTF_8)));
			internal.validateWebhook(resource, resourceSecret, nonce, hash);
		} catch (Exception ignored) {
			// The issuer keeps the previous URL list when proof fails.
		}
	}

	private ResponseEntity<Void> revoke(Map<String, String> form) {
		JWTClaimsSet claims = verifier.verify(form.get("revocation_token"), "at-revocation+jwt");
		if (claims == null)
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		Object jti = claims.getClaim("access_token_jti");
		Object validAfter = claims.getClaim("valid_after");
		if (jti == null || validAfter == null)
			return ResponseEntity.badRequest().build();
		long formValidAfter;
		long claimValidAfter;
		try {
			formValidAfter = Long.parseLong(form.get("valid_after"));
			claimValidAfter = Long.parseLong(String.valueOf(validAfter));
		} catch (NumberFormatException e) {
			return ResponseEntity.badRequest().build();
		}
		if (!String.valueOf(jti).equals(form.get("jti")) || claimValidAfter != formValidAfter)
			return ResponseEntity.badRequest().build();
		revocations.deny(String.valueOf(jti), claimValidAfter);
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<Void> logout(String token) {
		JWTClaimsSet claims = verifier.verify(token, "logout+jwt");
		if (claims == null)
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		Object event = claims.getClaim("http://schemas.openid.net/event/backchannel-logout");
		if (event == null)
			return ResponseEntity.badRequest().build();
		Object sub = claims.getSubject();
		if (sub == null)
			return ResponseEntity.badRequest().build();
		sessions.logout(String.valueOf(sub));
		return ResponseEntity.noContent().build();
	}
}
