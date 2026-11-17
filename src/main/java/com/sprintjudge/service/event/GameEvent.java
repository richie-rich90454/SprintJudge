package com.sprintjudge.service.event;

import java.util.Objects;

/**
 * Domain events published via Spring ApplicationEventPublisher. Decouples game
 * lifecycle from side effects (logging, metrics, persistence).
 */
public interface GameEvent {
	String getPin();

	public static final class PlayerJoined implements GameEvent {
		private String pin;
		private String playerName;
		private String playerUuid;

		public PlayerJoined() {
		}

		public PlayerJoined(String pin, String playerName, String playerUuid) {
			this.pin = pin;
			this.playerName = playerName;
			this.playerUuid = playerUuid;
		}

		@Override
		public String getPin() {
			return pin;
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

		public void setPin(String pin) {
			this.pin = pin;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof PlayerJoined))
				return false;
			PlayerJoined other = (PlayerJoined) o;
			return Objects.equals(pin, other.pin) && Objects.equals(playerName, other.playerName)
					&& Objects.equals(playerUuid, other.playerUuid);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pin, playerName, playerUuid);
		}

		@Override
		public String toString() {
			return "PlayerJoined[pin=" + pin + ", playerName=" + playerName + ", playerUuid=" + playerUuid + "]";
		}
	}

	public static final class PlayerLeft implements GameEvent {
		private String pin;
		private String playerUuid;

		public PlayerLeft() {
		}

		public PlayerLeft(String pin, String playerUuid) {
			this.pin = pin;
			this.playerUuid = playerUuid;
		}

		@Override
		public String getPin() {
			return pin;
		}
		public void setPin(String pin) {
			this.pin = pin;
		}

		public String getPlayerUuid() {
			return playerUuid;
		}
		public void setPlayerUuid(String playerUuid) {
			this.playerUuid = playerUuid;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof PlayerLeft))
				return false;
			PlayerLeft other = (PlayerLeft) o;
			return Objects.equals(pin, other.pin) && Objects.equals(playerUuid, other.playerUuid);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pin, playerUuid);
		}

		@Override
		public String toString() {
			return "PlayerLeft[pin=" + pin + ", playerUuid=" + playerUuid + "]";
		}
	}

	public static final class QuestionStarted implements GameEvent {
		private String pin;
		private String questionId;
		private int questionIndex;

		public QuestionStarted() {
		}

		public QuestionStarted(String pin, String questionId, int questionIndex) {
			this.pin = pin;
			this.questionId = questionId;
			this.questionIndex = questionIndex;
		}

		@Override
		public String getPin() {
			return pin;
		}
		public void setPin(String pin) {
			this.pin = pin;
		}

		public String getQuestionId() {
			return questionId;
		}
		public void setQuestionId(String questionId) {
			this.questionId = questionId;
		}

		public int getQuestionIndex() {
			return questionIndex;
		}
		public void setQuestionIndex(int questionIndex) {
			this.questionIndex = questionIndex;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof QuestionStarted))
				return false;
			QuestionStarted other = (QuestionStarted) o;
			return questionIndex == other.questionIndex && Objects.equals(pin, other.pin)
					&& Objects.equals(questionId, other.questionId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pin, questionId, questionIndex);
		}

		@Override
		public String toString() {
			return "QuestionStarted[pin=" + pin + ", questionId=" + questionId + ", questionIndex=" + questionIndex
					+ "]";
		}
	}

	public static final class QuestionCompleted implements GameEvent {
		private String pin;
		private String questionId;
		private int questionIndex;

		public QuestionCompleted() {
		}

		public QuestionCompleted(String pin, String questionId, int questionIndex) {
			this.pin = pin;
			this.questionId = questionId;
			this.questionIndex = questionIndex;
		}

		@Override
		public String getPin() {
			return pin;
		}
		public void setPin(String pin) {
			this.pin = pin;
		}

		public String getQuestionId() {
			return questionId;
		}
		public void setQuestionId(String questionId) {
			this.questionId = questionId;
		}

		public int getQuestionIndex() {
			return questionIndex;
		}
		public void setQuestionIndex(int questionIndex) {
			this.questionIndex = questionIndex;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof QuestionCompleted))
				return false;
			QuestionCompleted other = (QuestionCompleted) o;
			return questionIndex == other.questionIndex && Objects.equals(pin, other.pin)
					&& Objects.equals(questionId, other.questionId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pin, questionId, questionIndex);
		}

		@Override
		public String toString() {
			return "QuestionCompleted[pin=" + pin + ", questionId=" + questionId + ", questionIndex=" + questionIndex
					+ "]";
		}
	}

	public static final class SubmissionReceived implements GameEvent {
		private String pin;
		private String questionId;
		private String playerUuid;
		private boolean correct;

		public SubmissionReceived() {
		}

		public SubmissionReceived(String pin, String questionId, String playerUuid, boolean correct) {
			this.pin = pin;
			this.questionId = questionId;
			this.playerUuid = playerUuid;
			this.correct = correct;
		}

		@Override
		public String getPin() {
			return pin;
		}
		public void setPin(String pin) {
			this.pin = pin;
		}

		public String getQuestionId() {
			return questionId;
		}
		public void setQuestionId(String questionId) {
			this.questionId = questionId;
		}

		public String getPlayerUuid() {
			return playerUuid;
		}
		public void setPlayerUuid(String playerUuid) {
			this.playerUuid = playerUuid;
		}

		public boolean isCorrect() {
			return correct;
		}
		public void setCorrect(boolean correct) {
			this.correct = correct;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof SubmissionReceived))
				return false;
			SubmissionReceived other = (SubmissionReceived) o;
			return correct == other.correct && Objects.equals(pin, other.pin)
					&& Objects.equals(questionId, other.questionId) && Objects.equals(playerUuid, other.playerUuid);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pin, questionId, playerUuid, correct);
		}

		@Override
		public String toString() {
			return "SubmissionReceived[pin=" + pin + ", questionId=" + questionId + ", playerUuid=" + playerUuid
					+ ", correct=" + correct + "]";
		}
	}

	public static final class GameCreated implements GameEvent {
		private String pin;
		private String quizId;
		private String gameMode;

		public GameCreated() {
		}

		public GameCreated(String pin, String quizId, String gameMode) {
			this.pin = pin;
			this.quizId = quizId;
			this.gameMode = gameMode;
		}

		@Override
		public String getPin() {
			return pin;
		}
		public void setPin(String pin) {
			this.pin = pin;
		}

		public String getQuizId() {
			return quizId;
		}
		public void setQuizId(String quizId) {
			this.quizId = quizId;
		}

		public String getGameMode() {
			return gameMode;
		}
		public void setGameMode(String gameMode) {
			this.gameMode = gameMode;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof GameCreated))
				return false;
			GameCreated other = (GameCreated) o;
			return Objects.equals(pin, other.pin) && Objects.equals(quizId, other.quizId)
					&& Objects.equals(gameMode, other.gameMode);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pin, quizId, gameMode);
		}

		@Override
		public String toString() {
			return "GameCreated[pin=" + pin + ", quizId=" + quizId + ", gameMode=" + gameMode + "]";
		}
	}

	public static final class GameEnded implements GameEvent {
		private String pin;
		private int totalPlayers;
		private int totalQuestions;

		public GameEnded() {
		}

		public GameEnded(String pin, int totalPlayers, int totalQuestions) {
			this.pin = pin;
			this.totalPlayers = totalPlayers;
			this.totalQuestions = totalQuestions;
		}

		@Override
		public String getPin() {
			return pin;
		}
		public void setPin(String pin) {
			this.pin = pin;
		}

		public int getTotalPlayers() {
			return totalPlayers;
		}
		public void setTotalPlayers(int totalPlayers) {
			this.totalPlayers = totalPlayers;
		}

		public int getTotalQuestions() {
			return totalQuestions;
		}
		public void setTotalQuestions(int totalQuestions) {
			this.totalQuestions = totalQuestions;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof GameEnded))
				return false;
			GameEnded other = (GameEnded) o;
			return totalPlayers == other.totalPlayers && totalQuestions == other.totalQuestions
					&& Objects.equals(pin, other.pin);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pin, totalPlayers, totalQuestions);
		}

		@Override
		public String toString() {
			return "GameEnded[pin=" + pin + ", totalPlayers=" + totalPlayers + ", totalQuestions=" + totalQuestions
					+ "]";
		}
	}
}
