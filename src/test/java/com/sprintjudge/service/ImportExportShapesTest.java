package com.sprintjudge.service;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.repository.AdminSettingsRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.util.Json;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportExportShapesTest {

	private static final class Repos {
		private QuizRepository qr;
		private QuestionRepository qnr;
		private AdminSettingsRepository sr;
		private ImportExportService svc;

		Repos(QuizRepository qr, QuestionRepository qnr, AdminSettingsRepository sr, ImportExportService svc) {
			this.qr = qr;
			this.qnr = qnr;
			this.sr = sr;
			this.svc = svc;
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
		ImportExportService getSvc() {
			return svc;
		}
		void setSvc(ImportExportService svc) {
			this.svc = svc;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Repos))
				return false;
			Repos other = (Repos) o;
			return java.util.Objects.equals(this.qr, other.qr) && java.util.Objects.equals(this.qnr, other.qnr)
					&& java.util.Objects.equals(this.sr, other.sr) && java.util.Objects.equals(this.svc, other.svc);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(qr, qnr, sr, svc);
		}

		@Override
		public String toString() {
			return "Repos{qr=" + qr + ", qnr=" + qnr + ", sr=" + sr + ", svc=" + svc + "}";
		}
	}

	private Repos repos() throws Exception {
		DSLContext dsl = TestDb.inMemory();
		QuizRepository qr = new QuizRepository(dsl);
		QuestionRepository qnr = new QuestionRepository(dsl);
		AdminSettingsRepository sr = new AdminSettingsRepository(dsl);
		return new Repos(qr, qnr, sr, new ImportExportService(qr, qnr, sr));
	}

	private ExportBundle.QuestionExport qx(String id, String title) {
		return new ExportBundle.QuestionExport(id, "MCQ", title, "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
	}

	private static Map<String, Object> validConfigFor(String type) {
		switch (type) {
			case "TRUE_FALSE" :
				return Map.of("correct", true);
			case "MULTIPLE_SELECT" :
				return Map.of("options", List.of("a", "b"), "correctIndices", List.of(0));
			case "NUMERIC" :
				return Map.of("answer", 1);
			case "OUTPUT_PRED" :
				return Map.of("correctIndex", 0);
			case "FILL_BLANK" :
				return Map.of("answer", "x");
			case "DRAG_SORT" :
				return Map.of("correctOrder", List.of("a", "b"));
			case "CLICK_BUG" :
				return Map.of("codeLines", List.of("a", "b"), "bugLine", 0);
			case "CODE_COMPLETION" :
				return Map.of("expected", "x");
			case "COMPLEXITY" :
				return Map.of("options", List.of("O(1)", "O(n)"), "correctIndex", 0);
			case "OJ_FULL" :
			case "OJ_PATCH" :
				return Map.of("testCases", List.of(Map.of("input", "i", "expectedOutput", "o")));
			default :
				return Map.of("options", List.of("a", "b"), "correctIndex", 0);
		}
	}

	private ExportBundle bundle(List<ExportBundle.QuizExport> quizzes, Map<String, String> settings) {
		return new ExportBundle("1.0", 1L, quizzes, settings);
	}

	@Test
	void templateFalseRoundTripsAsFalse() throws Exception {
		Repos rp = repos();
		rp.getSvc()
				.importAll(Json.write(bundle(
						List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), null)),
						false);
		assertFalse(rp.getQr().findById("q1").orElseThrow().isTemplate());
		ExportBundle back = Json.read(rp.getSvc().exportAll(), ExportBundle.class);
		assertFalse(back.getQuizzes().get(0).isTemplate());
	}

	@Test
	void templateTrueAndFalseCoexistAcrossQuizzes() throws Exception {
		Repos rp = repos();
		rp.getSvc()
				.importAll(
						Json.write(bundle(
								List.of(new ExportBundle.QuizExport("t1", "A", "d", true, List.of(qx("a", "Q"))),
										new ExportBundle.QuizExport("t2", "B", "d", false, List.of(qx("b", "Q")))),
								null)),
						false);
		assertTrue(rp.getQr().findById("t1").orElseThrow().isTemplate());
		assertFalse(rp.getQr().findById("t2").orElseThrow().isTemplate());
	}

	@Test
	void emptyLanguagesListNormalizesToNull() throws Exception {
		Repos rp = repos();
		ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport("e", "OJ_FULL", "OJ", "d", 60, 500,
				Map.of("testCases", List.of(Map.of("input", "1", "expectedOutput", "1"))), List.of());
		rp.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)),
				false);
		ExportBundle back = Json.read(rp.getSvc().exportAll(), ExportBundle.class);
		assertNull(back.getQuizzes().get(0).getQuestions().get(0).getLanguagesAllowed());
	}

	@Test
	void multiLanguageListPreservesOrder() throws Exception {
		Repos rp = repos();
		ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport("m", "OJ_FULL", "OJ", "d", 60, 500,
				Map.of("testCases", List.of(Map.of("input", "1", "expectedOutput", "1"))),
				List.of("python", "java", "c"));
		rp.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)),
				false);
		ExportBundle back = Json.read(rp.getSvc().exportAll(), ExportBundle.class);
		assertEquals(List.of("python", "java", "c"),
				back.getQuizzes().get(0).getQuestions().get(0).getLanguagesAllowed());
	}

	@Test
	void quizWithZeroQuestionsExportsAndReimports() throws Exception {
		Repos rp = repos();
		rp.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("lonely", "L", "d", false, List.of())), null)),
				false);
		ExportBundle back = Json.read(rp.getSvc().exportAll(), ExportBundle.class);
		assertEquals(1, back.getQuizzes().size());
		assertTrue(back.getQuizzes().get(0).getQuestions().isEmpty());
		assertEquals(0, rp.getQnr().findByQuiz("lonely").size());
	}

	@Test
	void fiftyQuestionBulkImportCountsAll() throws Exception {
		Repos rp = repos();
		List<ExportBundle.QuestionExport> qs = new ArrayList<>();
		for (int i = 0; i < 50; i++)
			qs.add(qx("bulk-" + i, "Q" + i));
		int count = rp.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("big", "Big", "d", false, qs)), null)), false);
		assertEquals(50, count);
		assertEquals(50, rp.getQnr().findByQuiz("big").size());
	}

	@Test
	void fiftyQuestionsSurviveExportImportAcrossDatabases() throws Exception {
		Repos db1 = repos();
		List<ExportBundle.QuestionExport> qs = new ArrayList<>();
		for (int i = 0; i < 50; i++)
			qs.add(qx("x-" + i, "Q" + i));
		db1.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("big", "Big", "d", true, qs)), null)), false);
		Repos db2 = repos();
		int count = db2.getSvc().importAll(db1.getSvc().exportAll(), false);
		assertEquals(50, count);
		assertEquals(50, db2.getQnr().findByQuiz("big").size());
		assertTrue(db2.getQr().findById("big").orElseThrow().isTemplate());
	}

	@Test
	void settingsMergeKeepsUnmentionedKeys() throws Exception {
		Repos rp = repos();
		rp.getSr().put("keep", "old");
		rp.getSr().put("overwrite", "old");
		Map<String, String> incoming = new HashMap<>();
		incoming.put("overwrite", "new");
		incoming.put("added", "yes");
		rp.getSvc()
				.importAll(Json.write(bundle(
						List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), incoming)),
						false);
		assertEquals("old", rp.getSr().findByKey("keep").orElseThrow().getValue());
		assertEquals("new", rp.getSr().findByKey("overwrite").orElseThrow().getValue());
		assertEquals("yes", rp.getSr().findByKey("added").orElseThrow().getValue());
	}

	@Test
	void nullSettingsBundleLeavesExistingSettingsAlone() throws Exception {
		Repos rp = repos();
		rp.getSr().put("keep", "v");
		rp.getSvc()
				.importAll(Json.write(bundle(
						List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), null)),
						false);
		assertEquals("v", rp.getSr().findByKey("keep").orElseThrow().getValue());
	}

	@Test
	void replaceModeWipesPreviousQuizzes() throws Exception {
		Repos rp = repos();
		rp.getSvc()
				.importAll(Json.write(bundle(
						List.of(new ExportBundle.QuizExport("old", "Old", "d", false, List.of(qx("a", "Q")))), null)),
						false);
		rp.getSvc()
				.importAll(Json.write(bundle(
						List.of(new ExportBundle.QuizExport("new", "New", "d", false, List.of(qx("b", "Q")))), null)),
						true);
		assertTrue(rp.getQr().findById("old").isEmpty());
		assertTrue(rp.getQr().findById("new").isPresent());
		assertTrue(rp.getQnr().findByQuiz("old").isEmpty());
	}

	@Test
	void reimportSameBundleWithoutReplaceThrows() throws Exception {
		Repos rp = repos();
		String json = Json.write(
				bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), null));
		rp.getSvc().importAll(json, false);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				rp.getSvc().importAll(json, false);
			}
		});
	}

	@Test
	void reimportSameBundleWithReplaceSucceeds() throws Exception {
		Repos rp = repos();
		String json = Json.write(
				bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), null));
		rp.getSvc().importAll(json, false);
		assertEquals(1, rp.getSvc().importAll(json, true));
		assertEquals(1, rp.getQnr().findByQuiz("q1").size());
	}

	@Test
	void exportImportRoundTripPreservesQuizzesAndSettings() throws Exception {
		Repos db1 = repos();
		Map<String, String> settings = new HashMap<>();
		settings.put("theme", "dark");
		settings.put("rounds", "5");
		List<ExportBundle.QuestionExport> qs = List.of(
				new ExportBundle.QuestionExport("a", "MCQ", "QA", "da", 30, 100,
						Map.of("options", List.of("x", "y"), "correctIndex", 1), null),
				new ExportBundle.QuestionExport("b", "NUMERIC", "QB", "db", 45, 200,
						Map.of("answer", 42, "tolerance", 0.5), null));
		db1.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("q1", "Quiz", "desc", true, qs)), settings)),
				false);
		Repos db2 = repos();
		db2.getSvc().importAll(db1.getSvc().exportAll(), false);
		ExportBundle e1 = Json.read(db1.getSvc().exportAll(), ExportBundle.class);
		ExportBundle e2 = Json.read(db2.getSvc().exportAll(), ExportBundle.class);
		assertEquals(e1.getQuizzes(), e2.getQuizzes());
		assertEquals(e1.getAdminSettings(), e2.getAdminSettings());
	}

	@Test
	void questionConfigValuesSurviveRoundTrip() throws Exception {
		Repos rp = repos();
		Map<String, Object> config = new HashMap<>();
		config.put("options", List.of("a", "b", "c"));
		config.put("correctIndex", 2);
		config.put("hint", "think");
		ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport("c", "MCQ", "Q", "d", 30, 100, config, null);
		rp.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)),
				false);
		ExportBundle back = Json.read(rp.getSvc().exportAll(), ExportBundle.class);
		Map<String, Object> cfg = back.getQuizzes().get(0).getQuestions().get(0).getConfig();
		assertEquals(2, ((Number) cfg.get("correctIndex")).intValue());
		assertEquals("think", cfg.get("hint"));
	}

	@Test
	void zeroPointsBaseImportsCleanly() throws Exception {
		Repos rp = repos();
		ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport("z", "MCQ", "Q", "d", 30, 0,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		assertEquals(1,
				rp.getSvc()
						.importAll(Json.write(
								bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)),
								false));
		assertEquals(0, rp.getQnr().findByQuiz("q1").get(0).getPointsBase());
	}

	@Test
	void minimalTimeLimitOneImportsCleanly() throws Exception {
		Repos rp = repos();
		ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport("t", "MCQ", "Q", "d", 1, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		assertEquals(1,
				rp.getSvc()
						.importAll(Json.write(
								bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)),
								false));
	}

	@Test
	void importCountSpansMultipleQuizzes() throws Exception {
		Repos rp = repos();
		int count = rp.getSvc().importAll(
				Json.write(bundle(
						List.of(new ExportBundle.QuizExport("q1", "A", "d", false, List.of(qx("a", "Q"), qx("b", "Q"))),
								new ExportBundle.QuizExport("q2", "B", "d", true, List.of(qx("c", "Q")))),
						null)),
				false);
		assertEquals(3, count);
		assertEquals(2, rp.getQr().findAll().size());
	}

	@Test
	void exportOfEmptyBankHasNoQuizzes() throws Exception {
		Repos rp = repos();
		ExportBundle back = Json.read(rp.getSvc().exportAll(), ExportBundle.class);
		assertTrue(back.getQuizzes().isEmpty());
		assertEquals("1.0", back.getVersion());
	}

	@Test
	void allQuestionTypesImportTogether() throws Exception {
		Repos rp = repos();
		String[] types = {"MCQ", "TRUE_FALSE", "MULTIPLE_SELECT", "NUMERIC", "OUTPUT_PRED", "FILL_BLANK", "DRAG_SORT",
				"CLICK_BUG", "CODE_COMPLETION", "COMPLEXITY", "OJ_FULL", "OJ_PATCH"};
		List<ExportBundle.QuestionExport> qs = new ArrayList<>();
		for (int i = 0; i < types.length; i++) {
			qs.add(new ExportBundle.QuestionExport("t-" + i, types[i], "Q" + i, "d", 30, 100, validConfigFor(types[i]),
					null));
		}
		assertEquals(types.length, rp.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("mix", "Mix", "d", false, qs)), null)), false));
		assertEquals(types.length, rp.getQnr().findByQuiz("mix").size());
	}

	@Test
	void nullDescriptionImportsCleanly() throws Exception {
		Repos rp = repos();
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", null, false, List.of(qx("a", "Q")));
		assertEquals(1, rp.getSvc().importAll(Json.write(bundle(List.of(qe), null)), false));
		assertNull(rp.getQr().findById("q1").orElseThrow().getDescription());
	}

	@Test
	void duplicateQuestionIdsWithinOneQuizThrow() throws Exception {
		Repos rp = repos();
		ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false,
				List.of(qx("same", "A"), qx("same", "B")));
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				rp.getSvc().importAll(Json.write(bundle(List.of(qe), null)), false);
			}
		});
		assertTrue(rp.getQr().findById("q1").isEmpty());
	}

	@Test
	void nullSettingValueThrows() throws Exception {
		Repos rp = repos();
		Map<String, String> settings = new HashMap<>();
		settings.put("ok", "1");
		settings.put("bad", null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				rp.getSvc()
						.importAll(Json.write(bundle(
								List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))),
								settings)), false);
			}
		});
	}

	@Test
	void exportCarriesSettingsImportedEarlier() throws Exception {
		Repos rp = repos();
		rp.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))),
						Map.of("mode", "blitz"))),
				false);
		ExportBundle back = Json.read(rp.getSvc().exportAll(), ExportBundle.class);
		assertEquals("blitz", back.getAdminSettings().get("mode"));
	}

	@Test
	void importPreservesQuizOrderAcrossTwoQuizzes() throws Exception {
		Repos rp = repos();
		rp.getSvc()
				.importAll(Json.write(bundle(
						List.of(new ExportBundle.QuizExport("first", "First", "d", false, List.of(qx("a", "Q"))),
								new ExportBundle.QuizExport("second", "Second", "d", false, List.of(qx("b", "Q")))),
						null)), false);
		ExportBundle back = Json.read(rp.getSvc().exportAll(), ExportBundle.class);
		assertEquals(2, back.getQuizzes().size());
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.export.ExportBundle.QuizExport z : back.getQuizzes()) {
				if (z.getId().equals("first")) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.export.ExportBundle.QuizExport z : back.getQuizzes()) {
				if (z.getId().equals("second")) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void replaceKeepsImportedSettings() throws Exception {
		Repos rp = repos();
		rp.getSvc().importAll(
				Json.write(bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))),
						Map.of("s", "1"))),
				true);
		assertEquals("1", rp.getSr().findByKey("s").orElseThrow().getValue());
	}

	@Test
	void blankQuestionIdFallsBackToUuid() throws Exception {
		Repos rp = repos();
		ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport("", "MCQ", "Q", "d", 30, 100,
				Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
		assertEquals(1,
				rp.getSvc()
						.importAll(Json.write(
								bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)),
								false));
		assertFalse(rp.getQnr().findByQuiz("q1").get(0).getId().isBlank());
	}

	@Test
	void tooManyQuizzesRejected() throws Exception {
		Repos rp = repos();
		List<ExportBundle.QuizExport> quizzes = new ArrayList<>();
		for (int i = 0; i < 201; i++) {
			quizzes.add(new ExportBundle.QuizExport("cap-" + i, "T", "d", false, List.of()));
		}
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				rp.getSvc().importAll(Json.write(bundle(quizzes, null)), false);
			}
		});
		assertTrue(rp.getQr().findAll().isEmpty());
	}

	@Test
	void tooManyQuestionsRejected() throws Exception {
		Repos rp = repos();
		List<ExportBundle.QuestionExport> qs = new ArrayList<>();
		for (int i = 0; i < 5001; i++)
			qs.add(qx("cap-q" + i, "Q"));
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				rp.getSvc().importAll(
						Json.write(bundle(List.of(new ExportBundle.QuizExport("big", "Big", "d", false, qs)), null)),
						false);
			}
		});
		assertTrue(rp.getQr().findAll().isEmpty());
	}

	@Test
	void invalidQuestionConfigRejected() throws Exception {
		Repos rp = repos();
		ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport("bad", "MCQ", "Q", "d", 30, 100,
				Map.of("correctIndex", 9), null);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				rp.getSvc()
						.importAll(Json.write(
								bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)),
								false);
			}
		});
		assertTrue(rp.getQr().findById("q1").isEmpty());
	}
}
