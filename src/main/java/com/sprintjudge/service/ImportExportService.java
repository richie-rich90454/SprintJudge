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
            List<Question> questions = questionRepository.findByQuiz(q.getId());
            List<ExportBundle.QuestionExport> qExports = new ArrayList<>();
            for (Question qq : questions) {
                qExports.add(toExport(qq));
            }
            quizExports.add(new ExportBundle.QuizExport(
                    q.getId(), q.getTitle(), q.getDescription(), q.isTemplate(), qExports));
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
        if (bundle == null || bundle.getQuizzes() == null) {
            throw new IllegalArgumentException("Invalid import bundle: missing 'quizzes'");
        }
        if (bundle.getQuizzes().size() > 200) {
            throw new IllegalArgumentException("Import too large: max 200 quizzes");
        }
        if (replace && bundle.getQuizzes().isEmpty()) {
            throw new IllegalArgumentException("Replace import requires at least one quiz -?refusing to wipe the bank");
        }
        // Validate EVERYTHING before any delete so @Transactional atomicity is
        // honest: a bad row never wipes good data (even via rollback).
        for (ExportBundle.QuizExport qe : bundle.getQuizzes()) {
            validateQuiz(qe);
        }
        int totalQuestions = 0;
        for (ExportBundle.QuizExport qe : bundle.getQuizzes()) {
            totalQuestions += qe.getQuestions() == null ? 0 : qe.getQuestions().size();
        }
        if (totalQuestions > 5000) {
            throw new IllegalArgumentException("Import too large: max 5000 questions");
        }
        rejectCollisions(bundle, replace);
        if (bundle.getAdminSettings() != null) {
            for (Map.Entry<String, String> e : bundle.getAdminSettings().entrySet()) {
                // JSON objects cannot carry null keys, so only blank keys
                // and null values are rejected here.
                if (e.getKey().isBlank() || e.getValue() == null) {
                    throw new IllegalArgumentException("Invalid import bundle: setting keys must be non-blank and values non-null");
                }
            }
        }
        if (replace) {
            for (Quiz q : quizRepository.findAll()) {
                questionRepository.deleteByQuiz(q.getId());
                quizRepository.delete(q.getId());
            }
        }
        int count = 0;
        for (ExportBundle.QuizExport qe : bundle.getQuizzes()) {
            String quizId = qe.getId() != null && !qe.getId().isBlank() ? qe.getId() : Ids.uuid();
            quizRepository.create(new Quiz(quizId, qe.getTitle(), qe.getDescription(), null, Instant.now(), qe.isTemplate()));
            int order = 0;
            List<ExportBundle.QuestionExport> exports = qe.getQuestions() == null ? List.of() : qe.getQuestions();
            for (ExportBundle.QuestionExport ex : exports) {
                String qid = ex.getId() != null && !ex.getId().isBlank() ? ex.getId() : Ids.uuid();
                questionRepository.save(new Question(qid, quizId, ex.getTitle(), ex.getDescription(),
                        ex.getType(), ex.getLanguagesAllowed(), ex.getTimeLimitSec(), ex.getPointsBase(),
                        Json.write(ex.getConfig()), order++, Instant.now()));
                count++;
            }
        }
        if (bundle.getAdminSettings() != null) {
            importSettings(bundle.getAdminSettings());
        }
        return count;
    }

    private void importSettings(Map<String, String> settings) {
        for (Map.Entry<String, String> e : settings.entrySet()) {
            settingsRepository.put(e.getKey(), e.getValue());
        }
    }

    /**
     * A non-replace import must never silently overwrite bank rows or 500 on a
     * raw quiz INSERT: colliding or intra-bundle duplicate ids are rejected
     * up front (replace mode wipes the bank first, so only intra-bundle
     * duplicates matter there).
     */
    private void rejectCollisions(ExportBundle bundle, boolean replace) {
        java.util.Set<String> quizIds = new java.util.HashSet<>();
        java.util.Set<String> questionIds = new java.util.HashSet<>();
        for (ExportBundle.QuizExport qe : bundle.getQuizzes()) {
            String quizId = qe.getId() != null && !qe.getId().isBlank() ? qe.getId() : null;
            if (quizId != null) {
                if (!quizIds.add(quizId)) {
                    throw new IllegalArgumentException("Invalid import bundle: duplicate quiz id " + quizId);
                }
                if (!replace && quizRepository.findById(quizId).isPresent()) {
                    throw new IllegalArgumentException("Import would overwrite quiz " + quizId + " -?use replace mode");
                }
            }
            List<ExportBundle.QuestionExport> exports = qe.getQuestions() == null ? List.of() : qe.getQuestions();
            for (ExportBundle.QuestionExport ex : exports) {
                String qid = ex.getId() != null && !ex.getId().isBlank() ? ex.getId() : null;
                if (qid != null) {
                    if (!questionIds.add(qid)) {
                        throw new IllegalArgumentException("Invalid import bundle: duplicate question id " + qid);
                    }
                    if (!replace && questionRepository.findById(qid).isPresent()) {
                        throw new IllegalArgumentException("Import would overwrite question " + qid + " -?use replace mode");
                    }
                }
            }
        }
    }

    private void validateQuiz(ExportBundle.QuizExport qe) {
        if (qe == null) throw new IllegalArgumentException("Invalid import bundle: null quiz entry");
        if (qe.getTitle() == null || qe.getTitle().isBlank()) {
            throw new IllegalArgumentException("Quiz title must not be blank");
        }
        if (qe.getTitle().length() > 200) {
            throw new IllegalArgumentException("Quiz title exceeds 200 characters");
        }
        List<ExportBundle.QuestionExport> exports = qe.getQuestions() == null ? List.of() : qe.getQuestions();
        for (ExportBundle.QuestionExport ex : exports) {
            validateQuestion(ex);
        }
    }

    private void validateQuestion(ExportBundle.QuestionExport ex) {
        if (ex == null) throw new IllegalArgumentException("Invalid import bundle: null question entry");
        if (ex.getTitle() == null || ex.getTitle().isBlank()) {
            throw new IllegalArgumentException("Question title must not be blank");
        }
        if (ex.getTitle().length() > 200) {
            throw new IllegalArgumentException("Question title exceeds 200 characters");
        }
        // Throws IllegalArgumentException on unknown/blank type (maps to 400).
        com.sprintjudge.domain.enums.QuestionType.from(ex.getType());
        if (ex.getTimeLimitSec() < 1) {
            throw new IllegalArgumentException("Question timeLimitSec must be >= 1");
        }
        if (ex.getPointsBase() < 0) {
            throw new IllegalArgumentException("Question pointsBase must be >= 0");
        }
        // A bad config row must fail the import, never detonate mid-round.
        QuestionConfigValidator.requireValid(ex.getType(), Json.write(ex.getConfig()));
    }

    private ExportBundle.QuestionExport toExport(Question q) {
        Map<String, Object> config;
        try {
            config = q.getConfig() == null || q.getConfig().isBlank()
                    ? new LinkedHashMap<>()
                    : Json.readMap(q.getConfig());
        } catch (Exception e) {
            // ponytail: malformed config must not crash the entire export.
            config = new LinkedHashMap<>();
        }
        return new ExportBundle.QuestionExport(q.getId(), q.getQuestionType(), q.getTitle(), q.getDescription(),
                q.getTimeLimitSec(), q.getPointsBase(), config, q.getLanguagesAllowed());
    }
}
