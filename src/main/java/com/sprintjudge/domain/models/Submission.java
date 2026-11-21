package com.sprintjudge.domain.models;

import org.eclipse.jdt.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;

public final class Submission {

	private String id;

	private String gameSessionId;

	private String questionId;

	private String playerName;

	private String playerUuid;

	private String responseData;

	private int scoreEarned;

	private boolean correct;

	private String judgeLog;

	private int attemptCount;

	private @Nullable Instant submittedAt;

	@SuppressWarnings("all")
	public Submission() {
	}

	public Submission(String id, String gameSessionId, String questionId, String playerName, String playerUuid,
			String responseData, int scoreEarned, boolean correct, String judgeLog, int attemptCount,
			@Nullable Instant submittedAt) {
		this.id = id;
		this.gameSessionId = gameSessionId;
		this.questionId = questionId;
		this.playerName = playerName;
		this.playerUuid = playerUuid;
		this.responseData = responseData;
		this.scoreEarned = scoreEarned;
		this.correct = correct;
		this.judgeLog = judgeLog;
		this.attemptCount = attemptCount;
		this.submittedAt = submittedAt;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getGameSessionId() {
		return gameSessionId;
	}

	public void setGameSessionId(String gameSessionId) {
		this.gameSessionId = gameSessionId;
	}

	public String getQuestionId() {
		return questionId;
	}

	public void setQuestionId(String questionId) {
		this.questionId = questionId;
	}

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public String getPlayerUuid() {
		return playerUuid;
	}

	public void setPlayerUuid(String playerUuid) {
		this.playerUuid = playerUuid;
	}

	public String getResponseData() {
		return responseData;
	}

	public void setResponseData(String responseData) {
		this.responseData = responseData;
	}

	public int getScoreEarned() {
		return scoreEarned;
	}

	public void setScoreEarned(int scoreEarned) {
		this.scoreEarned = scoreEarned;
	}

	public boolean isCorrect() {
		return correct;
	}

	public void setCorrect(boolean correct) {
		this.correct = correct;
	}

	public String getJudgeLog() {
		return judgeLog;
	}

	public void setJudgeLog(String judgeLog) {
		this.judgeLog = judgeLog;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public @Nullable Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(@Nullable Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		Submission that = (Submission) obj;
		return scoreEarned == that.scoreEarned && correct == that.correct && attemptCount == that.attemptCount
				&& Objects.equals(id, that.id) && Objects.equals(gameSessionId, that.gameSessionId)
				&& Objects.equals(questionId, that.questionId) && Objects.equals(playerName, that.playerName)
				&& Objects.equals(playerUuid, that.playerUuid) && Objects.equals(responseData, that.responseData)
				&& Objects.equals(judgeLog, that.judgeLog) && Objects.equals(submittedAt, that.submittedAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, gameSessionId, questionId, playerName, playerUuid, responseData, scoreEarned, correct,
				judgeLog, attemptCount, submittedAt);
	}

	@Override
	public String toString() {
		return "Submission[id=" + id + ", gameSessionId=" + gameSessionId + ", questionId=" + questionId
				+ ", playerName=" + playerName + ", playerUuid=" + playerUuid + ", responseData=" + responseData
				+ ", scoreEarned=" + scoreEarned + ", correct=" + correct + ", judgeLog=" + judgeLog + ", attemptCount="
				+ attemptCount + ", submittedAt=" + submittedAt + "]";
	}
}
