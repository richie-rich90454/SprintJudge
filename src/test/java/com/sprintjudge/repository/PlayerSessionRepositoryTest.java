package com.sprintjudge.repository;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class PlayerSessionRepositoryTest {

	@Test
	void savesAndLoadsTokensAndExpiresOldOnes() throws Exception {
		DSLContext dsl = com.sprintjudge.TestDb.inMemory();
		PlayerSessionRepository repo = new PlayerSessionRepository(dsl);
		repo.save("p1", "room-1", "token-1");
		repo.save("p2", "room-1", "token-2");
		repo.save("p3", "room-2", "token-3");
		List<PlayerSessionRepository.StoredPlayerSession> rows = repo.findByRoom("room-1");
		assertEquals(2, rows.size());
		assertEquals("token-1",
				rows.stream().filter(r -> "p1".equals(r.getPlayerId())).findFirst().orElseThrow().getRejoinToken());
		assertTrue(rows.get(0).getExpiresAt() > System.currentTimeMillis());
		assertEquals("p1",
				rows.stream().filter(r -> "p1".equals(r.getPlayerId())).findFirst().orElseThrow().getPlayerId());

		// Rotation replaces the stored token.
		repo.save("p1", "room-1", "token-1b");
		assertEquals("token-1b", repo.findByRoom("room-1").stream().filter(r -> "p1".equals(r.getPlayerId()))
				.findFirst().orElseThrow().getRejoinToken());

		dsl.execute("UPDATE player_sessions SET expires_at = 1 WHERE player_id = 'p2'");
		assertEquals(1, repo.findByRoom("room-1").size());
		repo.deleteExpired();
		assertEquals(1, repo.findByRoom("room-1").size());
	}

	@Test
	void nullArgumentsAreIgnored() {
		PlayerSessionRepository repo = new PlayerSessionRepository(null);
		repo.save(null, "room", "token");
		repo.save("p", null, "token");
		repo.save("p", "room", null);
		PlayerSessionRepository.StoredPlayerSession row = new PlayerSessionRepository.StoredPlayerSession("p", "t",
				123L);
		assertEquals("p", row.getPlayerId());
		assertEquals("t", row.getRejoinToken());
		assertEquals(123L, row.getExpiresAt());
	}
}
