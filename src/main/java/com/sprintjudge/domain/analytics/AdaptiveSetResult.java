package com.sprintjudge.domain.analytics;

import java.util.List;
import java.util.Map;

/** The generated quiz id plus a short summary of how it was selected. */
public final class AdaptiveSetResult {
	private String quizId;
	private int questionCount;
	private int skipped;
	private double avgDifficulty;
	private double avgDiscrimination;
	private List<String> selectedIds;
	private Map<String, Integer> difficultyBands;

	@SuppressWarnings("all")
	public AdaptiveSetResult() {
	}

	public AdaptiveSetResult(String quizId, int questionCount, int skipped, double avgDifficulty,
			double avgDiscrimination, List<String> selectedIds, Map<String, Integer> difficultyBands) {
		this.quizId = quizId;
		this.questionCount = questionCount;
		this.skipped = skipped;
		this.avgDifficulty = avgDifficulty;
		this.avgDiscrimination = avgDiscrimination;
		this.selectedIds = selectedIds;
		this.difficultyBands = difficultyBands;
	}

	public String getQuizId() {
		return quizId;
	}

	public void setQuizId(String quizId) {
		this.quizId = quizId;
	}

	public int getQuestionCount() {
		return questionCount;
	}

	public void setQuestionCount(int questionCount) {
		this.questionCount = questionCount;
	}

	public int getSkipped() {
		return skipped;
	}

	public void setSkipped(int skipped) {
		this.skipped = skipped;
	}

	public double getAvgDifficulty() {
		return avgDifficulty;
	}

	public void setAvgDifficulty(double avgDifficulty) {
		this.avgDifficulty = avgDifficulty;
	}

	public double getAvgDiscrimination() {
		return avgDiscrimination;
	}

	public void setAvgDiscrimination(double avgDiscrimination) {
		this.avgDiscrimination = avgDiscrimination;
	}

	public List<String> getSelectedIds() {
		return selectedIds;
	}

	public void setSelectedIds(List<String> selectedIds) {
		this.selectedIds = selectedIds;
	}

	public Map<String, Integer> getDifficultyBands() {
		return difficultyBands;
	}

	public void setDifficultyBands(Map<String, Integer> difficultyBands) {
		this.difficultyBands = difficultyBands;
	}
}
