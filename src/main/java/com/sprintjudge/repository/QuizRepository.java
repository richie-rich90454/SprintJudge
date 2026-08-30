package com.sprintjudge.repository;

import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.RepoUtil;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class QuizRepository {

    private final DSLContext dsl;

    public QuizRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Quiz create(Quiz quiz) {
        String id = quiz.id() != null ? quiz.id() : Ids.uuid();
        long now = Instant.now().getEpochSecond();
        dsl.insertInto(Tables.QUIZZES)
            .columns(Tables.QUIZZES_ID, Tables.QUIZZES_TITLE, Tables.QUIZZES_DESC,
                    Tables.QUIZZES_CREATED_BY, Tables.QUIZZES_CREATED_AT, Tables.QUIZZES_TEMPLATE)
            .values(id, quiz.title(), quiz.description(), quiz.createdBy(), now, quiz.template())
            .execute();
        return new Quiz(id, quiz.title(), quiz.description(), quiz.createdBy(), Instant.ofEpochSecond(now), quiz.template());
    }

    public Optional<Quiz> findById(String id) {
        return dsl.selectFrom(Tables.QUIZZES).where(Tables.QUIZZES_ID.eq(id))
                .fetchOptional(r -> toQuiz(r));
    }

    public List<Quiz> findAll() {
        return dsl.selectFrom(Tables.QUIZZES).orderBy(Tables.QUIZZES_CREATED_AT.desc())
                .fetch(this::toQuiz);
    }

    public Quiz update(Quiz quiz) {
        dsl.update(Tables.QUIZZES)
            .set(Tables.QUIZZES_TITLE, quiz.title())
            .set(Tables.QUIZZES_DESC, quiz.description())
            .where(Tables.QUIZZES_ID.eq(quiz.id()))
            .execute();
        return quiz;
    }

    public void delete(String id) {
        dsl.deleteFrom(Tables.QUIZZES).where(Tables.QUIZZES_ID.eq(id)).execute();
    }

    private Quiz toQuiz(org.jooq.Record r) {
        Boolean tpl = r.get(Tables.QUIZZES_TEMPLATE);
        return new Quiz(
                r.get(Tables.QUIZZES_ID), r.get(Tables.QUIZZES_TITLE),
                r.get(Tables.QUIZZES_DESC), r.get(Tables.QUIZZES_CREATED_BY),
                Instant.ofEpochSecond(RepoUtil.asLong(r.get(Tables.QUIZZES_CREATED_AT))),
                tpl != null && tpl);
    }
}
