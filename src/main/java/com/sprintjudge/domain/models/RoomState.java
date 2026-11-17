package com.sprintjudge.domain.models;

import org.eclipse.jdt.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot view of a room. The live path still owns transitions; this class is
 * the frozen contract new snapshot and resume flows share.
 */
public final class RoomState {

	private String id;

	private String pin;

	private String mode;

	private String phase;

	private String hostName;

	private Map<String, String> players;

	private List<String> roundIds;

	private int currentRoundIndex;

	private long seq;

	private Instant createdAt;

	@SuppressWarnings("null")
	public RoomState() {
	}

	public RoomState(String id, String pin, String mode, String phase, String hostName, Map<String, String> players,
			List<String> roundIds, int currentRoundIndex, long seq, Instant createdAt) {
		this.id = id;
		this.pin = pin;
		this.mode = mode;
		this.phase = phase;
		this.hostName = hostName;
		this.players = players;
		this.roundIds = roundIds;
		this.currentRoundIndex = currentRoundIndex;
		this.seq = seq;
		this.createdAt = createdAt;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPin() {
		return pin;
	}

	public void setPin(String pin) {
		this.pin = pin;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getPhase() {
		return phase;
	}

	public void setPhase(String phase) {
		this.phase = phase;
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public Map<String, String> getPlayers() {
		return players;
	}

	public void setPlayers(Map<String, String> players) {
		this.players = players;
	}

	public List<String> getRoundIds() {
		return roundIds;
	}

	public void setRoundIds(List<String> roundIds) {
		this.roundIds = roundIds;
	}

	public int getCurrentRoundIndex() {
		return currentRoundIndex;
	}

	public void setCurrentRoundIndex(int currentRoundIndex) {
		this.currentRoundIndex = currentRoundIndex;
	}

	public long getSeq() {
		return seq;
	}

	public void setSeq(long seq) {
		this.seq = seq;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		RoomState roomState = (RoomState) obj;
		return currentRoundIndex == roomState.currentRoundIndex && seq == roomState.seq
				&& Objects.equals(id, roomState.id) && Objects.equals(pin, roomState.pin)
				&& Objects.equals(mode, roomState.mode) && Objects.equals(phase, roomState.phase)
				&& Objects.equals(hostName, roomState.hostName) && Objects.equals(players, roomState.players)
				&& Objects.equals(roundIds, roomState.roundIds) && Objects.equals(createdAt, roomState.createdAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, pin, mode, phase, hostName, players, roundIds, currentRoundIndex, seq, createdAt);
	}

	@Override
	public String toString() {
		return "RoomState[id=" + id + ", pin=" + pin + ", mode=" + mode + ", phase=" + phase + ", hostName=" + hostName
				+ ", players=" + players + ", roundIds=" + roundIds + ", currentRoundIndex=" + currentRoundIndex
				+ ", seq=" + seq + ", createdAt=" + createdAt + "]";
	}
}
