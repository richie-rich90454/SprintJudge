package com.sprintjudge.repository;

import java.util.List;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Persists rejoin tokens so a seat survives a server restart. */
@Repository
public class PlayerSessionRepository {

	private static final long TOKEN_TTL_MS = 24L * 60 * 60 * 1000;

	private final DSLContext dsl;

	public PlayerSessionRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public void save(String playerId, String roomId, String rejoinToken) {
		if (playerId == null || roomId == null || rejoinToken == null)
			return;
		long expires = System.currentTimeMillis() + TOKEN_TTL_MS;
		dsl.insertInto(Tables.PLAYER_SESSIONS, Tables.PS_PLAYER_ID, Tables.PS_ROOM_ID, Tables.PS_REJOIN_TOKEN,
				Tables.PS_EXPIRES_AT).values(playerId, roomId, rejoinToken, expires).onDuplicateKeyUpdate()
				.set(Tables.PS_REJOIN_TOKEN, rejoinToken).set(Tables.PS_EXPIRES_AT, expires).execute();
	}

	public List<StoredPlayerSession> findByRoom(String roomId) {
		return dsl.select(Tables.PS_PLAYER_ID, Tables.PS_REJOIN_TOKEN, Tables.PS_EXPIRES_AT)
				.from(Tables.PLAYER_SESSIONS)
				.where(Tables.PS_ROOM_ID.eq(roomId), Tables.PS_EXPIRES_AT.gt(System.currentTimeMillis()))
				.fetch(record -> new StoredPlayerSession(record.get(Tables.PS_PLAYER_ID),
						record.get(Tables.PS_REJOIN_TOKEN), record.get(Tables.PS_EXPIRES_AT)));
	}

	public void deleteExpired() {
		dsl.deleteFrom(Tables.PLAYER_SESSIONS).where(Tables.PS_EXPIRES_AT.le(System.currentTimeMillis())).execute();
	}

	public static final class StoredPlayerSession {
		private final String playerId;
		private final String rejoinToken;
		private final long expiresAt;

		public StoredPlayerSession(String playerId, String rejoinToken, long expiresAt) {
			this.playerId = playerId;
			this.rejoinToken = rejoinToken;
			this.expiresAt = expiresAt;
		}

		public String getPlayerId() {
			return playerId;
		}

		public String getRejoinToken() {
			return rejoinToken;
		}

		public long getExpiresAt() {
			return expiresAt;
		}
	}
}
