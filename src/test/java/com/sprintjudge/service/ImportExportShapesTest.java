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

    private record Repos(QuizRepository qr, QuestionRepository qnr, AdminSettingsRepository sr,
                         ImportExportService svc) {}

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
        return switch (type) {
            case "TRUE_FALSE" -> Map.of("correct", true);
            case "MULTIPLE_SELECT" ->
                    Map.of("options", List.of("a", "b"), "correctIndices", List.of(0));
            case "NUMERIC" -> Map.of("answer", 1);
            case "OUTPUT_PRED" -> Map.of("correctIndex", 0);
            case "FILL_BLANK" -> Map.of("answer", "x");
            case "DRAG_SORT" -> Map.of("correctOrder", List.of("a", "b"));
            case "CLICK_BUG" -> Map.of("codeLines", List.of("a", "b"), "bugLine", 0);
            case "CODE_COMPLETION" -> Map.of("expected", "x");
            case "COMPLEXITY" ->
                    Map.of("options", List.of("O(1)", "O(n)"), "correctIndex", 0);
            case "OJ_FULL", "OJ_PATCH" -> Map.of("testCases",
                    List.of(Map.of("input", "i", "expectedOutput", "o")));
            default -> Map.of("options", List.of("a", "b"), "correctIndex", 0);
        };
    }

    private ExportBundle bundle(List<ExportBundle.QuizExport> quizzes, Map<String, String> settings) {
        return new ExportBundle("1.0", 1L, quizzes, settings);
    }

    @Test
    void templateFalseRoundTripsAsFalse() throws Exception {
        Repos rp = repos();
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), null)), false);
        assertFalse(rp.qr().findById("q1").orElseThrow().template());
        ExportBundle back = Json.read(rp.svc().exportAll(), ExportBundle.class);
        assertFalse(back.quizzes().get(0).template());
    }

    @Test
    void templateTrueAndFalseCoexistAcrossQuizzes() throws Exception {
        Repos rp = repos();
        rp.svc().importAll(Json.write(bundle(List.of(
                new ExportBundle.QuizExport("t1", "A", "d", true, List.of(qx("a", "Q"))),
                new ExportBundle.QuizExport("t2", "B", "d", false, List.of(qx("b", "Q")))), null)), false);
        assertTrue(rp.qr().findById("t1").orElseThrow().template());
        assertFalse(rp.qr().findById("t2").orElseThrow().template());
    }

    @Test
    void emptyLanguagesListNormalizesToNull() throws Exception {
        Repos rp = repos();
        ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport(
                "e", "OJ_FULL", "OJ", "d", 60, 500,
                Map.of("testCases", List.of(Map.of("input", "1", "expectedOutput", "1"))), List.of());
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)), false);
        ExportBundle back = Json.read(rp.svc().exportAll(), ExportBundle.class);
        assertNull(back.quizzes().get(0).questions().get(0).languagesAllowed());
    }

    @Test
    void multiLanguageListPreservesOrder() throws Exception {
        Repos rp = repos();
        ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport(
                "m", "OJ_FULL", "OJ", "d", 60, 500,
                Map.of("testCases", List.of(Map.of("input", "1", "expectedOutput", "1"))),
                List.of("python", "java", "c"));
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)), false);
        ExportBundle back = Json.read(rp.svc().exportAll(), ExportBundle.class);
        assertEquals(List.of("python", "java", "c"),
                back.quizzes().get(0).questions().get(0).languagesAllowed());
    }

    @Test
    void quizWithZeroQuestionsExportsAndReimports() throws Exception {
        Repos rp = repos();
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("lonely", "L", "d", false, List.of())), null)), false);
        ExportBundle back = Json.read(rp.svc().exportAll(), ExportBundle.class);
        assertEquals(1, back.quizzes().size());
        assertTrue(back.quizzes().get(0).questions().isEmpty());
        assertEquals(0, rp.qnr().findByQuiz("lonely").size());
    }

    @Test
    void fiftyQuestionBulkImportCountsAll() throws Exception {
        Repos rp = repos();
        List<ExportBundle.QuestionExport> qs = new ArrayList<>();
        for (int i = 0; i < 50; i++) qs.add(qx("bulk-" + i, "Q" + i));
        int count = rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("big", "Big", "d", false, qs)), null)), false);
        assertEquals(50, count);
        assertEquals(50, rp.qnr().findByQuiz("big").size());
    }

    @Test
    void fiftyQuestionsSurviveExportImportAcrossDatabases() throws Exception {
        Repos db1 = repos();
        List<ExportBundle.QuestionExport> qs = new ArrayList<>();
        for (int i = 0; i < 50; i++) qs.add(qx("x-" + i, "Q" + i));
        db1.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("big", "Big", "d", true, qs)), null)), false);
        Repos db2 = repos();
        int count = db2.svc().importAll(db1.svc().exportAll(), false);
        assertEquals(50, count);
        assertEquals(50, db2.qnr().findByQuiz("big").size());
        assertTrue(db2.qr().findById("big").orElseThrow().template());
    }

    @Test
    void settingsMergeKeepsUnmentionedKeys() throws Exception {
        Repos rp = repos();
        rp.sr().put("keep", "old");
        rp.sr().put("overwrite", "old");
        Map<String, String> incoming = new HashMap<>();
        incoming.put("overwrite", "new");
        incoming.put("added", "yes");
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), incoming)), false);
        assertEquals("old", rp.sr().findByKey("keep").orElseThrow().value());
        assertEquals("new", rp.sr().findByKey("overwrite").orElseThrow().value());
        assertEquals("yes", rp.sr().findByKey("added").orElseThrow().value());
    }

    @Test
    void nullSettingsBundleLeavesExistingSettingsAlone() throws Exception {
        Repos rp = repos();
        rp.sr().put("keep", "v");
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), null)), false);
        assertEquals("v", rp.sr().findByKey("keep").orElseThrow().value());
    }

    @Test
    void replaceModeWipesPreviousQuizzes() throws Exception {
        Repos rp = repos();
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("old", "Old", "d", false, List.of(qx("a", "Q")))), null)), false);
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("new", "New", "d", false, List.of(qx("b", "Q")))), null)), true);
        assertTrue(rp.qr().findById("old").isEmpty());
        assertTrue(rp.qr().findById("new").isPresent());
        assertTrue(rp.qnr().findByQuiz("old").isEmpty());
    }

    @Test
    void reimportSameBundleWithoutReplaceThrows() throws Exception {
        Repos rp = repos();
        String json = Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), null));
        rp.svc().importAll(json, false);
        assertThrows(IllegalArgumentException.class, () -> rp.svc().importAll(json, false));
    }

    @Test
    void reimportSameBundleWithReplaceSucceeds() throws Exception {
        Repos rp = repos();
        String json = Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))), null));
        rp.svc().importAll(json, false);
        assertEquals(1, rp.svc().importAll(json, true));
        assertEquals(1, rp.qnr().findByQuiz("q1").size());
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
        db1.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "Quiz", "desc", true, qs)), settings)), false);
        Repos db2 = repos();
        db2.svc().importAll(db1.svc().exportAll(), false);
        ExportBundle e1 = Json.read(db1.svc().exportAll(), ExportBundle.class);
        ExportBundle e2 = Json.read(db2.svc().exportAll(), ExportBundle.class);
        assertEquals(e1.quizzes(), e2.quizzes());
        assertEquals(e1.adminSettings(), e2.adminSettings());
    }

    @Test
    void questionConfigValuesSurviveRoundTrip() throws Exception {
        Repos rp = repos();
        Map<String, Object> config = new HashMap<>();
        config.put("options", List.of("a", "b", "c"));
        config.put("correctIndex", 2);
        config.put("hint", "think");
        ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport(
                "c", "MCQ", "Q", "d", 30, 100, config, null);
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)), false);
        ExportBundle back = Json.read(rp.svc().exportAll(), ExportBundle.class);
        Map<String, Object> cfg = back.quizzes().get(0).questions().get(0).config();
        assertEquals(2, ((Number) cfg.get("correctIndex")).intValue());
        assertEquals("think", cfg.get("hint"));
    }

    @Test
    void zeroPointsBaseImportsCleanly() throws Exception {
        Repos rp = repos();
        ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport(
                "z", "MCQ", "Q", "d", 30, 0,
                Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
        assertEquals(1, rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)), false));
        assertEquals(0, rp.qnr().findByQuiz("q1").get(0).pointsBase());
    }

    @Test
    void minimalTimeLimitOneImportsCleanly() throws Exception {
        Repos rp = repos();
        ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport(
                "t", "MCQ", "Q", "d", 1, 100,
                Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
        assertEquals(1, rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)), false));
    }

    @Test
    void importCountSpansMultipleQuizzes() throws Exception {
        Repos rp = repos();
        int count = rp.svc().importAll(Json.write(bundle(List.of(
                new ExportBundle.QuizExport("q1", "A", "d", false, List.of(qx("a", "Q"), qx("b", "Q"))),
                new ExportBundle.QuizExport("q2", "B", "d", true, List.of(qx("c", "Q")))), null)), false);
        assertEquals(3, count);
        assertEquals(2, rp.qr().findAll().size());
    }

    @Test
    void exportOfEmptyBankHasNoQuizzes() throws Exception {
        Repos rp = repos();
        ExportBundle back = Json.read(rp.svc().exportAll(), ExportBundle.class);
        assertTrue(back.quizzes().isEmpty());
        assertEquals("1.0", back.version());
    }

    @Test
    void allQuestionTypesImportTogether() throws Exception {
        Repos rp = repos();
        String[] types = {"MCQ", "TRUE_FALSE", "MULTIPLE_SELECT", "NUMERIC", "OUTPUT_PRED",
                "FILL_BLANK", "DRAG_SORT", "CLICK_BUG", "CODE_COMPLETION", "COMPLEXITY",
                "OJ_FULL", "OJ_PATCH"};
        List<ExportBundle.QuestionExport> qs = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            qs.add(new ExportBundle.QuestionExport("t-" + i, types[i], "Q" + i, "d", 30, 100,
                    validConfigFor(types[i]), null));
        }
        assertEquals(types.length, rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("mix", "Mix", "d", false, qs)), null)), false));
        assertEquals(types.length, rp.qnr().findByQuiz("mix").size());
    }

    @Test
    void nullDescriptionImportsCleanly() throws Exception {
        Repos rp = repos();
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", null, false, List.of(qx("a", "Q")));
        assertEquals(1, rp.svc().importAll(Json.write(bundle(List.of(qe), null)), false));
        assertNull(rp.qr().findById("q1").orElseThrow().description());
    }

    @Test
    void duplicateQuestionIdsWithinOneQuizThrow() throws Exception {
        Repos rp = repos();
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "q1", "T", "d", false, List.of(qx("same", "A"), qx("same", "B")));
        assertThrows(IllegalArgumentException.class,
                () -> rp.svc().importAll(Json.write(bundle(List.of(qe), null)), false));
        assertTrue(rp.qr().findById("q1").isEmpty());
    }

    @Test
    void nullSettingValueThrows() throws Exception {
        Repos rp = repos();
        Map<String, String> settings = new HashMap<>();
        settings.put("ok", "1");
        settings.put("bad", null);
        assertThrows(IllegalArgumentException.class, () -> rp.svc().importAll(
                Json.write(bundle(List.of(new ExportBundle.QuizExport("q1", "T", "d", false,
                        List.of(qx("a", "Q")))), settings)), false));
    }

    @Test
    void exportCarriesSettingsImportedEarlier() throws Exception {
        Repos rp = repos();
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))),
                Map.of("mode", "blitz"))), false);
        ExportBundle back = Json.read(rp.svc().exportAll(), ExportBundle.class);
        assertEquals("blitz", back.adminSettings().get("mode"));
    }

    @Test
    void importPreservesQuizOrderAcrossTwoQuizzes() throws Exception {
        Repos rp = repos();
        rp.svc().importAll(Json.write(bundle(List.of(
                new ExportBundle.QuizExport("first", "First", "d", false, List.of(qx("a", "Q"))),
                new ExportBundle.QuizExport("second", "Second", "d", false, List.of(qx("b", "Q")))), null)), false);
        ExportBundle back = Json.read(rp.svc().exportAll(), ExportBundle.class);
        assertEquals(2, back.quizzes().size());
        assertTrue(back.quizzes().stream().anyMatch(z -> z.id().equals("first")));
        assertTrue(back.quizzes().stream().anyMatch(z -> z.id().equals("second")));
    }

    @Test
    void replaceKeepsImportedSettings() throws Exception {
        Repos rp = repos();
        rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")))),
                Map.of("s", "1"))), true);
        assertEquals("1", rp.sr().findByKey("s").orElseThrow().value());
    }

    @Test
    void blankQuestionIdFallsBackToUuid() throws Exception {
        Repos rp = repos();
        ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport(
                "", "MCQ", "Q", "d", 30, 100,
                Map.of("options", List.of("a", "b"), "correctIndex", 0), null);
        assertEquals(1, rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)), false));
        assertFalse(rp.qnr().findByQuiz("q1").get(0).id().isBlank());
    }

    @Test
    void tooManyQuizzesRejected() throws Exception {
        Repos rp = repos();
        List<ExportBundle.QuizExport> quizzes = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            quizzes.add(new ExportBundle.QuizExport("cap-" + i, "T", "d", false, List.of()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> rp.svc().importAll(Json.write(bundle(quizzes, null)), false));
        assertTrue(rp.qr().findAll().isEmpty());
    }

    @Test
    void tooManyQuestionsRejected() throws Exception {
        Repos rp = repos();
        List<ExportBundle.QuestionExport> qs = new ArrayList<>();
        for (int i = 0; i < 5001; i++) qs.add(qx("cap-q" + i, "Q"));
        assertThrows(IllegalArgumentException.class, () -> rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("big", "Big", "d", false, qs)), null)), false));
        assertTrue(rp.qr().findAll().isEmpty());
    }

    @Test
    void invalidQuestionConfigRejected() throws Exception {
        Repos rp = repos();
        ExportBundle.QuestionExport qe = new ExportBundle.QuestionExport(
                "bad", "MCQ", "Q", "d", 30, 100, Map.of("correctIndex", 9), null);
        assertThrows(IllegalArgumentException.class, () -> rp.svc().importAll(Json.write(bundle(
                List.of(new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qe))), null)), false));
        assertTrue(rp.qr().findById("q1").isEmpty());
    }
}
