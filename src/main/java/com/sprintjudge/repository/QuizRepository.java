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
		String id = quiz.getId() != null ? quiz.getId() : Ids.uuid();
		long now = Instant.now().getEpochSecond();
		dsl.insertInto(Tables.QUIZZES)
				.columns(Tables.QUIZZES_ID, Tables.QUIZZES_TITLE, Tables.QUIZZES_DESC, Tables.QUIZZES_CREATED_BY,
						Tables.QUIZZES_CREATED_AT, Tables.QUIZZES_TEMPLATE)
				.values(id, quiz.getTitle(), quiz.getDescription(), quiz.getCreatedBy(), now, quiz.isTemplate())
				.execute();
		return new Quiz(id, quiz.getTitle(), quiz.getDescription(), quiz.getCreatedBy(), Instant.ofEpochSecond(now),
				quiz.isTemplate());
	}

	public Optional<Quiz> findById(String id) {
		return dsl.selectFrom(Tables.QUIZZES).where(Tables.QUIZZES_ID.eq(id))
				.fetchOptional(new org.jooq.RecordMapper<org.jooq.Record, Quiz>() {
					@Override
					public Quiz map(org.jooq.Record r) {
						return toQuiz(r);
					}
				});
	}

	public List<Quiz> findAll() {
		return dsl.selectFrom(Tables.QUIZZES).orderBy(Tables.QUIZZES_CREATED_AT.desc())
				.fetch(new org.jooq.RecordMapper<org.jooq.Record, Quiz>() {
					@Override
					public Quiz map(org.jooq.Record r) {
						return toQuiz(r);
					}
				});
	}

	public int count() {
		Integer n = dsl.selectCount().from(Tables.QUIZZES).fetchOne(0, Integer.class);
		return n == null ? 0 : n;
	}

	public Quiz update(Quiz quiz) {
		dsl.update(Tables.QUIZZES).set(Tables.QUIZZES_TITLE, quiz.getTitle())
				.set(Tables.QUIZZES_DESC, quiz.getDescription()).where(Tables.QUIZZES_ID.eq(quiz.getId())).execute();
		return quiz;
	}

	/**
	 * Deletes the quiz and everything scoped to it: sessions, their submissions,
	 * and questions. The questions FK cascades in production, but explicit deletes
	 * keep behavior identical where FKs are off and prevent 500s/orphans where the
	 * sessions FK has no cascade.
	 */
	public void delete(String id) {
		dsl.deleteFrom(Tables.SUBMISSIONS)
				.where(Tables.SUB_SESS
						.in(dsl.select(Tables.SESS_ID).from(Tables.GAME_SESSIONS).where(Tables.SESS_QUIZ_ID.eq(id))))
				.execute();
		dsl.deleteFrom(Tables.GAME_SESSIONS).where(Tables.SESS_QUIZ_ID.eq(id)).execute();
		dsl.deleteFrom(Tables.QUESTIONS).where(Tables.QUESTIONS_QUIZ_ID.eq(id)).execute();
		dsl.deleteFrom(Tables.QUIZZES).where(Tables.QUIZZES_ID.eq(id)).execute();
	}

	private Quiz toQuiz(org.jooq.Record r) {
		Boolean tpl = r.get(Tables.QUIZZES_TEMPLATE);
		return new Quiz(r.get(Tables.QUIZZES_ID), r.get(Tables.QUIZZES_TITLE), r.get(Tables.QUIZZES_DESC),
				r.get(Tables.QUIZZES_CREATED_BY),
				Instant.ofEpochSecond(RepoUtil.asLong(r.get(Tables.QUIZZES_CREATED_AT))), tpl != null && tpl);
	}
}
