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
}
