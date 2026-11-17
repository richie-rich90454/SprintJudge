package com.sprintjudge.service;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.AdminSettingsRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.util.Json;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("null")
class ImportExportServiceTest {

	private static final class Repos {
		private QuizRepository qr;
		private QuestionRepository qnr;
		private AdminSettingsRepository sr;

		Repos(QuizRepository qr, QuestionRepository qnr, AdminSettingsRepository sr) {
			this.qr = qr;
			this.qnr = qnr;
			this.sr = sr;
		}

		QuizRepository getQr() {
			return qr;
		}
		void setQr(QuizRepository qr) {
			this.qr = qr;
		}
		QuestionRepository getQnr() {
			return qnr;
		}
		void setQnr(QuestionRepository qnr) {
			this.qnr = qnr;
		}
		AdminSettingsRepository getSr() {
			return sr;
		}
		void setSr(AdminSettingsRepository sr) {
			this.sr = sr;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Repos))
				return false;
			Repos other = (Repos) o;
			return java.util.Objects.equals(this.qr, other.qr) && java.util.Objects.equals(this.qnr, other.qnr)
					&& java.util.Objects.equals(this.sr, other.sr);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(qr, qnr, sr);
		}

		@Override
		public String toString() {
			return "Repos{qr=" + qr + ", qnr=" + qnr + ", sr=" + sr + "}";
		}
	}

	private Repos repos() throws Exception {
		DSLContext dsl = TestDb.inMemory();
		return new Repos(new QuizRepository(dsl), new QuestionRepository(dsl), new AdminSettingsRepository(dsl));
	}

	@Test
	void exportAllEmptyWhenNoQuizzes() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		String json = svc.exportAll();
		ExportBundle b = Json.read(json, ExportBundle.class);
		assertTrue(b.getQuizzes().isEmpty());
	}

	@Test
	void exportAllSerializesQuizzesQuestionsAndSettings() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());

		rp.getQr().create(new Quiz("qz1", "Quiz1", "desc", "admin", Instant.now(), false));
		// null config -> LinkedHashMap branch
		rp.getQnr()
				.save(new Question("qu1", "qz1", "Q1", "d", "MCQ", List.of("java"), 30, 100, null, 0, Instant.now()));
		// blank config -> LinkedHashMap branch
		rp.getQnr().save(new Question("qu2", "qz1", "Q2", "d", "MCQ", null, 30, 100, "", 1, Instant.now()));
		// non-blank config -> Json.readMap branch
		rp.getQnr().save(new Question("qu3", "qz1", "Q3", "d", "MCQ", null, 30, 100, "{\"k\":1}", 2, Instant.now()));
		rp.getSr().put("theme", "dark");

		String json = svc.exportAll();
		ExportBundle b = Json.read(json, ExportBundle.class);
		assertEquals(1, b.getQuizzes().size());
		assertEquals("qz1", b.getQuizzes().get(0).getId());
		assertEquals(3, b.getQuizzes().get(0).getQuestions().size());
		assertEquals("dark", b.getAdminSettings().get("theme"));
	}

	@Test
	void importAllUsesProvidedIdsAndWritesSettings() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());

		ExportBundle.QuestionExport ex1 = new ExportBundle.QuestionExport("qu1", "MCQ", "Q1", "d", 30, 100,
				Map.of("options", List.of("a", "b", "c"), "correctIndex", 2), List.of("java"));
		ExportBundle.QuestionExport ex2 = new ExportBundle.QuestionExport("qu2", "MCQ", "Q2", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "Quiz1", "desc", false, List.of(ex1, ex2));
		ExportBundle bundle = new ExportBundle("1.0", 123L, List.of(qe), Map.of("theme", "light"));

		int count = svc.importAll(Json.write(bundle), false);
		assertEquals(2, count);
		assertTrue(rp.getQr().findById("q1").isPresent());
		assertEquals(2, rp.getQnr().findByQuiz("q1").size());
		assertEquals("light", rp.getSr().findByKey("theme").orElseThrow().getValue());
	}

	@Test
	void importAllGeneratesIdsWhenAbsent() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());

		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(null, "MCQ", "Q", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport(null, "QuizG", "desc", false, List.of(ex));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), Map.of("k", "v"));

		int count = svc.importAll(Json.write(bundle), false);
		assertEquals(1, count);
		List<Quiz> all = rp.getQr().findAll();
		assertEquals(1, all.size());
		assertNotNull(all.get(0).getId());
		assertFalse(all.get(0).getId().isBlank());
		assertEquals(1, rp.getQnr().findByQuiz(all.get(0).getId()).size());
		assertEquals("v", rp.getSr().findByKey("k").orElseThrow().getValue());
	}

	@Test
	void importAllFallsBackToUuidForBlankIds() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());

		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("", "MCQ", "Q", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("", "QuizB", "desc", false, List.of(ex));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), Map.of("k", "v"));

		int count = svc.importAll(Json.write(bundle), false);
		assertEquals(1, count);
		List<Quiz> all = rp.getQr().findAll();
		assertEquals(1, all.size());
		assertFalse(all.get(0).getId().isBlank());
	}

	@Test
	void importAllSkipsSettingsWhenNull() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());

		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", "Q", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "Quiz1", "desc", false, List.of(ex));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);

		int count = svc.importAll(Json.write(bundle), false);
		assertEquals(1, count);
		assertTrue(rp.getQr().findById("q1").isPresent());
		assertFalse(rp.getSr().findByKey("theme").isPresent());
	}

	@Test
	void importAllReplaceDeletesExisting() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());

		rp.getQr().create(new Quiz("old", "Old", "d", "a", Instant.now(), false));
		rp.getQnr().save(new Question("oldq", "old", "Q", "d", "MCQ", null, 30, 100, null, 0, Instant.now()));

		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", "Q", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "New", "desc", false, List.of(ex));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);

		int count = svc.importAll(Json.write(bundle), true);
		assertEquals(1, count);
		assertFalse(rp.getQr().findById("old").isPresent());
		assertTrue(rp.getQr().findById("q1").isPresent());
	}

	@Test
	void importAllNoReplaceKeepsExisting() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());

		rp.getQr().create(new Quiz("keep", "Keep", "d", "a", Instant.now(), false));

		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu2", "MCQ", "Q", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q2", "Add", "desc", false, List.of(ex));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);

		int count = svc.importAll(Json.write(bundle), false);
		assertEquals(1, count);
		assertTrue(rp.getQr().findById("keep").isPresent());
		assertTrue(rp.getQr().findById("q2").isPresent());
	}

	@Test
	void importAllInvalidJsonThrows() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll("{not json", false);
			}
		});
	}

	@Test
	void importAllNoReplaceRejectsCollidingQuizId() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		rp.getQr().create(new Quiz("q1", "Keep", "d", "a", Instant.now(), false));
		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu9", "MCQ", "Q", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "Clash", "desc", false, List.of(ex));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertEquals("Keep", rp.getQr().findById("q1").orElseThrow().getTitle());
	}

	@Test
	void importAllNoReplaceRejectsCollidingQuestionId() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		rp.getQr().create(new Quiz("q1", "Keep", "d", "a", Instant.now(), false));
		rp.getQnr().save(new Question("qu1", "q1", "Q", "d", "MCQ", null, 30, 100, null, 0, Instant.now()));
		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", "Changed", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q2", "Add", "desc", false, List.of(ex));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsNullSettingValue() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", "Q", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "Quiz1", "desc", false, List.of(ex));
		java.util.Map<String, String> settings = new java.util.HashMap<>();
		settings.put("k", null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), settings);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertFalse(rp.getQr().findById("q1").isPresent());
	}

	private ExportBundle.QuestionExport qex(String id, String title) {
		return new ExportBundle.QuestionExport(id, "MCQ", title, "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
	}

	private ExportBundle.QuizExport qzex(String id, String title, List<ExportBundle.QuestionExport> qs) {
		return new ExportBundle.QuizExport(id, title, "desc", false, qs);
	}

	@Test
	void importAllRejectsMissingQuizzesField() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll("{\"version\":\"1.0\"}", false);
			}
		});
	}

	@Test
	void importAllRejectsNullBundle() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll("null", false);
			}
		});
	}

	@Test
	void importAllReplaceWithEmptyQuizzesRefusesToWipe() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		rp.getQr().create(new Quiz("keep", "Keep", "d", "a", Instant.now(), false));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), true);
			}
		});
		assertTrue(rp.getQr().findById("keep").isPresent());
	}

	@Test
	void importAllRejectsNullQuizEntry() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		java.util.List<ExportBundle.QuizExport> qs = new java.util.ArrayList<>();
		qs.add(null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, qs, null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsBlankQuizTitle() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "   ", List.of(qex("qu1", "Q")))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertFalse(rp.getQr().findById("q1").isPresent());
	}

	@Test
	void importAllRejectsNullQuizTitle() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", null, List.of(qex("qu1", "Q")))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsOverlongQuizTitle() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L,
				List.of(qzex("q1", "x".repeat(201), List.of(qex("qu1", "Q")))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsNullQuestionEntry() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		java.util.List<ExportBundle.QuestionExport> qs = new java.util.ArrayList<>();
		qs.add(null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", qs)), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertFalse(rp.getQr().findById("q1").isPresent());
	}

	@Test
	void importAllRejectsBlankQuestionTitle() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(qex("qu1", "")))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsNullQuestionTitle() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", null, "d", 30, 100, Map.of(),
				null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsOverlongQuestionTitle() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L,
				List.of(qzex("q1", "Quiz", List.of(qex("qu1", "y".repeat(201))))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsUnknownQuestionType() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "NOPE", "Q", "d", 30, 100, Map.of(),
				null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsBlankQuestionType() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "  ", "Q", "d", 30, 100, Map.of(),
				null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsZeroTimeLimit() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", "Q", "d", 0, 100, Map.of(),
				null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllRejectsNegativePointsBase() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", "Q", "d", 30, -1, Map.of(),
				null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertFalse(rp.getQr().findById("q1").isPresent());
	}

	@Test
	void importAllRejectsBlankSettingKey() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(qex("qu1", "Q")))),
				Map.of("  ", "v"));
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertFalse(rp.getQr().findById("q1").isPresent());
	}

	@Test
	void importAllAcceptsMultipleValidSettings() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(qex("qu1", "Q")))),
				Map.of("a", "1", "b", "2"));
		int n = svc.importAll(Json.write(bundle), false);
		assertEquals(1, n);
		assertEquals("1", rp.getSr().findByKey("a").orElseThrow().getValue());
		assertEquals("2", rp.getSr().findByKey("b").orElseThrow().getValue());
	}

	@Test
	void importAllRejectsIntraBundleDuplicateQuizId() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L,
				List.of(qzex("dup", "A", List.of(qex("qa", "Q"))), qzex("dup", "B", List.of(qex("qb", "Q")))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertFalse(rp.getQr().findById("dup").isPresent());
	}

	@Test
	void importAllRejectsIntraBundleDuplicateQuestionId() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L,
				List.of(qzex("q1", "A", List.of(qex("same", "Q"))), qzex("q2", "B", List.of(qex("same", "Q")))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertFalse(rp.getQr().findById("q1").isPresent());
		assertFalse(rp.getQr().findById("q2").isPresent());
	}

	@Test
	void importAllRejectsDuplicateQuestionWithinOneQuiz() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L,
				List.of(qzex("q1", "Quiz", List.of(qex("same", "Q1"), qex("same", "Q2")))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
	}

	@Test
	void importAllReplaceAllowsSameIdAsExistingBank() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		rp.getQr().create(new Quiz("q1", "Old", "d", "a", Instant.now(), false));
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "New", List.of(qex("qu1", "Q")))), null);
		int n = svc.importAll(Json.write(bundle), true);
		assertEquals(1, n);
		assertEquals("New", rp.getQr().findById("q1").orElseThrow().getTitle());
	}

	@Test
	void importAllReplaceRejectsIntraBundleDuplicates() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L,
				List.of(qzex("dup", "A", List.of(qex("qa", "Q"))), qzex("dup", "B", List.of(qex("qb", "Q")))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), true);
			}
		});
	}

	@Test
	void importAllWithNullQuestionsListImportsZeroQuestions() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", null)), null);
		int n = svc.importAll(Json.write(bundle), false);
		assertEquals(0, n);
		assertTrue(rp.getQr().findById("q1").isPresent());
		assertTrue(rp.getQnr().findByQuiz("q1").isEmpty());
	}

	@Test
	void exportSurvivesMalformedQuestionConfig() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		rp.getQr().create(new Quiz("qz1", "Quiz1", "desc", "admin", Instant.now(), false));
		rp.getQnr().save(new Question("qu1", "qz1", "Q1", "d", "MCQ", null, 30, 100, "{broken json", 0, Instant.now()));
		String json = svc.exportAll();
		ExportBundle b = Json.read(json, ExportBundle.class);
		assertEquals(1, b.getQuizzes().size());
		assertEquals(1, b.getQuizzes().get(0).getQuestions().size());
	}

	@Test
	void importAllValidationFailureWipesNothing() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		rp.getQr().create(new Quiz("keep", "Keep", "d", "a", Instant.now(), false));
		ExportBundle.QuestionExport bad = new ExportBundle.QuestionExport("qu1", "MCQ", "", "d", 30, 100, Map.of(),
				null);
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q2", "New", List.of(bad))), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				svc.importAll(Json.write(bundle), false);
			}
		});
		assertTrue(rp.getQr().findById("keep").isPresent());
		assertFalse(rp.getQr().findById("q2").isPresent());
	}

	@Test
	void importAllBlankQuizIdGeneratesFreshId() throws Exception {
		Repos rp = repos();
		ImportExportService svc = new ImportExportService(rp.getQr(), rp.getQnr(), rp.getSr());
		ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("   ", "Quiz", List.of(qex("  ", "Q")))), null);
		int n = svc.importAll(Json.write(bundle), false);
		assertEquals(1, n);
		assertEquals(1, rp.getQr().findAll().size());
		assertFalse(rp.getQr().findAll().get(0).getId().isBlank());
	}
}
