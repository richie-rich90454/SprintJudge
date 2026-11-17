package com.sprintjudge.domain.dto;

import org.eclipse.jdt.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Delta leaderboard broadcast. Clients apply {@code entries} and remember
 * {@code seq}; a gap triggers RESYNC_LEADERBOARD, answered by a full
 * {@code resync=true} batch. Ranks are exact at emission time.
 */
public final class LeaderboardDelta {
	private String type;
	private long seq;
	private boolean resync;
	private List<LeaderboardEntry> entries;

	@SuppressWarnings("null")
	public LeaderboardDelta() {
	}

	public LeaderboardDelta(String type, long seq, boolean resync, List<LeaderboardEntry> entries) {
		this.type = type;
		this.seq = seq;
		this.resync = resync;
		this.entries = entries;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	public long getSeq() {
		return seq;
	}
	public void setSeq(long seq) {
		this.seq = seq;
	}

	public boolean isResync() {
		return resync;
	}
	public void setResync(boolean resync) {
		this.resync = resync;
	}

	public List<LeaderboardEntry> getEntries() {
		return entries;
	}
	public void setEntries(List<LeaderboardEntry> entries) {
		this.entries = entries;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o)
			return true;
		if (!(o instanceof LeaderboardDelta))
			return false;
		LeaderboardDelta other = (LeaderboardDelta) o;
		return seq == other.seq && resync == other.resync && Objects.equals(type, other.type)
				&& Objects.equals(entries, other.entries);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, seq, resync, entries);
	}

	@Override
	public String toString() {
		return "LeaderboardDelta[type=" + type + ", seq=" + seq + ", resync=" + resync + ", entries=" + entries + "]";
	}
}
