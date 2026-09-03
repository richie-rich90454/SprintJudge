package com.sprintjudge.service;

import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.AdminSettingsRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-file question-bank import / export. The full bank is one JSON document.
 */
@Service
public class ImportExportService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AdminSettingsRepository settingsRepository;

    public ImportExportService(QuizRepository quizRepository,
                               QuestionRepository questionRepository,
                               AdminSettingsRepository settingsRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.settingsRepository = settingsRepository;
    }

    public String exportAll() {
        List<Quiz> quizzes = quizRepository.findAll();
        List<ExportBundle.QuizExport> quizExports = new ArrayList<>();
        for (Quiz q : quizzes) {
            List<Question> questions = questionRepository.findByQuiz(q.id());
            List<ExportBundle.QuestionExport> qExports = questions.stream().map(this::toExport).toList();
            quizExports.add(new ExportBundle.QuizExport(
                    q.id(), q.title(), q.description(), q.template(), qExports));
        }
        Map<String, String> settings = settingsRepository.findAllAsMap();
        ExportBundle bundle = new ExportBundle("1.0", Instant.now().getEpochSecond(), quizExports, settings);
        return Json.write(bundle);
    }

    @Transactional
    public int importAll(String json, boolean replace) {
        ExportBundle bundle = Json.read(json, ExportBundle.class);
        // Null-safe: malformed shapes are BAD_REQUEST (IAE), never NPE-500.
        // (Unparseable JSON still throws IllegalStateException from Json.read.)
        if (bundle == null || bundle.quizzes() == null) {
            throw new IllegalArgumentException("Invalid import bundle: missing 'quizzes'");
        }
        if (replace && bundle.quizzes().isEmpty()) {
            throw new IllegalArgumentException("Replace import requires at least one quiz — refusing to wipe the bank");
        }
        // Validate EVERYTHING before any delete so @Transactional atomicity is
        // honest: a bad row never wipes good data (even via rollback).
        for (ExportBundle.QuizExport qe : bundle.quizzes()) {
            validateQuiz(qe);
        }
        if (replace) {
            for (Quiz q : quizRepository.findAll()) {
                questionRepository.deleteByQuiz(q.id());
                quizRepository.delete(q.id());
            }
        }
        int count = 0;
        for (ExportBundle.QuizExport qe : bundle.quizzes()) {
            String quizId = qe.id() != null && !qe.id().isBlank() ? qe.id() : Ids.uuid();
            quizRepository.create(new Quiz(quizId, qe.title(), qe.description(), null, Instant.now(), qe.template()));
            int order = 0;
            List<ExportBundle.QuestionExport> exports = qe.questions() == null ? List.of() : qe.questions();
            for (ExportBundle.QuestionExport ex : exports) {
                String qid = ex.id() != null && !ex.id().isBlank() ? ex.id() : Ids.uuid();
                questionRepository.save(new Question(qid, quizId, ex.title(), ex.description(),
                        ex.type(), ex.languagesAllowed(), ex.timeLimitSec(), ex.pointsBase(),
                        Json.write(ex.config()), order++, Instant.now()));
                count++;
            }
        }
        if (bundle.adminSettings() != null) {
            importSettings(bundle.adminSettings());
        }
        return count;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importSettings(Map<String, String> settings) {
        settings.forEach(settingsRepository::put);
    }

    private void validateQuiz(ExportBundle.QuizExport qe) {
        if (qe == null) throw new IllegalArgumentException("Invalid import bundle: null quiz entry");
        if (qe.title() == null || qe.title().isBlank()) {
            throw new IllegalArgumentException("Quiz title must not be blank");
        }
        if (qe.title().length() > 200) {
            throw new IllegalArgumentException("Quiz title exceeds 200 characters");
        }
        List<ExportBundle.QuestionExport> exports = qe.questions() == null ? List.of() : qe.questions();
        for (ExportBundle.QuestionExport ex : exports) {
            validateQuestion(ex);
        }
    }

    private void validateQuestion(ExportBundle.QuestionExport ex) {
        if (ex == null) throw new IllegalArgumentException("Invalid import bundle: null question entry");
        if (ex.title() == null || ex.title().isBlank()) {
            throw new IllegalArgumentException("Question title must not be blank");
        }
        if (ex.title().length() > 200) {
            throw new IllegalArgumentException("Question title exceeds 200 characters");
        }
        // Throws IllegalArgumentException on unknown/blank type (maps to 400).
        com.sprintjudge.domain.enums.QuestionType.from(ex.type());
        if (ex.timeLimitSec() < 1) {
            throw new IllegalArgumentException("Question timeLimitSec must be >= 1");
        }
        if (ex.pointsBase() < 0) {
            throw new IllegalArgumentException("Question pointsBase must be >= 0");
        }
    }

    private ExportBundle.QuestionExport toExport(Question q) {
        Map<String, Object> config;
        try {
            config = q.config() == null || q.config().isBlank()
                    ? new LinkedHashMap<>()
                    : Json.readMap(q.config());
        } catch (Exception e) {
            // ponytail: malformed config must not crash the entire export.
            config = new LinkedHashMap<>();
        }
        return new ExportBundle.QuestionExport(q.id(), q.questionType(), q.title(), q.description(),
                q.timeLimitSec(), q.pointsBase(), config, q.languagesAllowed());
    }
}
