package com.sprintjudge.domain.event;

import org.eclipse.jdt.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only room history. State is a fold over these events. Kept additive:
 * new event types are added, never renamed or removed.
 */
public interface RoomEvent {

	String getRoomId();

	long getSeq();

	Instant getAt();

	public static final class RoomCreated implements RoomEvent {

		private String roomId;

		private long seq;

		private Instant at;

		private String mode;

		@SuppressWarnings("all")
		public RoomCreated() {
		}

		public RoomCreated(String roomId, long seq, Instant at, String mode) {
			this.roomId = roomId;
			this.seq = seq;
			this.at = at;
			this.mode = mode;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		@Override
		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		@Override
		public Instant getAt() {
			return at;
		}

		public void setAt(Instant at) {
			this.at = at;
		}

		public String getMode() {
			return mode;
		}

		public void setMode(String mode) {
			this.mode = mode;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			RoomCreated that = (RoomCreated) obj;
			return seq == that.seq && Objects.equals(roomId, that.roomId) && Objects.equals(at, that.at)
					&& Objects.equals(mode, that.mode);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, at, mode);
		}

		@Override
		public String toString() {
			return "RoomCreated[roomId=" + roomId + ", seq=" + seq + ", at=" + at + ", mode=" + mode + "]";
		}
	}

	public static final class PlayerJoined implements RoomEvent {

		private String roomId;

		private long seq;

		private Instant at;

		private String playerId;

		private String nickname;

		@SuppressWarnings("all")
		public PlayerJoined() {
		}

		public PlayerJoined(String roomId, long seq, Instant at, String playerId, String nickname) {
			this.roomId = roomId;
			this.seq = seq;
			this.at = at;
			this.playerId = playerId;
			this.nickname = nickname;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		@Override
		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		@Override
		public Instant getAt() {
			return at;
		}

		public void setAt(Instant at) {
			this.at = at;
		}

		public String getPlayerId() {
			return playerId;
		}

		public void setPlayerId(String playerId) {
			this.playerId = playerId;
		}

		public String getNickname() {
			return nickname;
		}

		public void setNickname(String nickname) {
			this.nickname = nickname;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			PlayerJoined that = (PlayerJoined) obj;
			return seq == that.seq && Objects.equals(roomId, that.roomId) && Objects.equals(at, that.at)
					&& Objects.equals(playerId, that.playerId) && Objects.equals(nickname, that.nickname);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, at, playerId, nickname);
		}

		@Override
		public String toString() {
			return "PlayerJoined[roomId=" + roomId + ", seq=" + seq + ", at=" + at + ", playerId=" + playerId
					+ ", nickname=" + nickname + "]";
		}
	}

	public static final class PlayerLeft implements RoomEvent {

		private String roomId;

		private long seq;

		private Instant at;

		private String playerId;

		@SuppressWarnings("all")
		public PlayerLeft() {
		}

		public PlayerLeft(String roomId, long seq, Instant at, String playerId) {
			this.roomId = roomId;
			this.seq = seq;
			this.at = at;
			this.playerId = playerId;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		@Override
		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		@Override
		public Instant getAt() {
			return at;
		}

		public void setAt(Instant at) {
			this.at = at;
		}

		public String getPlayerId() {
			return playerId;
		}

		public void setPlayerId(String playerId) {
			this.playerId = playerId;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			PlayerLeft that = (PlayerLeft) obj;
			return seq == that.seq && Objects.equals(roomId, that.roomId) && Objects.equals(at, that.at)
					&& Objects.equals(playerId, that.playerId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, at, playerId);
		}

		@Override
		public String toString() {
			return "PlayerLeft[roomId=" + roomId + ", seq=" + seq + ", at=" + at + ", playerId=" + playerId + "]";
		}
	}

	public static final class RoundStarted implements RoomEvent {

		private String roomId;

		private long seq;

		private Instant at;

		private String roundId;

		private int index;

		@SuppressWarnings("all")
		public RoundStarted() {
		}

		public RoundStarted(String roomId, long seq, Instant at, String roundId, int index) {
			this.roomId = roomId;
			this.seq = seq;
			this.at = at;
			this.roundId = roundId;
			this.index = index;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		@Override
		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		@Override
		public Instant getAt() {
			return at;
		}

		public void setAt(Instant at) {
			this.at = at;
		}

		public String getRoundId() {
			return roundId;
		}

		public void setRoundId(String roundId) {
			this.roundId = roundId;
		}

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			RoundStarted that = (RoundStarted) obj;
			return seq == that.seq && index == that.index && Objects.equals(roomId, that.roomId)
					&& Objects.equals(at, that.at) && Objects.equals(roundId, that.roundId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, at, roundId, index);
		}

		@Override
		public String toString() {
			return "RoundStarted[roomId=" + roomId + ", seq=" + seq + ", at=" + at + ", roundId=" + roundId + ", index="
					+ index + "]";
		}
	}

	public static final class AnswerSubmitted implements RoomEvent {

		private String roomId;

		private long seq;

		private Instant at;

		private String playerId;

		private String questionId;

		private boolean correct;

		private double seconds;

