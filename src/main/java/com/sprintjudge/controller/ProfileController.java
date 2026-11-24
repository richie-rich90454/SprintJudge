package com.sprintjudge.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.sprintjudge.domain.profile.ProfileReport;
import com.sprintjudge.service.ProfileService;

import java.util.Map;

/**
 * Optional player profiles under the public API. The bearer token returned at
 * creation is the only credential; anonymous join needs none of this.
 */
@RestController
@RequestMapping("/api/public/profiles")
public class ProfileController {

	private final ProfileService service;

	public ProfileController(ProfileService service) {
		this.service = service;
	}

	/**
	 * Creates a profile. Linking a game identity requires its live rejoin token.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProfileReport create(@RequestBody(required = false) Map<String, String> body) {
		Map<String, String> fields = body == null ? Map.of() : body;
		return service.create(fields.get("nickname"), fields.get("playerUuid"), fields.get("rejoinToken"));
	}

	/**
	 * Reads the caller's own profile: profile, stats, history, awards, portfolio.
	 */
	@GetMapping("/{id}")
	public ProfileReport get(@PathVariable String id,
			@RequestHeader(value = "Authorization", required = false) String authorization) {
		return service.get(id, bearer(authorization));
	}

	/** Renames the nickname and/or links a game identity. */
	@PatchMapping("/{id}")
	public ProfileReport update(@PathVariable String id,
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody(required = false) Map<String, String> body) {
		Map<String, String> fields = body == null ? Map.of() : body;
		return service.update(id, bearer(authorization), fields.get("nickname"), fields.get("playerUuid"),
				fields.get("rejoinToken"));
	}

	private static String bearer(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "profile_token_required");
		}
		return authorization.substring(7).trim();
	}
}
