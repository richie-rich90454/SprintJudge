package com.sprintjudge.domain.analytics;

import java.util.List;

/** Class-wide rollup, with an optional per-session breakdown. */
public final class ClassReport {
	private int sessionCount;
	private int players;
	private int questions;
	private int attempts;
	private int correct;
	private double accuracy;
	private double avgScore;
	private String hardestQuestionId;
	private String easiestQuestionId;
	private List<SessionStat> sessionStats;

	@SuppressWarnings("all")
	public ClassReport() {
	}

	public ClassReport(int sessionCount, int players, int questions, int attempts, int correct, double accuracy,
			double avgScore, String hardestQuestionId, String easiestQuestionId, List<SessionStat> sessionStats) {
		this.sessionCount = sessionCount;
		this.players = players;
		this.questions = questions;
		this.attempts = attempts;
		this.correct = correct;
		this.accuracy = accuracy;
		this.avgScore = avgScore;
		this.hardestQuestionId = hardestQuestionId;
		this.easiestQuestionId = easiestQuestionId;
		this.sessionStats = sessionStats;
	}

	public int getSessionCount() {
		return sessionCount;
	}

	public void setSessionCount(int sessionCount) {
		this.sessionCount = sessionCount;
	}

	public int getPlayers() {
		return players;
	}

	public void setPlayers(int players) {
		this.players = players;
	}

	public int getQuestions() {
		return questions;
	}

	public void setQuestions(int questions) {
		this.questions = questions;
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

	public String getHardestQuestionId() {
		return hardestQuestionId;
	}

	public void setHardestQuestionId(String hardestQuestionId) {
		this.hardestQuestionId = hardestQuestionId;
	}

	public String getEasiestQuestionId() {
		return easiestQuestionId;
	}

	public void setEasiestQuestionId(String easiestQuestionId) {
		this.easiestQuestionId = easiestQuestionId;
	}

	public List<SessionStat> getSessionStats() {
		return sessionStats;
	}

	public void setSessionStats(List<SessionStat> sessionStats) {
		this.sessionStats = sessionStats;
	}

	/** One game session (one class meeting) seen in the submission history. */
	public static final class SessionStat {
		private String sessionId;
		private String hostUserId;
		private int players;
		private int questions;
		private int attempts;
		private double accuracy;
		private double avgScore;

		@SuppressWarnings("all")
		public SessionStat() {
		}

		public SessionStat(String sessionId, String hostUserId, int players, int questions, int attempts,
				double accuracy, double avgScore) {
			this.sessionId = sessionId;
			this.hostUserId = hostUserId;
			this.players = players;
			this.questions = questions;
			this.attempts = attempts;
			this.accuracy = accuracy;
			this.avgScore = avgScore;
		}

		public String getSessionId() {
			return sessionId;
		}

		public void setSessionId(String sessionId) {
			this.sessionId = sessionId;
		}

		public String getHostUserId() {
			return hostUserId;
		}

		public void setHostUserId(String hostUserId) {
			this.hostUserId = hostUserId;
		}

		public int getPlayers() {
			return players;
		}

		public void setPlayers(int players) {
			this.players = players;
		}

		public int getQuestions() {
			return questions;
		}

		public void setQuestions(int questions) {
			this.questions = questions;
		}

		public int getAttempts() {
			return attempts;
		}

		public void setAttempts(int attempts) {
			this.attempts = attempts;
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
	}
}
