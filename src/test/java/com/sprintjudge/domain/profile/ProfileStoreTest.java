package com.sprintjudge.domain.profile;

import com.sprintjudge.PlatformDb;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class ProfileStoreTest {

	@Test
	void createFindRenameLinkAndLatestUuid() throws Exception {
		PlatformDb.Db db = PlatformDb.open();
		ProfileStore store = new ProfileStore(db.dsl, db.dataSource);

		PlayerProfile created = store.create("Alice");
		assertNotNull(created.getId());
		assertEquals("Alice", created.getNickname());
		created.getLinkToken().matches("[0-9a-f]{64}");
		assertTrue(created.getCreatedAt() > 0);
		assertNull(created.getPlayerUuid());

		assertTrue(store.findById(created.getId()).isPresent());
		PlayerProfile found = store.findById(created.getId()).orElseThrow();
		assertEquals("Alice", found.getNickname());
		assertEquals(created.getLinkToken(), found.getLinkToken());
		assertNull(found.getPlayerUuid());

		assertTrue(store.findById("missing").isEmpty());
		assertNull(store.latestUuid("missing"));
		assertFalse(store.rename("missing", "Nobody"));
		assertTrue(store.rename(created.getId(), "Bob"));
		assertEquals("Bob", store.findById(created.getId()).orElseThrow().getNickname());

		store.link(created.getId(), "uuid-1");
		assertEquals("uuid-1", store.latestUuid(created.getId()));
		assertEquals("uuid-1", store.findById(created.getId()).orElseThrow().getPlayerUuid());
		store.link(created.getId(), "uuid-2");
		assertEquals("uuid-2", store.latestUuid(created.getId()));
	}

	@Test
	void validRejoinChecksTokenAndExpiry() throws Exception {
		PlatformDb.Db db = PlatformDb.open();
		ProfileStore store = new ProfileStore(db.dsl, db.dataSource);
		long now = System.currentTimeMillis();
		db.dsl.execute("INSERT INTO player_sessions (player_id, room_id, rejoin_token, expires_at) "
				+ "VALUES ('p1','r1','live',?), ('p2','r1','expired',?)", now + 60000, now - 1000);

		assertFalse(store.validRejoin("p1", null));
		assertFalse(store.validRejoin("p1", "  "));
		assertTrue(store.validRejoin("p1", "live"));
		assertFalse(store.validRejoin("p1", "wrong"));
		assertFalse(store.validRejoin("p2", "expired"));
		assertFalse(store.validRejoin("unknown", "live"));
	}

	@Test
	void historyAndCorrectnessJoinExistingSubmissions() throws Exception {
		PlatformDb.Db db = PlatformDb.open();
		ProfileStore store = new ProfileStore(db.dsl, db.dataSource);
		PlayerProfile profile = store.create("Stats");
		store.link(profile.getId(), "p1");
		db.dsl.execute("INSERT INTO quizzes (id, title) VALUES ('q1','Quiz One')");
		db.dsl.execute(
				"INSERT INTO game_sessions (id, quiz_id, pin_code) VALUES ('s1','q1','111'), ('s2','gone','222')");
		db.dsl.execute("INSERT INTO submissions (id, game_session_id, question_id, player_uuid, score_earned, "
				+ "is_correct, submitted_at) VALUES "
				+ "('sub1','s1','quest','p1',10,1,100), ('sub2','s1','quest','p1',5,0,200), "
				+ "('sub3','s2','quest','p1',0,NULL,300)");

		List<ProfileReport.ScoreEntry> history = store.history(profile.getId());
		assertEquals(2, history.size());
		ProfileReport.ScoreEntry latest = history.get(0);
		assertEquals("s2", latest.getSessionId());
		assertEquals("gone", latest.getQuizId());
		assertNull(latest.getQuizTitle());
		assertEquals(300L, latest.getPlayedAt());
		ProfileReport.ScoreEntry older = history.get(1);
		assertEquals("s1", older.getSessionId());
		assertEquals("Quiz One", older.getQuizTitle());
		assertEquals(15, older.getScore());
		assertEquals(1, older.getCorrect());
		assertEquals(2, older.getAnswers());

		List<Boolean> flags = store.correctness(profile.getId());
		assertEquals(3, flags.size());
		assertEquals(List.of(false, false, true), flags);
		assertTrue(store.history("missing").isEmpty());
		assertTrue(store.correctness("missing").isEmpty());
	}

	@Test
	void newTokenCoversBothHexDigitWidths() throws Exception {
		PlatformDb.Db db = PlatformDb.open();
		ProfileStore store = new ProfileStore(db.dsl, db.dataSource);
		for (int i = 0; i < 120; i++) {
			assertTrue(store.create("P" + i).getLinkToken().matches("[0-9a-f]{64}"));
		}
	}

	@Test
	void schemaFailureIsWrapped() throws Exception {
		PlatformDb.Db db = PlatformDb.open();
		DataSource broken = mock(DataSource.class);
		when(broken.getConnection()).thenThrow(new SQLException("boom"));
		assertThrows(IllegalStateException.class, () -> new ProfileStore(db.dsl, broken));
	}
}
