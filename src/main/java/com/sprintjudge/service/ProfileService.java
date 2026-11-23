package com.sprintjudge.service;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.sprintjudge.domain.profile.PlayerProfile;
import com.sprintjudge.domain.profile.ProfileReport;
import com.sprintjudge.domain.profile.ProfileReport.ScoreEntry;
import com.sprintjudge.domain.profile.ProfileStore;
import com.sprintjudge.util.NameSanitizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Optional profiles: a nickname plus progress, claimed with a game rejoin token. */
@Service
public class ProfileService {

	private final ProfileStore store;

	public ProfileService(ProfileStore store) {
		this.store = store;
	}

	public ProfileReport create(@Nullable String rawNickname, @Nullable String playerUuid,
			@Nullable String rejoinToken) {
		String nickname = requireNickname(rawNickname);
		PlayerProfile profile;
		try {
			profile = store.create(nickname);
			@Nullable
			String linked = link(playerUuid, rejoinToken);
			if (linked != null) {
				store.link(profile.getId(), linked);
			}
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "player_uuid already linked");
		}
		return report(profile.getId());
	}

	public ProfileReport get(String id, String token) {
		require(id, token);
		return report(id);
	}

	public ProfileReport update(String id, String token, @Nullable String rawNickname,
			@Nullable String playerUuid, @Nullable String rejoinToken) {
		PlayerProfile current = require(id, token);
		String nickname = rawNickname == null ? current.getNickname() : requireNickname(rawNickname);
		try {
			if (!store.rename(id, nickname)) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "profile_not_found");
			}
			@Nullable
			String linked = link(playerUuid, rejoinToken);
			if (linked != null) {
				store.link(id, linked);
			}
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "player_uuid already linked");
		}
		return report(id);
	}

	private PlayerProfile require(String id, String token) {
		PlayerProfile profile = store.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "profile_not_found"));
		if (!MessageDigest.isEqual(profile.getLinkToken().getBytes(StandardCharsets.UTF_8),
				token.getBytes(StandardCharsets.UTF_8))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_profile_token");
		}
		return profile;
	}

	@Nullable
	private String link(@Nullable String playerUuid, @Nullable String rejoinToken) {
		if (playerUuid == null || playerUuid.isBlank())
			return null;
		String uuid = playerUuid.trim();
		if (!store.validRejoin(uuid, rejoinToken)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_rejoin_token");
		}
		return uuid;
	}

	private static String requireNickname(@Nullable String raw) {
		String nickname = NameSanitizer.sanitize(raw);
		if (nickname.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nickname is required");
		}
		return nickname;
	}

	private ProfileReport report(String id) {
		PlayerProfile profile = store.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "profile_not_found"));
		List<ScoreEntry> history = store.history(id);
		List<Boolean> flags = store.correctness(id);
		ProfileReport.Stats stats = ProfileReport.stats(history, flags);
		return new ProfileReport(new ProfileReport.Profile(profile), stats, history,
				ProfileReport.achievements(stats), ProfileReport.portfolio(history));
	}
}
