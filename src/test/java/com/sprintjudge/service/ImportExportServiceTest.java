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

import static org.junit.jupiter.api.Assertions.*;

class ImportExportServiceTest {

    private record Repos(QuizRepository qr, QuestionRepository qnr, AdminSettingsRepository sr) {}

    private Repos repos() throws Exception {
        DSLContext dsl = TestDb.inMemory();
        return new Repos(new QuizRepository(dsl), new QuestionRepository(dsl), new AdminSettingsRepository(dsl));
    }

    @Test
    void exportAllEmptyWhenNoQuizzes() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        String json = svc.exportAll();
        ExportBundle b = Json.read(json, ExportBundle.class);
        assertTrue(b.quizzes().isEmpty());
    }

    @Test
    void exportAllSerializesQuizzesQuestionsAndSettings() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());

        rp.qr().create(new Quiz("qz1", "Quiz1", "desc", "admin", Instant.now(), false));
        // null config -> LinkedHashMap branch
        rp.qnr().save(new Question("qu1", "qz1", "Q1", "d", "MCQ", List.of("java"), 30, 100, null, 0, Instant.now()));
        // blank config -> LinkedHashMap branch
        rp.qnr().save(new Question("qu2", "qz1", "Q2", "d", "MCQ", null, 30, 100, "", 1, Instant.now()));
        // non-blank config -> Json.readMap branch
        rp.qnr().save(new Question("qu3", "qz1", "Q3", "d", "MCQ", null, 30, 100, "{\"k\":1}", 2, Instant.now()));
        rp.sr().put("theme", "dark");

        String json = svc.exportAll();
        ExportBundle b = Json.read(json, ExportBundle.class);
        assertEquals(1, b.quizzes().size());
        assertEquals("qz1", b.quizzes().get(0).id());
        assertEquals(3, b.quizzes().get(0).questions().size());
        assertEquals("dark", b.adminSettings().get("theme"));
    }

    @Test
    void importAllUsesProvidedIdsAndWritesSettings() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());

        ExportBundle.QuestionExport ex1 = new ExportBundle.QuestionExport(
                "qu1", "MCQ", "Q1", "d", 30, 100, Map.of("correctIndex", 2), List.of("java"));
        ExportBundle.QuestionExport ex2 = new ExportBundle.QuestionExport(
                "qu2", "MCQ", "Q2", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "q1", "Quiz1", "desc", false, List.of(ex1, ex2));
        ExportBundle bundle = new ExportBundle("1.0", 123L, List.of(qe), Map.of("theme", "light"));

        int count = svc.importAll(Json.write(bundle), false);
        assertEquals(2, count);
        assertTrue(rp.qr().findById("q1").isPresent());
        assertEquals(2, rp.qnr().findByQuiz("q1").size());
        assertEquals("light", rp.sr().findByKey("theme").orElseThrow().value());
    }

    @Test
    void importAllGeneratesIdsWhenAbsent() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());

        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                null, "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                null, "QuizG", "desc", false, List.of(ex));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), Map.of("k", "v"));

        int count = svc.importAll(Json.write(bundle), false);
        assertEquals(1, count);
        List<Quiz> all = rp.qr().findAll();
        assertEquals(1, all.size());
        assertNotNull(all.get(0).id());
        assertFalse(all.get(0).id().isBlank());
        assertEquals(1, rp.qnr().findByQuiz(all.get(0).id()).size());
        assertEquals("v", rp.sr().findByKey("k").orElseThrow().value());
    }

    @Test
    void importAllFallsBackToUuidForBlankIds() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());

        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "", "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "", "QuizB", "desc", false, List.of(ex));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), Map.of("k", "v"));

        int count = svc.importAll(Json.write(bundle), false);
        assertEquals(1, count);
        List<Quiz> all = rp.qr().findAll();
        assertEquals(1, all.size());
        assertFalse(all.get(0).id().isBlank());
    }

    @Test
    void importAllSkipsSettingsWhenNull() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());

        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "qu1", "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "q1", "Quiz1", "desc", false, List.of(ex));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);

        int count = svc.importAll(Json.write(bundle), false);
        assertEquals(1, count);
        assertTrue(rp.qr().findById("q1").isPresent());
        assertFalse(rp.sr().findByKey("theme").isPresent());
    }

    @Test
    void importAllReplaceDeletesExisting() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());

        rp.qr().create(new Quiz("old", "Old", "d", "a", Instant.now(), false));
        rp.qnr().save(new Question("oldq", "old", "Q", "d", "MCQ", null, 30, 100, null, 0, Instant.now()));

        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "qu1", "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "q1", "New", "desc", false, List.of(ex));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);

        int count = svc.importAll(Json.write(bundle), true);
        assertEquals(1, count);
        assertFalse(rp.qr().findById("old").isPresent());
        assertTrue(rp.qr().findById("q1").isPresent());
    }

    @Test
    void importAllNoReplaceKeepsExisting() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());

        rp.qr().create(new Quiz("keep", "Keep", "d", "a", Instant.now(), false));

        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "qu2", "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "q2", "Add", "desc", false, List.of(ex));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);

        int count = svc.importAll(Json.write(bundle), false);
        assertEquals(1, count);
        assertTrue(rp.qr().findById("keep").isPresent());
        assertTrue(rp.qr().findById("q2").isPresent());
    }

    @Test
    void importAllInvalidJsonThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        assertThrows(IllegalStateException.class, () -> svc.importAll("{not json", false));
    }

    @Test
    void importAllNoReplaceRejectsCollidingQuizId() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        rp.qr().create(new Quiz("q1", "Keep", "d", "a", Instant.now(), false));
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "qu9", "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "q1", "Clash", "desc", false, List.of(ex));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertEquals("Keep", rp.qr().findById("q1").orElseThrow().title());
    }

    @Test
    void importAllNoReplaceRejectsCollidingQuestionId() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        rp.qr().create(new Quiz("q1", "Keep", "d", "a", Instant.now(), false));
        rp.qnr().save(new Question("qu1", "q1", "Q", "d", "MCQ", null, 30, 100, null, 0, Instant.now()));
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "qu1", "MCQ", "Changed", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "q2", "Add", "desc", false, List.of(ex));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsNullSettingValue() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "qu1", "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport(
                "q1", "Quiz1", "desc", false, List.of(ex));
        java.util.Map<String, String> settings = new java.util.HashMap<>();
        settings.put("k", null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qe), settings);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertFalse(rp.qr().findById("q1").isPresent());
    }

    private ExportBundle.QuestionExport qex(String id, String title) {
        return new ExportBundle.QuestionExport(id, "MCQ", title, "d", 30, 100, Map.of(), null);
    }

    private ExportBundle.QuizExport qzex(String id, String title, List<ExportBundle.QuestionExport> qs) {
        return new ExportBundle.QuizExport(id, title, "desc", false, qs);
    }

    @Test
    void importAllRejectsMissingQuizzesField() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        assertThrows(IllegalArgumentException.class, () -> svc.importAll("{\"version\":\"1.0\"}", false));
    }

    @Test
    void importAllRejectsNullBundle() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        assertThrows(Exception.class, () -> svc.importAll("null", false));
    }

    @Test
    void importAllReplaceWithEmptyQuizzesRefusesToWipe() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        rp.qr().create(new Quiz("keep", "Keep", "d", "a", Instant.now(), false));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), true));
        assertTrue(rp.qr().findById("keep").isPresent());
    }

    @Test
    void importAllRejectsNullQuizEntry() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        java.util.List<ExportBundle.QuizExport> qs = new java.util.ArrayList<>();
        qs.add(null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, qs, null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsBlankQuizTitle() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "   ", List.of(qex("qu1", "Q")))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertFalse(rp.qr().findById("q1").isPresent());
    }

    @Test
    void importAllRejectsNullQuizTitle() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", null, List.of(qex("qu1", "Q")))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsOverlongQuizTitle() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "x".repeat(201), List.of(qex("qu1", "Q")))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsNullQuestionEntry() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        java.util.List<ExportBundle.QuestionExport> qs = new java.util.ArrayList<>();
        qs.add(null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", qs)), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertFalse(rp.qr().findById("q1").isPresent());
    }

    @Test
    void importAllRejectsBlankQuestionTitle() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(qex("qu1", "")))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsNullQuestionTitle() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", null, "d", 30, 100, Map.of(), null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsOverlongQuestionTitle() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(qex("qu1", "y".repeat(201))))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsUnknownQuestionType() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "NOPE", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsBlankQuestionType() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "  ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsZeroTimeLimit() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", "Q", "d", 0, 100, Map.of(), null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllRejectsNegativePointsBase() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport("qu1", "MCQ", "Q", "d", 30, -1, Map.of(), null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", List.of(ex))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertFalse(rp.qr().findById("q1").isPresent());
    }

    @Test
    void importAllRejectsBlankSettingKey() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L,
                List.of(qzex("q1", "Quiz", List.of(qex("qu1", "Q")))), Map.of("  ", "v"));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertFalse(rp.qr().findById("q1").isPresent());
    }

    @Test
    void importAllAcceptsMultipleValidSettings() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L,
                List.of(qzex("q1", "Quiz", List.of(qex("qu1", "Q")))), Map.of("a", "1", "b", "2"));
        int n = svc.importAll(Json.write(bundle), false);
        assertEquals(1, n);
        assertEquals("1", rp.sr().findByKey("a").orElseThrow().value());
        assertEquals("2", rp.sr().findByKey("b").orElseThrow().value());
    }

    @Test
    void importAllRejectsIntraBundleDuplicateQuizId() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(
                qzex("dup", "A", List.of(qex("qa", "Q"))),
                qzex("dup", "B", List.of(qex("qb", "Q")))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertFalse(rp.qr().findById("dup").isPresent());
    }

    @Test
    void importAllRejectsIntraBundleDuplicateQuestionId() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(
                qzex("q1", "A", List.of(qex("same", "Q"))),
                qzex("q2", "B", List.of(qex("same", "Q")))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertFalse(rp.qr().findById("q1").isPresent());
        assertFalse(rp.qr().findById("q2").isPresent());
    }

    @Test
    void importAllRejectsDuplicateQuestionWithinOneQuiz() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(
                qzex("q1", "Quiz", List.of(qex("same", "Q1"), qex("same", "Q2")))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
    }

    @Test
    void importAllReplaceAllowsSameIdAsExistingBank() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        rp.qr().create(new Quiz("q1", "Old", "d", "a", Instant.now(), false));
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(
                qzex("q1", "New", List.of(qex("qu1", "Q")))), null);
        int n = svc.importAll(Json.write(bundle), true);
        assertEquals(1, n);
        assertEquals("New", rp.qr().findById("q1").orElseThrow().title());
    }

    @Test
    void importAllReplaceRejectsIntraBundleDuplicates() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(
                qzex("dup", "A", List.of(qex("qa", "Q"))),
                qzex("dup", "B", List.of(qex("qb", "Q")))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), true));
    }

    @Test
    void importAllWithNullQuestionsListImportsZeroQuestions() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q1", "Quiz", null)), null);
        int n = svc.importAll(Json.write(bundle), false);
        assertEquals(0, n);
        assertTrue(rp.qr().findById("q1").isPresent());
        assertTrue(rp.qnr().findByQuiz("q1").isEmpty());
    }

    @Test
    void exportSurvivesMalformedQuestionConfig() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        rp.qr().create(new Quiz("qz1", "Quiz1", "desc", "admin", Instant.now(), false));
        rp.qnr().save(new Question("qu1", "qz1", "Q1", "d", "MCQ", null, 30, 100, "{broken json", 0, Instant.now()));
        String json = svc.exportAll();
        ExportBundle b = Json.read(json, ExportBundle.class);
        assertEquals(1, b.quizzes().size());
        assertEquals(1, b.quizzes().get(0).questions().size());
    }

    @Test
    void importAllValidationFailureWipesNothing() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        rp.qr().create(new Quiz("keep", "Keep", "d", "a", Instant.now(), false));
        ExportBundle.QuestionExport bad = new ExportBundle.QuestionExport("qu1", "MCQ", "", "d", 30, 100, Map.of(), null);
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(qzex("q2", "New", List.of(bad))), null);
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle), false));
        assertTrue(rp.qr().findById("keep").isPresent());
        assertFalse(rp.qr().findById("q2").isPresent());
    }

    @Test
    void importAllBlankQuizIdGeneratesFreshId() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle bundle = new ExportBundle("1.0", 1L, List.of(
                qzex("   ", "Quiz", List.of(qex("  ", "Q")))), null);
        int n = svc.importAll(Json.write(bundle), false);
        assertEquals(1, n);
        assertEquals(1, rp.qr().findAll().size());
        assertFalse(rp.qr().findAll().get(0).id().isBlank());
    }
}
