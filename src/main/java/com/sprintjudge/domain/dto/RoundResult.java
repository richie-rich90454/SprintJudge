package com.sprintjudge.domain.dto;

import org.eclipse.jdt.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Round reveal. {@code correctAnswer} carries only the answer-bearing config
 * keys for the question type (never full config, never hidden OJ test cases).
 * {@code scores} reports each player's standing plus this round's contribution
 * so the client can show streaks / bonuses.
 */
public final class RoundResult {
	private String type;
	private String questionId;
	private boolean revealed;
	private JsonNode correctAnswer;
	private List<PlayerScore> scores;
	private Map<String, Integer> answerCounts;

	@SuppressWarnings("null")
	public RoundResult() {
	}

	public RoundResult(String type, String questionId, boolean revealed, JsonNode correctAnswer,
			List<PlayerScore> scores, Map<String, Integer> answerCounts) {
		this.type = type;
		this.questionId = questionId;
		this.revealed = revealed;
		this.correctAnswer = correctAnswer;
		this.scores = scores;
		this.answerCounts = answerCounts;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	public String getQuestionId() {
		return questionId;
	}
	public void setQuestionId(String questionId) {
		this.questionId = questionId;
	}

	public boolean isRevealed() {
		return revealed;
	}
	public void setRevealed(boolean revealed) {
		this.revealed = revealed;
	}

	public JsonNode getCorrectAnswer() {
		return correctAnswer;
	}
	public void setCorrectAnswer(JsonNode correctAnswer) {
		this.correctAnswer = correctAnswer;
	}

	public List<PlayerScore> getScores() {
		return scores;
	}
	public void setScores(List<PlayerScore> scores) {
		this.scores = scores;
	}

	public Map<String, Integer> getAnswerCounts() {
		return answerCounts;
	}
	public void setAnswerCounts(Map<String, Integer> answerCounts) {
		this.answerCounts = answerCounts;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o)
			return true;
		if (!(o instanceof RoundResult))
			return false;
		RoundResult other = (RoundResult) o;
		return revealed == other.revealed && Objects.equals(type, other.type)
				&& Objects.equals(questionId, other.questionId) && Objects.equals(correctAnswer, other.correctAnswer)
				&& Objects.equals(scores, other.scores) && Objects.equals(answerCounts, other.answerCounts);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, questionId, revealed, correctAnswer, scores, answerCounts);
	}

	@Override
	public String toString() {
		return "RoundResult[type=" + type + ", questionId=" + questionId + ", revealed=" + revealed + ", correctAnswer="
				+ correctAnswer + ", scores=" + scores + ", answerCounts=" + answerCounts + "]";
	}

	public static final class PlayerScore {
		private String uuid;
		private String name;
		private int score;
		private boolean correct;
		private int roundScore;
		private int streak;
		private int bonus;

		@SuppressWarnings("null")
		public PlayerScore() {
		}

		public PlayerScore(String uuid, String name, int score, boolean correct, int roundScore, int streak,
				int bonus) {
			this.uuid = uuid;
			this.name = name;
			this.score = score;
			this.correct = correct;
			this.roundScore = roundScore;
			this.streak = streak;
			this.bonus = bonus;
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

		public boolean isCorrect() {
			return correct;
		}
		public void setCorrect(boolean correct) {
			this.correct = correct;
		}

		public int getRoundScore() {
			return roundScore;
		}
		public void setRoundScore(int roundScore) {
			this.roundScore = roundScore;
		}

		public int getStreak() {
			return streak;
		}
		public void setStreak(int streak) {
			this.streak = streak;
		}

		public int getBonus() {
			return bonus;
		}
		public void setBonus(int bonus) {
			this.bonus = bonus;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (!(o instanceof PlayerScore))
				return false;
			PlayerScore other = (PlayerScore) o;
			return score == other.score && correct == other.correct && roundScore == other.roundScore
					&& streak == other.streak && bonus == other.bonus && Objects.equals(uuid, other.uuid)
					&& Objects.equals(name, other.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(uuid, name, score, correct, roundScore, streak, bonus);
		}

		@Override
		public String toString() {
			return "PlayerScore[uuid=" + uuid + ", name=" + name + ", score=" + score + ", correct=" + correct
					+ ", roundScore=" + roundScore + ", streak=" + streak + ", bonus=" + bonus + "]";
		}
	}
}
