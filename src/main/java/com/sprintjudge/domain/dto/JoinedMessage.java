package com.sprintjudge.domain.dto;

import org.eclipse.jdt.annotation.Nullable;
import java.util.Objects;

public final class JoinedMessage {
	private String type;
	private String uuid;
	private String rejoinToken;
	private RoomState room;

	@SuppressWarnings("all")
	public JoinedMessage() {
	}

	public JoinedMessage(String type, String uuid, String rejoinToken, RoomState room) {
		this.type = type;
		this.uuid = uuid;
		this.rejoinToken = rejoinToken;
		this.room = room;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	public String getUuid() {
		return uuid;
	}
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getRejoinToken() {
		return rejoinToken;
	}
	public void setRejoinToken(String rejoinToken) {
		this.rejoinToken = rejoinToken;
	}

	public RoomState getRoom() {
		return room;
	}
	public void setRoom(RoomState room) {
		this.room = room;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o)
			return true;
		if (!(o instanceof JoinedMessage))
			return false;
		JoinedMessage other = (JoinedMessage) o;
		return Objects.equals(type, other.type) && Objects.equals(uuid, other.uuid)
				&& Objects.equals(rejoinToken, other.rejoinToken) && Objects.equals(room, other.room);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, uuid, rejoinToken, room);
	}

	@Override
	public String toString() {
		return "JoinedMessage[type=" + type + ", uuid=" + uuid + ", rejoinToken=" + rejoinToken + ", room=" + room
				+ "]";
	}
}
