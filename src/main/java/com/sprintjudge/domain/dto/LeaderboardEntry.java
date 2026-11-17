package com.sprintjudge.domain.dto;

import org.eclipse.jdt.annotation.Nullable;
import java.util.Objects;

public final class LeaderboardEntry {
	private String uuid;
	private String name;
	private int score;
	private int rank;

	@SuppressWarnings("null")
	public LeaderboardEntry() {
	}

	public LeaderboardEntry(String uuid, String name, int score, int rank) {
		this.uuid = uuid;
		this.name = name;
		this.score = score;
		this.rank = rank;
	}

	public String getUuid() {
		return uuid;
	}
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public int getScore() {
		return score;
	}
	public void setScore(int score) {
		this.score = score;
	}

	public int getRank() {
		return rank;
	}
	public void setRank(int rank) {
		this.rank = rank;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o)
			return true;
		if (!(o instanceof LeaderboardEntry))
			return false;
		LeaderboardEntry other = (LeaderboardEntry) o;
		return score == other.score && rank == other.rank && Objects.equals(uuid, other.uuid)
				&& Objects.equals(name, other.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(uuid, name, score, rank);
	}

	@Override
	public String toString() {
		return "LeaderboardEntry[uuid=" + uuid + ", name=" + name + ", score=" + score + ", rank=" + rank + "]";
	}
}
