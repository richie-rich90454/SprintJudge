package com.sprintjudge.domain.profile;

import org.eclipse.jdt.annotation.Nullable;
import java.util.Objects;

/** Opt-in player identity. The link token is the only credential. */
public final class PlayerProfile {

	private final String id;
	private final String nickname;
	private final String linkToken;
	@Nullable
	private final String playerUuid;
	private final long createdAt;

	public PlayerProfile(String id, String nickname, @Nullable String playerUuid, String linkToken, long createdAt) {
		this.id = id;
		this.nickname = nickname;
		this.playerUuid = playerUuid;
		this.linkToken = linkToken;
		this.createdAt = createdAt;
	}

	public String getId() {
		return id;
	}

	public String getNickname() {
		return nickname;
	}

	public String getLinkToken() {
		return linkToken;
	}

	@Nullable
	public String getPlayerUuid() {
		return playerUuid;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		PlayerProfile that = (PlayerProfile) o;
		return createdAt == that.createdAt && Objects.equals(id, that.id) && Objects.equals(nickname, that.nickname)
				&& Objects.equals(linkToken, that.linkToken) && Objects.equals(playerUuid, that.playerUuid);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, nickname, linkToken, playerUuid, createdAt);
	}

	@Override
	public String toString() {
		return "PlayerProfile[id=" + id + ", nickname=" + nickname + "]";
	}
}
