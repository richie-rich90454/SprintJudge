package com.sprintjudge.domain.dto;

import java.util.Objects;

/**
 * Personal feedback after a coding submission is judged. Lets the player see
 * pass/fail immediately instead of waiting for the leaderboard to move.
 */
public final class SubmissionResult {
	private String type;
	private String questionId;
	private int score;
	private boolean allPassed;
	private int passed;
	private int totalTests;
	private String aiFeedback;

	public SubmissionResult() {
	}

	public SubmissionResult(String type, String questionId, int score, boolean allPassed, int passed, int totalTests,
			String aiFeedback) {
		this.type = type;
		this.questionId = questionId;
		this.score = score;
		this.allPassed = allPassed;
		this.passed = passed;
		this.totalTests = totalTests;
		this.aiFeedback = aiFeedback;
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

	public int getScore() {
		return score;
	}
	public void setScore(int score) {
		this.score = score;
	}

	public boolean isAllPassed() {
		return allPassed;
	}
	public void setAllPassed(boolean allPassed) {
		this.allPassed = allPassed;
	}

	public int getPassed() {
		return passed;
	}
	public void setPassed(int passed) {
		this.passed = passed;
	}

	public int getTotalTests() {
		return totalTests;
	}
	public void setTotalTests(int totalTests) {
		this.totalTests = totalTests;
	}

	public String getAiFeedback() {
		return aiFeedback;
	}
	public void setAiFeedback(String aiFeedback) {
		this.aiFeedback = aiFeedback;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof SubmissionResult))
			return false;
		SubmissionResult other = (SubmissionResult) o;
		return score == other.score && allPassed == other.allPassed && passed == other.passed
				&& totalTests == other.totalTests && Objects.equals(type, other.type)
				&& Objects.equals(questionId, other.questionId) && Objects.equals(aiFeedback, other.aiFeedback);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, questionId, score, allPassed, passed, totalTests, aiFeedback);
	}

	@Override
	public String toString() {
		return "SubmissionResult[type=" + type + ", questionId=" + questionId + ", score=" + score + ", allPassed="
				+ allPassed + ", passed=" + passed + ", totalTests=" + totalTests + ", aiFeedback=" + aiFeedback + "]";
	}
}
