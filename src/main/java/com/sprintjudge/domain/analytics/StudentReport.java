package com.sprintjudge.domain.analytics;

/** One player's aggregated performance across finished and live sessions. */
public final class StudentReport {
	private String playerUuid;
	private String playerName;
	private int sessions;
	private int attempts;
	private int correct;
	private double accuracy;
	private double avgScore;
	private double avgAttemptsPerItem;

	@SuppressWarnings("all")
	public StudentReport() {
	}

	public StudentReport(String playerUuid, String playerName, int sessions, int attempts, int correct,
			double accuracy, double avgScore, double avgAttemptsPerItem) {
		this.playerUuid = playerUuid;
		this.playerName = playerName;
		this.sessions = sessions;
		this.attempts = attempts;
		this.correct = correct;
		this.accuracy = accuracy;
		this.avgScore = avgScore;
		this.avgAttemptsPerItem = avgAttemptsPerItem;
	}

	public String getPlayerUuid() {
		return playerUuid;
	}

	public void setPlayerUuid(String playerUuid) {
		this.playerUuid = playerUuid;
	}

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public int getSessions() {
		return sessions;
	}

	public void setSessions(int sessions) {
		this.sessions = sessions;
	}

	public int getAttempts() {
		return attempts;
	}

	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}

	public int getCorrect() {
		return correct;
	}

	public void setCorrect(int correct) {
		this.correct = correct;
	}

	public double getAccuracy() {
		return accuracy;
	}

	public void setAccuracy(double accuracy) {
		this.accuracy = accuracy;
	}

	public double getAvgScore() {
		return avgScore;
	}

	public void setAvgScore(double avgScore) {
		this.avgScore = avgScore;
	}

	public double getAvgAttemptsPerItem() {
		return avgAttemptsPerItem;
	}

	public void setAvgAttemptsPerItem(double avgAttemptsPerItem) {
		this.avgAttemptsPerItem = avgAttemptsPerItem;
	}
}
