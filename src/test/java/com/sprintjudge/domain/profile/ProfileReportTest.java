package com.sprintjudge.domain.profile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class ProfileReportTest {

	private static ProfileReport.ScoreEntry entry(String quiz, int score, int correct, int answers, long played) {
		return new ProfileReport.ScoreEntry("session-" + quiz, quiz, "Title " + quiz, score, correct, answers, played);
	}

	@Test
	void statsAggregatesHistoryAndStreak() {
		ProfileReport.Stats stats = ProfileReport.stats(
				List.of(entry("q1", 100, 4, 5, 10L), entry("q1", 250, 5, 5, 20L)),
				List.of(true, true, false, true, true, true));
		assertEquals(2, stats.getSessions());
		assertEquals(350, stats.getTotalScore());
		assertEquals(250, stats.getBestScore());
		assertEquals(10, stats.getAnswers());
		assertEquals(9, stats.getCorrect());
		assertEquals(0.9, stats.getAccuracy(), 1e-9);
		assertEquals(3, stats.getBestStreak());

		ProfileReport.Stats empty = ProfileReport.stats(List.of(), List.of());
		assertEquals(0, empty.getSessions());
		assertEquals(0.0, empty.getAccuracy(), 1e-9);
		assertEquals(0, empty.getBestStreak());
	}

	@Test
	void achievementsCoverUnlockedAndLocked() {
		List<ProfileReport.Achievement> all = ProfileReport
				.achievements(new ProfileReport.Stats(5, 1000, 200, 10, 9, 0.9, 5));
		assertEquals(5, all.size());
		for (ProfileReport.Achievement achievement : all) {
			assertTrue(achievement.isUnlocked());
			assertNotNull(achievement.getId());
			assertNotNull(achievement.getTitle());
			assertNotNull(achievement.getDescription());
		}
		List<ProfileReport.Achievement> none = ProfileReport
				.achievements(new ProfileReport.Stats(0, 0, 0, 9, 7, 0.7, 4));
		for (ProfileReport.Achievement achievement : none) {
			assertFalse(achievement.isUnlocked());
		}
		List<ProfileReport.Achievement> manyWrong = ProfileReport
				.achievements(new ProfileReport.Stats(5, 1000, 200, 10, 7, 0.7, 5));
		assertFalse(manyWrong.get(2).isUnlocked());
		assertTrue(manyWrong.get(0).isUnlocked());
		List<ProfileReport.Achievement> fewRight = ProfileReport
				.achievements(new ProfileReport.Stats(5, 1000, 200, 9, 9, 1.0, 5));
		assertFalse(fewRight.get(2).isUnlocked());
	}

	@Test
	void portfolioMergesRepeatedQuizzes() {
		List<ProfileReport.PortfolioEntry> entries = ProfileReport.portfolio(
				List.of(entry("q1", 100, 4, 5, 10L), entry("q1", 300, 5, 5, 20L), entry("q2", 50, 1, 2, 5L)));
		assertEquals(2, entries.size());
		ProfileReport.PortfolioEntry first = entries.get(0);
		assertEquals("q1", first.getQuizId());
		assertEquals("Title q1", first.getQuizTitle());
		assertEquals(2, first.getSessions());
		assertEquals(300, first.getBestScore());
		assertEquals(20L, first.getLastPlayedAt());
		assertEquals("q2", entries.get(1).getQuizId());
		assertTrue(ProfileReport.portfolio(List.of()).isEmpty());
	}

	@Test
	void reportExposesProfileAndCollections() {
		PlayerProfile player = new PlayerProfile("p1", "Alice", "uuid", "tok", 42L);
		ProfileReport.Profile profile = new ProfileReport.Profile(player);
		ProfileReport report = new ProfileReport(profile, ProfileReport.stats(List.of(), List.of()), List.of(),
				List.of(), List.of());
		assertEquals("p1", report.getProfile().getId());
		assertEquals("Alice", report.getProfile().getNickname());
		assertEquals("uuid", report.getProfile().getPlayerUuid());
		assertEquals("tok", report.getProfile().getLinkToken());
		assertEquals(42L, report.getProfile().getCreatedAt());
		assertTrue(report.getHistory().isEmpty());
		assertTrue(report.getAchievements().isEmpty());
		assertTrue(report.getPortfolio().isEmpty());
		assertTrue(report.getStats().getSessions() == 0);
		assertEquals(player, player);
		assertEquals(player.hashCode(), new PlayerProfile("p1", "Alice", "uuid", "tok", 42L).hashCode());
		assertFalse(player.equals(null));
		assertFalse(player.equals(new PlayerProfile("other", "Alice", "uuid", "tok", 42L)));
		assertFalse(player.equals(new PlayerProfile("p1", "Bob", "uuid", "tok", 42L)));
		assertFalse(player.equals(new PlayerProfile("p1", "Alice", "uuid", "other", 42L)));
		assertFalse(player.equals(new PlayerProfile("p1", "Alice", "other", "tok", 42L)));
		assertFalse(player.equals(new PlayerProfile("p1", "Alice", "uuid", "tok", 43L)));
		assertFalse(player.equals(new Object()));
		assertTrue(player.toString().contains("Alice"));
	}
}
