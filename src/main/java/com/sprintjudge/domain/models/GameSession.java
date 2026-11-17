package com.sprintjudge.domain.models;

import org.eclipse.jdt.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;

public final class GameSession {

	private String id;

	private String quizId;

	private String pinCode;

	private String hostUserId;

	private String status;

	private String gameMode;

	private int currentQuestionIndex;

	private @Nullable Instant startedAt;

	private @Nullable Instant endedAt;

	private String settingsOverride;

	private @Nullable Instant createdAt;

	@SuppressWarnings("null")
	public GameSession() {
	}

	public GameSession(String id, String quizId, String pinCode, String hostUserId, String status, String gameMode,
			int currentQuestionIndex, @Nullable Instant startedAt, @Nullable Instant endedAt, String settingsOverride,
			@Nullable Instant createdAt) {
		this.id = id;
		this.quizId = quizId;
		this.pinCode = pinCode;
		this.hostUserId = hostUserId;
		this.status = status;
		this.gameMode = gameMode;
		this.currentQuestionIndex = currentQuestionIndex;
		this.startedAt = startedAt;
		this.endedAt = endedAt;
		this.settingsOverride = settingsOverride;
		this.createdAt = createdAt;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getQuizId() {
		return quizId;
	}

	public void setQuizId(String quizId) {
		this.quizId = quizId;
	}

	public String getPinCode() {
		return pinCode;
	}

	public void setPinCode(String pinCode) {
		this.pinCode = pinCode;
	}

	public String getHostUserId() {
		return hostUserId;
	}

	public void setHostUserId(String hostUserId) {
		this.hostUserId = hostUserId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getGameMode() {
		return gameMode;
	}

	public void setGameMode(String gameMode) {
		this.gameMode = gameMode;
	}

	public int getCurrentQuestionIndex() {
		return currentQuestionIndex;
	}

	public void setCurrentQuestionIndex(int currentQuestionIndex) {
		this.currentQuestionIndex = currentQuestionIndex;
	}

	public @Nullable Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(@Nullable Instant startedAt) {
		this.startedAt = startedAt;
	}

	public @Nullable Instant getEndedAt() {
		return endedAt;
	}

	public void setEndedAt(@Nullable Instant endedAt) {
		this.endedAt = endedAt;
	}

	public String getSettingsOverride() {
		return settingsOverride;
	}

	public void setSettingsOverride(String settingsOverride) {
		this.settingsOverride = settingsOverride;
	}

	public @Nullable Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(@Nullable Instant createdAt) {
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
		GameSession that = (GameSession) obj;
		return currentQuestionIndex == that.currentQuestionIndex && Objects.equals(id, that.id)
				&& Objects.equals(quizId, that.quizId) && Objects.equals(pinCode, that.pinCode)
				&& Objects.equals(hostUserId, that.hostUserId) && Objects.equals(status, that.status)
				&& Objects.equals(gameMode, that.gameMode) && Objects.equals(startedAt, that.startedAt)
				&& Objects.equals(endedAt, that.endedAt) && Objects.equals(settingsOverride, that.settingsOverride)
				&& Objects.equals(createdAt, that.createdAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, quizId, pinCode, hostUserId, status, gameMode, currentQuestionIndex, startedAt, endedAt,
				settingsOverride, createdAt);
	}

	@Override
	public String toString() {
		return "GameSession[id=" + id + ", quizId=" + quizId + ", pinCode=" + pinCode + ", hostUserId=" + hostUserId
				+ ", status=" + status + ", gameMode=" + gameMode + ", currentQuestionIndex=" + currentQuestionIndex
				+ ", startedAt=" + startedAt + ", endedAt=" + endedAt + ", settingsOverride=" + settingsOverride
				+ ", createdAt=" + createdAt + "]";
	}
}
