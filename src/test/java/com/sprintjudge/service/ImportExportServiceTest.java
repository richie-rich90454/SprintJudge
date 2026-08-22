package com.sprintjudge.service;

import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.AdminSettingsRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportExportServiceTest {

    @Mock QuizRepository quizRepository;
    @Mock QuestionRepository questionRepository;
    @Mock AdminSettingsRepository settingsRepository;

    private ImportExportService service() {
        return new ImportExportService(quizRepository, questionRepository, settingsRepository);
    }

    private Quiz quiz(String id) {
        return new Quiz(id, "Algorithms 101", "Intro", "admin", Instant.now(), false);
    }

    private Question question(String id, String type, String config) {
        return new Question(id, "qz", "T" + id, "D", type, List.of("python"), 45, 250,
                Json.write(Map.of("correctIndex", 1)), 0, Instant.now());
    }

    // ---------- export ----------

    @Test
    void exportProducesVersionedBundleWithQuizzesAndQuestions() {
        when(quizRepository.findAll()).thenReturn(List.of(quiz("qz")));
        when(questionRepository.findByQuiz("qz"))
                .thenReturn(List.of(question("q1", "MCQ", "{\"correctIndex\":1}")));
        when(settingsRepository.findAllAsMap()).thenReturn(Map.of("default_time_limit", "60"));

        String json = service().exportAll();
        ExportBundle bundle = Json.read(json, ExportBundle.class);

        assertEquals("1.0", bundle.version());
        assertEquals(1, bundle.quizzes().size());
        assertEquals("Algorithms 101", bundle.quizzes().get(0).title());
        ExportBundle.QuestionExport q = bundle.quizzes().get(0).questions().get(0);
        assertEquals("MCQ", q.type());
        assertEquals(45, q.timeLimitSec());
        assertEquals(250, q.pointsBase());
        assertEquals(List.of("python"), q.languagesAllowed());
        assertEquals(1, ((Number) q.config().get("correctIndex")).intValue());
        assertTrue(bundle.exportedAt() > 0);
    }

    @Test
    void exportWithEmptyBankIsStillValidJson() {
        when(quizRepository.findAll()).thenReturn(List.of());
        ExportBundle bundle = Json.read(service().exportAll(), ExportBundle.class);
        assertTrue(bundle.quizzes().isEmpty());
    }

    @Test
    void exportBlankQuestionConfigYieldsEmptyMapNotGarbage() {
        when(quizRepository.findAll()).thenReturn(List.of(quiz("qz")));
        when(questionRepository.findByQuiz("qz")).thenAnswer(inv -> List.of(
                new Question("e1", "qz", "E", "", "MCQ", null, 30, 100, "", 0, null),
                new Question("e2", "qz", "E2", "", "MCQ", null, 30, 100, null, 1, null)));
        ExportBundle bundle = Json.read(service().exportAll(), ExportBundle.class);
        assertEquals(0, bundle.quizzes().get(0).questions().get(0).config().size());
        assertEquals(0, bundle.quizzes().get(0).questions().get(1).config().size());
    }

    // ---------- import ----------

    private String bankJson(boolean withSettings) {
        String settings = withSettings ? ",\"adminSettings\":{\"mcq_max_attempts\":\"2\"}" : "";
        return """
        {"version":"1.0","exportedAt":123,"quizzes":[{"id":"qzX","title":"Imported",
          "description":"","questions":[
            {"id":"n1","type":"MCQ","title":"Q1","description":"","timeLimitSec":20,
             "pointsBase":100,"config":{"correctIndex":0},"languagesAllowed":null},
            {"id":"n2","type":"NUMERIC","title":"Q2","description":"","timeLimitSec":20,
             "pointsBase":100,"config":{"answer":7},"languagesAllowed":null}]}]%s}
        """.formatted(settings);
    }

    @Test
    void importReplaceDeletesExistingQuizzesFirst() {
        when(quizRepository.findAll()).thenReturn(List.of(quiz("old-1"), quiz("old-2")));

        int imported = service().importAll(bankJson(false), true);

        assertEquals(2, imported);
        verify(quizRepository).delete("old-1");
        verify(quizRepository).delete("old-2");
        verify(quizRepository).create(any(Quiz.class));
    }

    @Test
    void importMergeKeepsExistingQuizzes() {
        service().importAll(bankJson(false), false);
        verify(quizRepository, times(0)).delete(anyString());
    }

    @Test
    void importedQuestionsCarrySequentialOrderIndexes() {
        when(quizRepository.findAll()).thenReturn(List.of());

        service().importAll(bankJson(false), true);

        ArgumentCaptor<Question> cap = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(2)).save(cap.capture());
        assertEquals(0, cap.getAllValues().get(0).orderIndex());
        assertEquals(1, cap.getAllValues().get(1).orderIndex());
        assertEquals("qzX", cap.getAllValues().get(0).quizId());
    }

    @Test
    void importAppliesBundledAdminSettings() {
        when(quizRepository.findAll()).thenReturn(List.of());

        service().importAll(bankJson(true), true);

        verify(settingsRepository).put("mcq_max_attempts", "2");
    }

    @Test
    void importMalformedJsonThrowsWithoutSideEffects() {
        assertThrows(Exception.class, () -> service().importAll("{broken", true));
        verify(quizRepository, times(0)).delete(anyString());
        verify(questionRepository, times(0)).save(any(Question.class));
    }
}
