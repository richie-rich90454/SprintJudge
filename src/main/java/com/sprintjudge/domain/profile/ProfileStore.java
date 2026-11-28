package com.sprintjudge.domain.profile;

import org.eclipse.jdt.annotation.Nullable;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Repository;
import com.sprintjudge.domain.profile.ProfileReport.ScoreEntry;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.RepoUtil;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for optional profiles. History is derived from the submissions a
 * player already produced - nothing in the game loop had to change.
 */
@Repository
public class ProfileStore {

	private static final Field<String> F_ID = DSL.field("id", SQLDataType.VARCHAR);
	private static final Field<String> F_NICKNAME = DSL.field("nickname", SQLDataType.VARCHAR);
	private static final Field<String> F_TOKEN = DSL.field("link_token", SQLDataType.VARCHAR);
	private static final Field<String> F_UUID = DSL.field("player_uuid", SQLDataType.VARCHAR);
	private static final Field<Long> F_CREATED = DSL.field("created_at", SQLDataType.BIGINT);

	private static final Field<String> F_SESSION = DSL.field("session_id", SQLDataType.VARCHAR);
	private static final Field<String> F_QUIZ = DSL.field("quiz_id", SQLDataType.VARCHAR);
	private static final Field<String> F_TITLE = DSL.field("quiz_title", SQLDataType.VARCHAR);
	private static final Field<Long> F_SCORE = DSL.field("score", SQLDataType.BIGINT);
	private static final Field<Long> F_CORRECT = DSL.field("correct", SQLDataType.BIGINT);
	private static final Field<Long> F_ANSWERS = DSL.field("answers", SQLDataType.BIGINT);
	private static final Field<Long> F_PLAYED = DSL.field("played_at", SQLDataType.BIGINT);
	private static final Field<Boolean> F_FLAG = DSL.field("is_correct", SQLDataType.BOOLEAN);

	/** Bounded on purpose: a profile view never loads an unbounded history. */
	private static final String HISTORY_SQL = "SELECT s.game_session_id AS session_id, g.quiz_id AS quiz_id, "
			+ "q.title AS quiz_title, SUM(s.score_earned) AS score, "
			+ "SUM(CASE WHEN s.is_correct = 1 THEN 1 ELSE 0 END) AS correct, "
			+ "COUNT(*) AS answers, MAX(s.submitted_at) AS played_at "
			+ "FROM submissions s JOIN profile_links pl ON pl.player_uuid = s.player_uuid "
			+ "JOIN game_sessions g ON g.id = s.game_session_id "
			+ "LEFT JOIN quizzes q ON q.id = g.quiz_id WHERE pl.profile_id = ? "
			+ "GROUP BY s.game_session_id ORDER BY played_at DESC LIMIT 500";

	private final DSLContext dsl;

	public ProfileStore(DSLContext dsl, DataSource dataSource) {
		this.dsl = dsl;
		// The migration script is idempotent, so applying it here keeps profile
		// storage available even on databases whose bootstrap list predates it.
		try (java.sql.Connection conn = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conn, new EncodedResource(
					new ClassPathResource("db/migration/V5__profiles.sql"), StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new IllegalStateException("Profile schema initialization failed", e);
		}
	}

	public PlayerProfile create(String nickname) {
		String id = Ids.uuid();
		String token = newToken();
		long now = System.currentTimeMillis();
		dsl.execute("INSERT INTO player_profiles (id, nickname, link_token, created_at) VALUES (?, ?, ?, ?)", id,
				nickname, token, now);
		return new PlayerProfile(id, nickname, null, token, now);
	}

	public Optional<PlayerProfile> findById(String id) {
		Result<Record> rows = dsl
				.fetch("SELECT id, nickname, link_token, created_at " + "FROM player_profiles WHERE id = ?", id);
		if (rows.isEmpty())
			return Optional.empty();
		Record r = rows.get(0);
		String nickname = r.get(F_NICKNAME);
		String token = r.get(F_TOKEN);
		long created = RepoUtil.asLong(r.get(F_CREATED));
		return Optional.of(new PlayerProfile(r.get(F_ID), nickname, latestUuid(id), token, created));
	}

	public boolean rename(String id, String nickname) {
		return dsl.execute("UPDATE player_profiles SET nickname = ? WHERE id = ?", nickname, id) > 0;
	}

	/**
	 * Adds a game identity to the profile; a duplicate uuid is rejected by the DB.
	 */
	public void link(String profileId, String playerUuid) {
		dsl.execute("INSERT INTO profile_links (profile_id, player_uuid, linked_at) VALUES (?, ?, ?)", profileId,
				playerUuid, System.currentTimeMillis());
	}

	@Nullable
	public String latestUuid(String profileId) {
		Result<Record> rows = dsl.fetch(
				"SELECT player_uuid FROM profile_links WHERE profile_id = ? " + "ORDER BY linked_at DESC LIMIT 1",
				profileId);
		return rows.isEmpty() ? null : rows.get(0).get(F_UUID);
	}

	/**
	 * The rejoin token is the proof that a game identity belongs to this player.
	 */
	public boolean validRejoin(String playerUuid, @Nullable String rejoinToken) {
		if (rejoinToken == null || rejoinToken.isBlank())
			return false;
		return !dsl
				.fetch("SELECT 1 FROM player_sessions WHERE player_id = ? AND rejoin_token = ? " + "AND expires_at > ?",
						playerUuid, rejoinToken, System.currentTimeMillis())
				.isEmpty();
	}

	public List<ScoreEntry> history(String profileId) {
		List<ScoreEntry> out = new ArrayList<>();
		for (Record r : dsl.fetch(HISTORY_SQL, profileId)) {
			out.add(new ScoreEntry(r.get(F_SESSION), r.get(F_QUIZ), r.get(F_TITLE),
					(int) RepoUtil.asLong(r.get(F_SCORE)), (int) RepoUtil.asLong(r.get(F_CORRECT)),
					(int) RepoUtil.asLong(r.get(F_ANSWERS)), RepoUtil.asLong(r.get(F_PLAYED))));
		}
		return out;
	}

	public List<Boolean> correctness(String profileId) {
		List<Boolean> out = new ArrayList<>();
		for (Record r : dsl.fetch("SELECT s.is_correct FROM submissions s "
				+ "JOIN profile_links pl ON pl.player_uuid = s.player_uuid WHERE pl.profile_id = ? "
				+ "ORDER BY s.submitted_at DESC, s.id DESC LIMIT 1000", profileId)) {
			out.add(Boolean.TRUE.equals(r.get(F_FLAG)));
		}
		return out;
	}

	private static String newToken() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		StringBuilder out = new StringBuilder(64);
		for (byte b : bytes) {
			String hex = Integer.toHexString(b & 0xff);
			if (hex.length() == 1)
				out.append('0');
			out.append(hex);
		}
		return out.toString();
	}
}
