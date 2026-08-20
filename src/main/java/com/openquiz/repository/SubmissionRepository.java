package com.openquiz.repository;

import com.openquiz.domain.models.Submission;
import com.openquiz.util.Ids;
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
        boolean exists = s.id() != null && dsl.fetchExists(Tables.SUBMISSIONS, Tables.SUB_ID.eq(s.id()));
        if (exists) {
            dsl.update(Tables.SUBMISSIONS)
                .set(Tables.SUB_DATA, s.responseData())
                .set(Tables.SUB_SCORE, s.scoreEarned())
                .set(Tables.SUB_CORRECT, s.correct())
                .set(Tables.SUB_LOG, s.judgeLog())
                .set(Tables.SUB_ATTEMPTS, s.attemptCount())
                .where(Tables.SUB_ID.eq(id)).execute();
            return s;
        }
        dsl.insertInto(Tables.SUBMISSIONS)
            .columns(Tables.SUB_ID, Tables.SUB_SESS, Tables.SUB_QUESTION, Tables.SUB_PNAME,
                    Tables.SUB_PUUID, Tables.SUB_DATA, Tables.SUB_SCORE, Tables.SUB_CORRECT,
                    Tables.SUB_LOG, Tables.SUB_ATTEMPTS, Tables.SUB_AT)
            .values(id, s.gameSessionId(), s.questionId(), s.playerName(), s.playerUuid(),
                    s.responseData(), s.scoreEarned(), s.correct(), s.judgeLog(),
                    s.attemptCount(), now)
            .execute();
        return new Submission(id, s.gameSessionId(), s.questionId(), s.playerName(), s.playerUuid(),
                s.responseData(), s.scoreEarned(), s.correct(), s.judgeLog(), s.attemptCount(), Instant.ofEpochSecond(now));
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

    private Submission toSubmission(org.jooq.Record r) {
        Boolean correct = r.get(Tables.SUB_CORRECT);
        Long at = r.get(Tables.SUB_AT);
        return new Submission(
                r.get(Tables.SUB_ID), r.get(Tables.SUB_SESS), r.get(Tables.SUB_QUESTION),
                r.get(Tables.SUB_PNAME), r.get(Tables.SUB_PUUID), r.get(Tables.SUB_DATA),
                r.get(Tables.SUB_SCORE), correct != null && correct, r.get(Tables.SUB_LOG),
                r.get(Tables.SUB_ATTEMPTS), at == null ? null : Instant.ofEpochSecond(at));
    }
}
