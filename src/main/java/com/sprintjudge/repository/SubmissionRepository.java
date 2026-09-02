package com.sprintjudge.repository;

import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.RepoUtil;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SubmissionRepository {

    private final DSLContext dsl;

    public SubmissionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Submission save(Submission s) {
        String id = s.id() != null ? s.id() : Ids.uuid();
        long now = Instant.now().getEpochSecond();
        // ponytail: atomic upsert replaces the TOCTOU check-then-insert.
        dsl.insertInto(Tables.SUBMISSIONS)
            .columns(Tables.SUB_ID, Tables.SUB_SESS, Tables.SUB_QUESTION, Tables.SUB_PNAME,
                    Tables.SUB_PUUID, Tables.SUB_DATA, Tables.SUB_SCORE, Tables.SUB_CORRECT,
                    Tables.SUB_LOG, Tables.SUB_ATTEMPTS, Tables.SUB_AT)
            .values(id, s.gameSessionId(), s.questionId(), s.playerName(), s.playerUuid(),
                    s.responseData(), s.scoreEarned(), s.correct(), s.judgeLog(),
                    s.attemptCount(), now)
            .onConflict(Tables.SUB_ID).doUpdate()
            .set(Tables.SUB_DATA, s.responseData())
            .set(Tables.SUB_SCORE, s.scoreEarned())
            .set(Tables.SUB_CORRECT, s.correct())
            .set(Tables.SUB_LOG, s.judgeLog())
            .set(Tables.SUB_ATTEMPTS, s.attemptCount())
            .execute();
        return new Submission(id, s.gameSessionId(), s.questionId(), s.playerName(),
                s.playerUuid(), s.responseData(), s.scoreEarned(), s.correct(),
                s.judgeLog(), s.attemptCount(), s.submittedAt());
    }

    public Optional<Submission> findBest(String sessionId, String questionId, String playerUuid) {
        return dsl.selectFrom(Tables.SUBMISSIONS)
                .where(Tables.SUB_SESS.eq(sessionId), Tables.SUB_QUESTION.eq(questionId),
                        Tables.SUB_PUUID.eq(playerUuid))
                .orderBy(Tables.SUB_SCORE.desc())
                .limit(1)
                .fetchOptional(this::toSubmission);
    }

    public List<Submission> findBySessionQuestion(String sessionId, String questionId) {
        return dsl.selectFrom(Tables.SUBMISSIONS)
                .where(Tables.SUB_SESS.eq(sessionId), Tables.SUB_QUESTION.eq(questionId))
                .fetch(this::toSubmission);
    }

    public List<Submission> findBySession(String sessionId) {
        return dsl.selectFrom(Tables.SUBMISSIONS)
                .where(Tables.SUB_SESS.eq(sessionId))
                .fetch(this::toSubmission);
    }

    public Optional<Submission> findHighestByPlayer(String sessionId, String playerUuid) {
        return dsl.selectFrom(Tables.SUBMISSIONS)
                .where(Tables.SUB_SESS.eq(sessionId), Tables.SUB_PUUID.eq(playerUuid))
                .orderBy(Tables.SUB_SCORE.desc()).limit(1)
                .fetchOptional(this::toSubmission);
    }

    /** Batched insert for the write-coalescing buffer: one round trip per flush. */
    public void saveAll(java.util.List<Submission> batch) {
        if (batch.isEmpty()) return;
        var stmt = dsl.query(
                "INSERT INTO submissions (id, game_session_id, question_id, player_name, player_uuid, "
              + "response_data, score_earned, is_correct, judge_log, attempt_count, submitted_at) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        var step = dsl.batch(stmt);
        for (Submission s : batch) {
            String id = s.id() != null ? s.id() : Ids.uuid();
            long now = Instant.now().getEpochSecond();
            step.bind(id, s.gameSessionId(), s.questionId(), s.playerName(), s.playerUuid(),
                    s.responseData(), s.scoreEarned(), s.correct(), s.judgeLog(),
                    s.attemptCount(), now);
        }
        step.execute();
    }
    private Submission toSubmission(org.jooq.Record r) {
        Boolean correct = r.get(Tables.SUB_CORRECT);
        Long at = RepoUtil.asLongBoxed(r.get(Tables.SUB_AT));
        return new Submission(
                r.get(Tables.SUB_ID), r.get(Tables.SUB_SESS), r.get(Tables.SUB_QUESTION),
                r.get(Tables.SUB_PNAME), r.get(Tables.SUB_PUUID), r.get(Tables.SUB_DATA),
                r.get(Tables.SUB_SCORE), correct != null && correct, r.get(Tables.SUB_LOG),
                r.get(Tables.SUB_ATTEMPTS), at == null ? null : Instant.ofEpochSecond(at));
    }
}
