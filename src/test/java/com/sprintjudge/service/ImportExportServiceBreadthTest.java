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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportExportServiceBreadthTest {

    private record Repos(QuizRepository qr, QuestionRepository qnr, AdminSettingsRepository sr) {}

    private Repos repos() throws Exception {
        DSLContext dsl = TestDb.inMemory();
        return new Repos(new QuizRepository(dsl), new QuestionRepository(dsl), new AdminSettingsRepository(dsl));
    }

    private ExportBundle.QuestionExport qx(String id, String title) {
        return new ExportBundle.QuestionExport(id, "MCQ", title, "d", 30, 100, Map.of(), null);
    }

    private ExportBundle bundle(List<ExportBundle.QuizExport> quizzes, Map<String, String> settings) {
        return new ExportBundle("1.0", 1L, quizzes, settings);
    }

    @Test
    void replaceWithEmptyQuizzesRefusesToWipe() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        rp.qr().create(new Quiz("keep", "Keep", "d", "a", Instant.now(), false));
        assertThrows(IllegalArgumentException.class,
                () -> svc.importAll(Json.write(bundle(List.of(), null)), true));
        assertTrue(rp.qr().findById("keep").isPresent());
    }

    @Test
    void nullQuizzesFieldThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        assertThrows(IllegalArgumentException.class,
                () -> svc.importAll("{\"version\":\"1.0\",\"exportedAt\":1,\"quizzes\":null}", false));
    }

    @Test
    void nullQuizEntryThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        assertThrows(IllegalArgumentException.class,
                () -> svc.importAll("{\"version\":\"1.0\",\"exportedAt\":1,\"quizzes\":[null]}", false));
    }

    @Test
    void blankQuizTitleThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "  ", "d", false, List.of(qx("a", "Q")));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle(List.of(qe), null)), false));
        assertFalse(rp.qr().findById("q1").isPresent());
    }

    @Test
    void overlongQuizTitleThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "t".repeat(201), "d", false, List.of(qx("a", "Q")));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle(List.of(qe), null)), false));
    }

    @Test
    void nullQuestionEntryThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false,
                java.util.Collections.singletonList(null));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle(List.of(qe), null)), false));
    }

    @Test
    void blankQuestionTitleThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "")));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle(List.of(qe), null)), false));
    }

    @Test
    void overlongQuestionTitleThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "t".repeat(201))));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle(List.of(qe), null)), false));
    }

    @Test
    void unknownQuestionTypeThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport bad = new ExportBundle.QuestionExport("a", "BOGUS", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(bad));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle(List.of(qe), null)), false));
    }

    @Test
    void zeroTimeLimitThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport bad = new ExportBundle.QuestionExport("a", "MCQ", "Q", "d", 0, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(bad));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle(List.of(qe), null)), false));
    }

    @Test
    void negativePointsThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport bad = new ExportBundle.QuestionExport("a", "MCQ", "Q", "d", 30, -1, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(bad));
        assertThrows(IllegalArgumentException.class, () -> svc.importAll(Json.write(bundle(List.of(qe), null)), false));
    }

    @Test
    void duplicateQuizIdsThrow() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport a = new ExportBundle.QuizExport("dup", "A", "d", false, List.of(qx("qa", "Q")));
        ExportBundle.QuizExport b = new ExportBundle.QuizExport("dup", "B", "d", false, List.of(qx("qb", "Q")));
        assertThrows(IllegalArgumentException.class,
                () -> svc.importAll(Json.write(bundle(List.of(a, b), null)), false));
    }

    @Test
    void duplicateQuestionIdsAcrossQuizzesThrow() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport a = new ExportBundle.QuizExport("qa", "A", "d", false, List.of(qx("same", "Q")));
        ExportBundle.QuizExport b = new ExportBundle.QuizExport("qb", "B", "d", false, List.of(qx("same", "Q")));
        assertThrows(IllegalArgumentException.class,
                () -> svc.importAll(Json.write(bundle(List.of(a, b), null)), false));
    }

    @Test
    void blankSettingKeyThrows() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("a", "Q")));
        assertThrows(IllegalArgumentException.class,
                () -> svc.importAll(Json.write(bundle(List.of(qe), Map.of("  ", "v"))), false));
        assertFalse(rp.qr().findById("q1").isPresent());
    }

    @Test
    void nullQuestionsListImportsBareQuiz() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "Bare", "d", false, null);
        assertEquals(0, svc.importAll(Json.write(bundle(List.of(qe), null)), false));
        assertTrue(rp.qr().findById("q1").isPresent());
        assertTrue(rp.qnr().findByQuiz("q1").isEmpty());
    }

    @Test
    void templateFlagSurvivesImportAndExport() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("tpl", "Tpl", "d", true, List.of(qx("a", "Q")));
        svc.importAll(Json.write(bundle(List.of(qe), null)), false);
        assertTrue(rp.qr().findById("tpl").orElseThrow().template());
        ExportBundle back = Json.read(svc.exportAll(), ExportBundle.class);
        assertTrue(back.quizzes().stream().filter(z -> z.id().equals("tpl")).findFirst().orElseThrow().template());
    }

    @Test
    void languageListsRoundTripThroughExport() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport multi = new ExportBundle.QuestionExport(
                "m", "OJ_FULL", "OJ", "d", 60, 500, Map.of(), List.of("java", "python"));
        ExportBundle.QuestionExport none = new ExportBundle.QuestionExport(
                "n", "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(multi, none));
        svc.importAll(Json.write(bundle(List.of(qe), null)), false);
        ExportBundle back = Json.read(svc.exportAll(), ExportBundle.class);
        var qs = back.quizzes().get(0).questions();
        assertEquals(List.of("java", "python"),
                qs.stream().filter(z -> z.id().equals("m")).findFirst().orElseThrow().languagesAllowed());
        assertNull(qs.stream().filter(z -> z.id().equals("n")).findFirst().orElseThrow().languagesAllowed());
    }

    @Test
    void malformedStoredConfigDoesNotBreakExport() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        rp.qr().create(new Quiz("qz", "T", "d", "a", Instant.now(), false));
        rp.qnr().save(new Question("bad", "qz", "Q", "d", "MCQ", null, 30, 100, "{bad json", 0, Instant.now()));
        ExportBundle back = Json.read(svc.exportAll(), ExportBundle.class);
        assertTrue(back.quizzes().get(0).questions().get(0).config().isEmpty());
    }

    @Test
    void whitespaceIdsFallBackToUuid() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "  ", "MCQ", "Q", "d", 30, 100, Map.of(), null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("   ", "Ws", "d", false, List.of(ex));
        assertEquals(1, svc.importAll(Json.write(bundle(List.of(qe), null)), false));
        assertEquals(1, rp.qr().findAll().size());
        assertFalse(rp.qr().findAll().get(0).id().isBlank());
    }

    @Test
    void nullQuestionConfigImportsCleanly() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        ExportBundle.QuestionExport ex = new ExportBundle.QuestionExport(
                "nc", "MCQ", "Q", "d", 30, 100, null, null);
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(ex));
        assertEquals(1, svc.importAll(Json.write(bundle(List.of(qe), null)), false));
        assertEquals(1, rp.qnr().findByQuiz("q1").size());
    }

    @Test
    void multipleSettingsImportTogether() throws Exception {
        Repos rp = repos();
        ImportExportService svc = new ImportExportService(rp.qr(), rp.qnr(), rp.sr());
        Map<String, String> settings = new HashMap<>();
        settings.put("a", "1");
        settings.put("b", "2");
        settings.put("c", "3");
        ExportBundle.QuizExport qe = new ExportBundle.QuizExport("q1", "T", "d", false, List.of(qx("x", "Q")));
        svc.importAll(Json.write(bundle(List.of(qe), settings)), false);
        assertEquals("1", rp.sr().findByKey("a").orElseThrow().value());
        assertEquals("3", rp.sr().findByKey("c").orElseThrow().value());
    }
}
