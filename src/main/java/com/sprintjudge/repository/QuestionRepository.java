package com.sprintjudge.repository;

import com.sprintjudge.domain.models.Question;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.RepoUtil;
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
		String id = q.getId() != null ? q.getId() : Ids.uuid();
		long now = Instant.now().getEpochSecond();
		String langs = q.getLanguagesAllowed() == null ? null : String.join(",", q.getLanguagesAllowed());
		// ponytail: atomic upsert replaces the TOCTOU check-then-insert.
		dsl.insertInto(Tables.QUESTIONS)
				.columns(Tables.QUESTIONS_ID, Tables.QUESTIONS_QUIZ_ID, Tables.QUESTIONS_TITLE, Tables.QUESTIONS_DESC,
						Tables.QUESTIONS_TYPE, Tables.QUESTIONS_LANGS, Tables.QUESTIONS_TIME, Tables.QUESTIONS_POINTS,
						Tables.QUESTIONS_CONFIG, Tables.QUESTIONS_ORDER, Tables.QUESTIONS_CREATED_AT)
				.values(id, q.getQuizId(), q.getTitle(), q.getDescription(), q.getQuestionType(), langs,
						q.getTimeLimitSec(), q.getPointsBase(), q.getConfig(), q.getOrderIndex(), now)
				.onConflict(Tables.QUESTIONS_ID).doUpdate().set(Tables.QUESTIONS_TITLE, q.getTitle())
				.set(Tables.QUESTIONS_DESC, q.getDescription()).set(Tables.QUESTIONS_TYPE, q.getQuestionType())
				.set(Tables.QUESTIONS_LANGS, langs).set(Tables.QUESTIONS_TIME, q.getTimeLimitSec())
				.set(Tables.QUESTIONS_POINTS, q.getPointsBase()).set(Tables.QUESTIONS_CONFIG, q.getConfig())
				.set(Tables.QUESTIONS_ORDER, q.getOrderIndex()).execute();
		return new Question(id, q.getQuizId(), q.getTitle(), q.getDescription(), q.getQuestionType(),
				q.getLanguagesAllowed(), q.getTimeLimitSec(), q.getPointsBase(), q.getConfig(), q.getOrderIndex(),
				q.getCreatedAt());
	}

	public List<Question> findByQuiz(String quizId) {
		return dsl.selectFrom(Tables.QUESTIONS).where(Tables.QUESTIONS_QUIZ_ID.eq(quizId))
				.orderBy(Tables.QUESTIONS_ORDER.asc()).fetch(new org.jooq.RecordMapper<org.jooq.Record, Question>() {
					@Override
					public Question map(org.jooq.Record r) {
						return toQuestion(r);
					}
				});
	}

	public Optional<Question> findById(String id) {
		return dsl.selectFrom(Tables.QUESTIONS).where(Tables.QUESTIONS_ID.eq(id))
				.fetchOptional(new org.jooq.RecordMapper<org.jooq.Record, Question>() {
					@Override
					public Question map(org.jooq.Record r) {
						return toQuestion(r);
					}
				});
	}

	public void delete(String id) {
		dsl.deleteFrom(Tables.QUESTIONS).where(Tables.QUESTIONS_ID.eq(id)).execute();
	}

	public void deleteByQuiz(String quizId) {
		dsl.deleteFrom(Tables.QUESTIONS).where(Tables.QUESTIONS_QUIZ_ID.eq(quizId)).execute();
	}

	private Question toQuestion(org.jooq.Record r) {
		String langs = r.get(Tables.QUESTIONS_LANGS);
		List<String> langList = langs == null || langs.isBlank() ? null : List.of(langs.split(","));
		return new Question(r.get(Tables.QUESTIONS_ID), r.get(Tables.QUESTIONS_QUIZ_ID), r.get(Tables.QUESTIONS_TITLE),
				r.get(Tables.QUESTIONS_DESC), r.get(Tables.QUESTIONS_TYPE), langList, r.get(Tables.QUESTIONS_TIME),
				r.get(Tables.QUESTIONS_POINTS), r.get(Tables.QUESTIONS_CONFIG), r.get(Tables.QUESTIONS_ORDER),
				Instant.ofEpochSecond(RepoUtil.asLong(r.get(Tables.QUESTIONS_CREATED_AT))));
	}
}
