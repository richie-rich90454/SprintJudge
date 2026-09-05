package com.sprintjudge.repository;

import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.util.RepoUtil;
import com.sprintjudge.util.Ids;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class GameSessionRepository {

    private final DSLContext dsl;

    public GameSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public GameSession create(String quizId, String hostUserId, String pin, String settingsOverride) {
        return create(quizId, hostUserId, pin, settingsOverride, "STANDARD");
    }

    public GameSession create(String quizId, String hostUserId, String pin, String settingsOverride, String gameMode) {
        String id = Ids.uuid();
        long now = Instant.now().getEpochSecond();
        String mode = gameMode == null || gameMode.isBlank() ? "STANDARD" : gameMode;
        dsl.insertInto(Tables.GAME_SESSIONS)
            .columns(Tables.SESS_ID, Tables.SESS_QUIZ_ID, Tables.SESS_PIN, Tables.SESS_HOST,
                    Tables.SESS_STATUS, Tables.SESS_MODE, Tables.SESS_INDEX, Tables.SESS_CREATED, Tables.SESS_OVERRIDE)
            .values(id, quizId, pin, hostUserId, "LOBBY", mode, 0, now, settingsOverride)
            .execute();
        return new GameSession(id, quizId, pin, hostUserId, "LOBBY", mode, 0, null, null, settingsOverride, Instant.ofEpochSecond(now));
    }

    public Optional<GameSession> findByPin(String pin) {
        return dsl.selectFrom(Tables.GAME_SESSIONS).where(Tables.SESS_PIN.eq(pin))
                .fetchOptional(this::toSession);
    }

    public Optional<GameSession> findById(String id) {
        return dsl.selectFrom(Tables.GAME_SESSIONS).where(Tables.SESS_ID.eq(id))
                .fetchOptional(this::toSession);
    }

    public void updateStatus(String id, String status) {
        var update = dsl.update(Tables.GAME_SESSIONS).set(Tables.SESS_STATUS, status);
        if ("ACTIVE".equals(status)) {
            update.set(Tables.SESS_STARTED, Instant.now().getEpochSecond());
        } else if ("ENDED".equals(status)) {
            update.set(Tables.SESS_ENDED, Instant.now().getEpochSecond());
        }
        update.where(Tables.SESS_ID.eq(id)).execute();
    }

    public void setCurrentIndex(String id, int index) {
        dsl.update(Tables.GAME_SESSIONS).set(Tables.SESS_INDEX, index)
            .where(Tables.SESS_ID.eq(id)).execute();
    }

    public void setOverride(String id, String override) {
        dsl.update(Tables.GAME_SESSIONS).set(Tables.SESS_OVERRIDE, override)
            .where(Tables.SESS_ID.eq(id)).execute();
    }

    /** PIN reuse: ENDED rows never come back, so their PINs return to the pool. */
    public int deleteByPin(String pin) {
        return dsl.deleteFrom(Tables.GAME_SESSIONS).where(Tables.SESS_PIN.eq(pin)).execute();
    }

    private GameSession toSession(org.jooq.Record r) {
        Long start = RepoUtil.asLongBoxed(r.get(Tables.SESS_STARTED));
        Long end = RepoUtil.asLongBoxed(r.get(Tables.SESS_ENDED));
        Long created = RepoUtil.asLongBoxed(r.get(Tables.SESS_CREATED));
        String mode = r.get(Tables.SESS_MODE);
        return new GameSession(
                r.get(Tables.SESS_ID), r.get(Tables.SESS_QUIZ_ID), r.get(Tables.SESS_PIN),
                r.get(Tables.SESS_HOST), r.get(Tables.SESS_STATUS),
                mode == null || mode.isBlank() ? "STANDARD" : mode, r.get(Tables.SESS_INDEX),
                start == null ? null : Instant.ofEpochSecond(start),
                end == null ? null : Instant.ofEpochSecond(end),
                r.get(Tables.SESS_OVERRIDE),
                created == null ? null : Instant.ofEpochSecond(created));
    }
}
