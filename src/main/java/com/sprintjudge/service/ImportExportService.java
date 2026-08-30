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
            for (ExportBundle.QuestionExport ex : qe.questions()) {
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

    private ExportBundle.QuestionExport toExport(Question q) {
        Map<String, Object> config = q.config() == null || q.config().isBlank()
                ? new LinkedHashMap<>()
                : Json.readMap(q.config());
        return new ExportBundle.QuestionExport(q.id(), q.questionType(), q.title(), q.description(),
                q.timeLimitSec(), q.pointsBase(), config, q.languagesAllowed());
    }
}
