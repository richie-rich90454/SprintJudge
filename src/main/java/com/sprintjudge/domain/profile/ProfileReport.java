package com.sprintjudge.domain.profile;

import org.eclipse.jdt.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read model returned by the profile API: identity, stats, history, awards,
 * portfolio.
 */
public final class ProfileReport {

	private final Profile profile;
	private final Stats stats;
	private final List<ScoreEntry> history;
	private final List<Achievement> achievements;
	private final List<PortfolioEntry> portfolio;

	public ProfileReport(Profile profile, Stats stats, List<ScoreEntry> history, List<Achievement> achievements,
			List<PortfolioEntry> portfolio) {
		this.profile = profile;
		this.stats = stats;
		this.history = history;
		this.achievements = achievements;
		this.portfolio = portfolio;
	}

	public Profile getProfile() {
		return profile;
	}

	public Stats getStats() {
		return stats;
	}

	public List<ScoreEntry> getHistory() {
		return history;
	}

	public List<Achievement> getAchievements() {
		return achievements;
	}

	public List<PortfolioEntry> getPortfolio() {
		return portfolio;
	}

	public static Stats stats(List<ScoreEntry> history, List<Boolean> correctness) {
		int sessions = history.size();
		int totalScore = 0;
		int bestScore = 0;
		int answers = 0;
		int correct = 0;
		for (ScoreEntry entry : history) {
			totalScore += entry.getScore();
			bestScore = Math.max(bestScore, entry.getScore());
			answers += entry.getAnswers();
			correct += entry.getCorrect();
		}
		double accuracy = answers == 0 ? 0 : (double) correct / answers;
		return new Stats(sessions, totalScore, bestScore, answers, correct, accuracy, longestStreak(correctness));
	}

	private static int longestStreak(List<Boolean> correctness) {
		int best = 0;
		int run = 0;
		for (boolean right : correctness) {
			run = right ? run + 1 : 0;
			best = Math.max(best, run);
		}
		return best;
	}

	public static List<Achievement> achievements(Stats s) {
		List<Achievement> out = new ArrayList<>();
		out.add(new Achievement("FIRST_QUIZ", "First finish", "Complete a quiz.", s.getSessions() >= 1));
		out.add(new Achievement("FIVE_QUIZZES", "Regular", "Complete five quizzes.", s.getSessions() >= 5));
		out.add(new Achievement("SHARP_SHOOTER", "Sharp shooter", "At least 10 answers with 80% correct.",
				s.getAnswers() >= 10 && s.getAccuracy() >= 0.8));
		out.add(new Achievement("STREAK_5", "On a roll", "Five correct answers in a row.", s.getBestStreak() >= 5));
		out.add(new Achievement("SCORE_1000", "High scorer", "Earn 1,000 points in total.", s.getTotalScore() >= 1000));
		return List.copyOf(out);
	}

	public static List<PortfolioEntry> portfolio(List<ScoreEntry> history) {
		Map<String, PortfolioEntry> byQuiz = new LinkedHashMap<>();
		for (ScoreEntry entry : history) {
			PortfolioEntry current = byQuiz.get(entry.getQuizId());
			if (current == null) {
				byQuiz.put(entry.getQuizId(), new PortfolioEntry(entry.getQuizId(), entry.getQuizTitle(), 1,
						entry.getScore(), entry.getPlayedAt()));
			} else {
				byQuiz.put(entry.getQuizId(), current.with(entry.getScore(), entry.getPlayedAt()));
			}
		}
		return List.copyOf(byQuiz.values());
	}

	public static final class Profile {
		private final String id;
		private final String nickname;
		@Nullable
		private final String playerUuid;
		private final String linkToken;
		private final long createdAt;

		public Profile(PlayerProfile p) {
			this.id = p.getId();
			this.nickname = p.getNickname();
			this.playerUuid = p.getPlayerUuid();
			this.linkToken = p.getLinkToken();
			this.createdAt = p.getCreatedAt();
		}

		public String getId() {
			return id;
		}

		public String getNickname() {
			return nickname;
		}

		@Nullable
		public String getPlayerUuid() {
			return playerUuid;
		}

		public String getLinkToken() {
			return linkToken;
		}

		public long getCreatedAt() {
			return createdAt;
		}
	}

	public static final class Stats {
		private final int sessions;
		private final int totalScore;
		private final int bestScore;
		private final int answers;
		private final int correct;
		private final double accuracy;
		private final int bestStreak;

		public Stats(int sessions, int totalScore, int bestScore, int answers, int correct, double accuracy,
				int bestStreak) {
			this.sessions = sessions;
			this.totalScore = totalScore;
			this.bestScore = bestScore;
			this.answers = answers;
			this.correct = correct;
			this.accuracy = accuracy;
			this.bestStreak = bestStreak;
		}

		public int getSessions() {
			return sessions;
		}

		public int getTotalScore() {
			return totalScore;
		}

		public int getBestScore() {
			return bestScore;
		}

		public int getAnswers() {
			return answers;
		}

		public int getCorrect() {
			return correct;
		}

		public double getAccuracy() {
			return accuracy;
		}

		public int getBestStreak() {
			return bestStreak;
		}
	}

	public static final class ScoreEntry {
		private final String sessionId;
		private final String quizId;
		@Nullable
		private final String quizTitle;
		private final int score;
		private final int correct;
		private final int answers;
		private final long playedAt;

		public ScoreEntry(String sessionId, String quizId, @Nullable String quizTitle, int score, int correct,
				int answers, long playedAt) {
			this.sessionId = sessionId;
			this.quizId = quizId;
			this.quizTitle = quizTitle;
			this.score = score;
			this.correct = correct;
			this.answers = answers;
			this.playedAt = playedAt;
		}

		public String getSessionId() {
			return sessionId;
		}

		public String getQuizId() {
			return quizId;
		}

		@Nullable
		public String getQuizTitle() {
			return quizTitle;
		}

		public int getScore() {
			return score;
		}

		public int getCorrect() {
			return correct;
		}

		public int getAnswers() {
			return answers;
		}

		public long getPlayedAt() {
			return playedAt;
		}
	}

	public static final class Achievement {
		private final String id;
		private final String title;
		private final String description;
		private final boolean unlocked;

		public Achievement(String id, String title, String description, boolean unlocked) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.unlocked = unlocked;
		}

		public String getId() {
			return id;
		}

		public String getTitle() {
			return title;
		}

		public String getDescription() {
			return description;
		}

		public boolean isUnlocked() {
			return unlocked;
		}
	}

	public static final class PortfolioEntry {
		private final String quizId;
		@Nullable
		private final String quizTitle;
		private final int sessions;
		private final int bestScore;
		private final long lastPlayedAt;

		public PortfolioEntry(String quizId, @Nullable String quizTitle, int sessions, int bestScore,
				long lastPlayedAt) {
			this.quizId = quizId;
			this.quizTitle = quizTitle;
			this.sessions = sessions;
			this.bestScore = bestScore;
			this.lastPlayedAt = lastPlayedAt;
		}

		public PortfolioEntry with(int score, long playedAt) {
			return new PortfolioEntry(quizId, quizTitle, sessions + 1, Math.max(bestScore, score),
					Math.max(lastPlayedAt, playedAt));
		}

		public String getQuizId() {
			return quizId;
		}

		@Nullable
		public String getQuizTitle() {
			return quizTitle;
		}

		public int getSessions() {
			return sessions;
		}

		public int getBestScore() {
			return bestScore;
		}

		public long getLastPlayedAt() {
			return lastPlayedAt;
		}
	}
}
