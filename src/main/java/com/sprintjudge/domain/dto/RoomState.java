package com.sprintjudge.domain.dto;

import org.eclipse.jdt.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public final class RoomState {
	private String type;
	private String status;
	private int questionCount;
	private int currentQuestionIndex;
	private String currentQuestionId;
	private List<PlayerInfo> players;
	private String gameMode;

	@SuppressWarnings("null")
	public RoomState() {
	}

	public RoomState(String type, String status, int questionCount, int currentQuestionIndex, String currentQuestionId,
			List<PlayerInfo> players, String gameMode) {
		this.type = type;
		this.status = status;
		this.questionCount = questionCount;
		this.currentQuestionIndex = currentQuestionIndex;
		this.currentQuestionId = currentQuestionId;
		this.players = players;
		this.gameMode = gameMode;
	}

	/** Backward-compatible shape for callers that do not track the index yet. */
	public RoomState(String type, String status, int questionCount, String currentQuestionId, List<PlayerInfo> players,
			String gameMode) {
		this(type, status, questionCount, 0, currentQuestionId, players, gameMode);
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}

	public int getQuestionCount() {
		return questionCount;
	}
	public void setQuestionCount(int questionCount) {
		this.questionCount = questionCount;
	}

	public int getCurrentQuestionIndex() {
		return currentQuestionIndex;
	}
	public void setCurrentQuestionIndex(int currentQuestionIndex) {
		this.currentQuestionIndex = currentQuestionIndex;
	}

	public String getCurrentQuestionId() {
		return currentQuestionId;
	}
	public void setCurrentQuestionId(String currentQuestionId) {
		this.currentQuestionId = currentQuestionId;
	}

	public List<PlayerInfo> getPlayers() {
		return players;
	}
	public void setPlayers(List<PlayerInfo> players) {
		this.players = players;
	}

	public String getGameMode() {
		return gameMode;
	}
	public void setGameMode(String gameMode) {
		this.gameMode = gameMode;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o)
			return true;
		if (!(o instanceof RoomState))
			return false;
		RoomState other = (RoomState) o;
		return questionCount == other.questionCount && currentQuestionIndex == other.currentQuestionIndex
				&& Objects.equals(type, other.type) && Objects.equals(status, other.status)
				&& Objects.equals(currentQuestionId, other.currentQuestionId) && Objects.equals(players, other.players)
				&& Objects.equals(gameMode, other.gameMode);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, status, questionCount, currentQuestionIndex, currentQuestionId, players, gameMode);
	}

	@Override
	public String toString() {
		return "RoomState[type=" + type + ", status=" + status + ", questionCount=" + questionCount
				+ ", currentQuestionIndex=" + currentQuestionIndex + ", currentQuestionId=" + currentQuestionId
				+ ", players=" + players + ", gameMode=" + gameMode + "]";
	}

	public static final class PlayerInfo {
		private String uuid;
		private String name;
		private int score;
		private boolean connected;

		@SuppressWarnings("null")
		public PlayerInfo() {
		}

		public PlayerInfo(String uuid, String name, int score, boolean connected) {
			this.uuid = uuid;
			this.name = name;
			this.score = score;
			this.connected = connected;
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

		public boolean isConnected() {
			return connected;
		}
		public void setConnected(boolean connected) {
			this.connected = connected;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (!(o instanceof PlayerInfo))
				return false;
			PlayerInfo other = (PlayerInfo) o;
			return score == other.score && connected == other.connected && Objects.equals(uuid, other.uuid)
					&& Objects.equals(name, other.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(uuid, name, score, connected);
		}

		@Override
		public String toString() {
			return "PlayerInfo[uuid=" + uuid + ", name=" + name + ", score=" + score + ", connected=" + connected + "]";
		}
	}
}
