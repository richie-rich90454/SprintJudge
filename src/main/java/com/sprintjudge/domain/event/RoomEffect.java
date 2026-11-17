package com.sprintjudge.domain.event;

import org.eclipse.jdt.annotation.Nullable;
import java.util.Objects;

/**
 * Side effects a reducer may request. The handler runs them; the domain only
 * describes them, keeping the fold pure and testable.
 */
public interface RoomEffect {

	String getRoomId();

	public static final class Broadcast implements RoomEffect {

		private String roomId;

		private String type;

		private String payloadJson;

		@SuppressWarnings("null")
		public Broadcast() {
		}

		public Broadcast(String roomId, String type, String payloadJson) {
			this.roomId = roomId;
			this.type = type;
			this.payloadJson = payloadJson;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getPayloadJson() {
			return payloadJson;
		}

		public void setPayloadJson(String payloadJson) {
			this.payloadJson = payloadJson;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			Broadcast that = (Broadcast) obj;
			return Objects.equals(roomId, that.roomId) && Objects.equals(type, that.type)
					&& Objects.equals(payloadJson, that.payloadJson);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, type, payloadJson);
		}

		@Override
		public String toString() {
			return "Broadcast[roomId=" + roomId + ", type=" + type + ", payloadJson=" + payloadJson + "]";
		}
	}

	public static final class Persist implements RoomEffect {

		private String roomId;

		private long seq;

		private String type;

		private String payloadJson;

		@SuppressWarnings("null")
		public Persist() {
		}

		public Persist(String roomId, long seq, String type, String payloadJson) {
			this.roomId = roomId;
			this.seq = seq;
			this.type = type;
			this.payloadJson = payloadJson;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getPayloadJson() {
			return payloadJson;
		}

		public void setPayloadJson(String payloadJson) {
			this.payloadJson = payloadJson;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			Persist persist = (Persist) obj;
			return seq == persist.seq && Objects.equals(roomId, persist.roomId) && Objects.equals(type, persist.type)
					&& Objects.equals(payloadJson, persist.payloadJson);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, type, payloadJson);
		}

		@Override
		public String toString() {
			return "Persist[roomId=" + roomId + ", seq=" + seq + ", type=" + type + ", payloadJson=" + payloadJson
					+ "]";
		}
	}

	public static final class Schedule implements RoomEffect {

		private String roomId;

		private String kind;

		private long delayMs;

		@SuppressWarnings("null")
		public Schedule() {
		}

		public Schedule(String roomId, String kind, long delayMs) {
			this.roomId = roomId;
			this.kind = kind;
			this.delayMs = delayMs;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		public String getKind() {
			return kind;
		}

		public void setKind(String kind) {
			this.kind = kind;
		}

		public long getDelayMs() {
			return delayMs;
		}

		public void setDelayMs(long delayMs) {
			this.delayMs = delayMs;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			Schedule schedule = (Schedule) obj;
			return delayMs == schedule.delayMs && Objects.equals(roomId, schedule.roomId)
					&& Objects.equals(kind, schedule.kind);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, kind, delayMs);
		}

		@Override
		public String toString() {
			return "Schedule[roomId=" + roomId + ", kind=" + kind + ", delayMs=" + delayMs + "]";
		}
	}

	public static final class Award implements RoomEffect {

		private String roomId;

		private String playerId;

		private int points;

		@SuppressWarnings("null")
		public Award() {
		}

		public Award(String roomId, String playerId, int points) {
			this.roomId = roomId;
			this.playerId = playerId;
			this.points = points;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		public String getPlayerId() {
			return playerId;
		}

		public void setPlayerId(String playerId) {
			this.playerId = playerId;
		}

		public int getPoints() {
			return points;
		}

		public void setPoints(int points) {
			this.points = points;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			Award award = (Award) obj;
			return points == award.points && Objects.equals(roomId, award.roomId)
					&& Objects.equals(playerId, award.playerId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, playerId, points);
		}

		@Override
		public String toString() {
			return "Award[roomId=" + roomId + ", playerId=" + playerId + ", points=" + points + "]";
		}
	}
}