		@SuppressWarnings("all")
		public AnswerSubmitted() {
		}

		public AnswerSubmitted(String roomId, long seq, Instant at, String playerId, String questionId, boolean correct,
				double seconds) {
			this.roomId = roomId;
			this.seq = seq;
			this.at = at;
			this.playerId = playerId;
			this.questionId = questionId;
			this.correct = correct;
			this.seconds = seconds;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		@Override
		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		@Override
		public Instant getAt() {
			return at;
		}

		public void setAt(Instant at) {
			this.at = at;
		}

		public String getPlayerId() {
			return playerId;
		}

		public void setPlayerId(String playerId) {
			this.playerId = playerId;
		}

		public String getQuestionId() {
			return questionId;
		}

		public void setQuestionId(String questionId) {
			this.questionId = questionId;
		}

		public boolean isCorrect() {
			return correct;
		}

		public void setCorrect(boolean correct) {
			this.correct = correct;
		}

		public double getSeconds() {
			return seconds;
		}

		public void setSeconds(double seconds) {
			this.seconds = seconds;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			AnswerSubmitted that = (AnswerSubmitted) obj;
			return seq == that.seq && correct == that.correct && Double.compare(seconds, that.seconds) == 0
					&& Objects.equals(roomId, that.roomId) && Objects.equals(at, that.at)
					&& Objects.equals(playerId, that.playerId) && Objects.equals(questionId, that.questionId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, at, playerId, questionId, correct, seconds);
		}

		@Override
		public String toString() {
			return "AnswerSubmitted[roomId=" + roomId + ", seq=" + seq + ", at=" + at + ", playerId=" + playerId
					+ ", questionId=" + questionId + ", correct=" + correct + ", seconds=" + seconds + "]";
		}
	}

	public static final class RoundEnded implements RoomEvent {

		private String roomId;

		private long seq;

		private Instant at;

		private String roundId;

		@SuppressWarnings("all")
		public RoundEnded() {
		}

		public RoundEnded(String roomId, long seq, Instant at, String roundId) {
			this.roomId = roomId;
			this.seq = seq;
			this.at = at;
			this.roundId = roundId;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		@Override
		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		@Override
		public Instant getAt() {
			return at;
		}

		public void setAt(Instant at) {
			this.at = at;
		}

		public String getRoundId() {
			return roundId;
		}

		public void setRoundId(String roundId) {
			this.roundId = roundId;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			RoundEnded that = (RoundEnded) obj;
			return seq == that.seq && Objects.equals(roomId, that.roomId) && Objects.equals(at, that.at)
					&& Objects.equals(roundId, that.roundId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, at, roundId);
		}

		@Override
		public String toString() {
			return "RoundEnded[roomId=" + roomId + ", seq=" + seq + ", at=" + at + ", roundId=" + roundId + "]";
		}
	}

	public static final class GameEnded implements RoomEvent {

		private String roomId;

		private long seq;

		private Instant at;

		@SuppressWarnings("all")
		public GameEnded() {
		}

		public GameEnded(String roomId, long seq, Instant at) {
			this.roomId = roomId;
			this.seq = seq;
			this.at = at;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		@Override
		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		@Override
		public Instant getAt() {
			return at;
		}

		public void setAt(Instant at) {
			this.at = at;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			GameEnded that = (GameEnded) obj;
			return seq == that.seq && Objects.equals(roomId, that.roomId) && Objects.equals(at, that.at);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, at);
		}

		@Override
		public String toString() {
			return "GameEnded[roomId=" + roomId + ", seq=" + seq + ", at=" + at + "]";
		}
	}

	public static final class ScoreComputed implements RoomEvent {

		private String roomId;

		private long seq;

		private Instant at;

		private String playerId;

		private double sjs;

		@SuppressWarnings("all")
		public ScoreComputed() {
		}

		public ScoreComputed(String roomId, long seq, Instant at, String playerId, double sjs) {
			this.roomId = roomId;
			this.seq = seq;
			this.at = at;
			this.playerId = playerId;
			this.sjs = sjs;
		}

		@Override
		public String getRoomId() {
			return roomId;
		}

		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		@Override
		public long getSeq() {
			return seq;
		}

		public void setSeq(long seq) {
			this.seq = seq;
		}

		@Override
		public Instant getAt() {
			return at;
		}

		public void setAt(Instant at) {
			this.at = at;
		}

		public String getPlayerId() {
			return playerId;
		}

		public void setPlayerId(String playerId) {
			this.playerId = playerId;
		}

		public double getSjs() {
			return sjs;
		}

		public void setSjs(double sjs) {
			this.sjs = sjs;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			ScoreComputed that = (ScoreComputed) obj;
			return seq == that.seq && Double.compare(sjs, that.sjs) == 0 && Objects.equals(roomId, that.roomId)
					&& Objects.equals(at, that.at) && Objects.equals(playerId, that.playerId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(roomId, seq, at, playerId, sjs);
		}

		@Override
		public String toString() {
			return "ScoreComputed[roomId=" + roomId + ", seq=" + seq + ", at=" + at + ", playerId=" + playerId
					+ ", sjs=" + sjs + "]";
		}
	}
}
