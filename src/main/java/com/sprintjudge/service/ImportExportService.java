package com.openquiz.service;

import com.openquiz.domain.dto.export.ExportBundle;
import com.openquiz.domain.models.Question;
import com.openquiz.domain.models.Quiz;
import com.openquiz.repository.AdminSettingsRepository;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.QuizRepository;
import com.openquiz.util.Ids;
import com.openquiz.util.Json;
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
            bundle.adminSettings().forEach(settingsRepository::put);
        }
        return count;
    }

    private ExportBundle.QuestionExport toExport(Question q) {
        Map<String, Object> config = q.config() == null || q.config().isBlank()
                ? new LinkedHashMap<>()
                : Json.readMap(q.config());
        return new ExportBundle.QuestionExport(q.id(), q.questionType(), q.title(), q.description(),
                q.timeLimitSec(), q.pointsBase(), config, q.languagesAllowed());
    }
}
