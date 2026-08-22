package com.sprintjudge.repository;

import com.sprintjudge.domain.models.Question;
import com.sprintjudge.util.Ids;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class QuestionRepository {

    private final DSLContext dsl;

    public QuestionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Question save(Question q) {
        String id = q.id() != null ? q.id() : Ids.uuid();
        long now = Instant.now().getEpochSecond();
        String langs = q.languagesAllowed() == null ? null : String.join(",", q.languagesAllowed());
        boolean exists = dsl.fetchExists(Tables.QUESTIONS, Tables.QUESTIONS_ID.eq(id));
        if (exists) {
            dsl.update(Tables.QUESTIONS)
                .set(Tables.QUESTIONS_TITLE, q.title())
                .set(Tables.QUESTIONS_DESC, q.description())
                .set(Tables.QUESTIONS_TYPE, q.questionType())
                .set(Tables.QUESTIONS_LANGS, langs)
                .set(Tables.QUESTIONS_TIME, q.timeLimitSec())
                .set(Tables.QUESTIONS_POINTS, q.pointsBase())
                .set(Tables.QUESTIONS_CONFIG, q.config())
                .set(Tables.QUESTIONS_ORDER, q.orderIndex())
                .where(Tables.QUESTIONS_ID.eq(id))
                .execute();
            return q;
        }
        dsl.insertInto(Tables.QUESTIONS)
            .columns(Tables.QUESTIONS_ID, Tables.QUESTIONS_QUIZ_ID, Tables.QUESTIONS_TITLE,
                    Tables.QUESTIONS_DESC, Tables.QUESTIONS_TYPE, Tables.QUESTIONS_LANGS,
                    Tables.QUESTIONS_TIME, Tables.QUESTIONS_POINTS, Tables.QUESTIONS_CONFIG,
                    Tables.QUESTIONS_ORDER, Tables.QUESTIONS_CREATED_AT)
            .values(id, q.quizId(), q.title(), q.description(), q.questionType(), langs,
                    q.timeLimitSec(), q.pointsBase(), q.config(), q.orderIndex(), now)
            .execute();
        return new Question(id, q.quizId(), q.title(), q.description(), q.questionType(),
                q.languagesAllowed(), q.timeLimitSec(), q.pointsBase(), q.config(), q.orderIndex(), Instant.ofEpochSecond(now));
    }

    public List<Question> findByQuiz(String quizId) {
        return dsl.selectFrom(Tables.QUESTIONS)
                .where(Tables.QUESTIONS_QUIZ_ID.eq(quizId))
                .orderBy(Tables.QUESTIONS_ORDER.asc())
                .fetch(this::toQuestion);
    }

    public Optional<Question> findById(String id) {
        return dsl.selectFrom(Tables.QUESTIONS).where(Tables.QUESTIONS_ID.eq(id))
                .fetchOptional(this::toQuestion);
    }

    public void delete(String id) {
        dsl.deleteFrom(Tables.QUESTIONS).where(Tables.QUESTIONS_ID.eq(id)).execute();
    }

    private Question toQuestion(org.jooq.Record r) {
        String langs = r.get(Tables.QUESTIONS_LANGS);
        List<String> langList = langs == null || langs.isBlank() ? null : List.of(langs.split(","));
        return new Question(
                r.get(Tables.QUESTIONS_ID), r.get(Tables.QUESTIONS_QUIZ_ID),
                r.get(Tables.QUESTIONS_TITLE), r.get(Tables.QUESTIONS_DESC),
                r.get(Tables.QUESTIONS_TYPE), langList, r.get(Tables.QUESTIONS_TIME),
                r.get(Tables.QUESTIONS_POINTS), r.get(Tables.QUESTIONS_CONFIG),
                r.get(Tables.QUESTIONS_ORDER), Instant.ofEpochSecond(r.get(Tables.QUESTIONS_CREATED_AT)));
    }
}
