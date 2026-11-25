package com.sprintjudge.auth;

import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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

	/** Per-IP budget for the issuer verification ping. */
	private static final java.util.concurrent.ConcurrentHashMap<String, long[]> TEST_WINDOW = new java.util.concurrent.ConcurrentHashMap<>();
	private static final int TEST_LIMIT = 10;
	private static final long WINDOW_MS = 60_000L;
	private static final int MAX_TEST_IPS = 1_000;

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
			if (!testAuthorized(form))
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			if (testRateLimited())
				return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
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

	/**
	 * The test action makes the server send the shared secret to the issuer, so an
	 * HTTP caller must be an authenticated admin or present an issuer-signed token
	 * bound to this nonce/challenge. Direct in-process calls have no request
	 * context and are trusted.
	 */
	private boolean testAuthorized(Map<String, String> form) {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		if (!(attrs instanceof ServletRequestAttributes))
			return true;
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
			for (GrantedAuthority authority : auth.getAuthorities()) {
				if ("ROLE_ADMIN".equals(authority.getAuthority()))
					return true;
			}
		}
		JWTClaimsSet claims = verifier.verify(form.get("token"), "at+jwt");
		if (claims == null)
			return false;
		return java.util.Objects.equals(form.get("nonce"), claims.getClaim("nonce"))
				&& java.util.Objects.equals(form.get("challenge"), claims.getClaim("challenge"));
	}

	/**
	 * Bounds the issuer verification ping by IP. Over the cap, only expired buckets
	 * are evicted - clearing all would let a source-IP spray reset every budget.
	 */
	private boolean testRateLimited() {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		if (!(attrs instanceof ServletRequestAttributes))
			return false;
		if (TEST_WINDOW.size() > MAX_TEST_IPS) {
			long evictAt = System.currentTimeMillis();
			TEST_WINDOW.entrySet().removeIf(entry -> evictAt - entry.getValue()[0] > WINDOW_MS);
		}
		jakarta.servlet.http.HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
		String ip = com.sprintjudge.util.ClientIpResolver.resolve(request.getRemoteAddr(),
				request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"));
		long now = System.currentTimeMillis();
		long[] slot = TEST_WINDOW.compute(ip, (k, v) -> {
			if (v == null || now - v[0] > WINDOW_MS)
				return new long[]{now, 1};
			v[1]++;
			return v;
		});
		return slot[1] > TEST_LIMIT;
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
