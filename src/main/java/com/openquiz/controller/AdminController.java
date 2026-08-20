package com.openquiz.controller;

import com.openquiz.domain.models.Question;
import com.openquiz.domain.models.Quiz;
import com.openquiz.repository.AdminSettingsRepository;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.QuizRepository;
import com.openquiz.service.AdminSettingsService;
import com.openquiz.service.ImportExportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(originPatterns = "*")
public class AdminController {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AdminSettingsRepository settingsRepository;
    private final AdminSettingsService settingsService;
    private final ImportExportService importExportService;

    public AdminController(QuizRepository quizRepository, QuestionRepository questionRepository,
                           AdminSettingsRepository settingsRepository, AdminSettingsService settingsService,
                           ImportExportService importExportService) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.settingsRepository = settingsRepository;
        this.settingsService = settingsService;
        this.importExportService = importExportService;
    }

    @GetMapping("/quizzes")
    public List<Quiz> quizzes() {
        return quizRepository.findAll();
    }

    @PostMapping("/quizzes")
    public Quiz createQuiz(@RequestBody Quiz quiz) {
        return quizRepository.create(quiz);
    }

    @DeleteMapping("/quizzes/{id}")
    public void deleteQuiz(@PathVariable String id) {
        quizRepository.delete(id);
    }

    @GetMapping("/quizzes/{id}/questions")
    public List<Question> questions(@PathVariable String id) {
        return questionRepository.findByQuiz(id);
    }

    @PostMapping("/quizzes/{id}/questions")
    public Question addQuestion(@PathVariable String id, @RequestBody Question question) {
        Question q = new Question(question.id(), id, question.title(), question.description(),
                question.questionType(), question.languagesAllowed(), question.timeLimitSec(),
                question.pointsBase(), question.config(), question.orderIndex(), question.createdAt());
        return questionRepository.save(q);
    }

    @PutMapping("/questions/{id}")
    public Question updateQuestion(@PathVariable String id, @RequestBody Question question) {
        return questionRepository.save(question);
    }

    @DeleteMapping("/questions/{id}")
    public void deleteQuestion(@PathVariable String id) {
        questionRepository.delete(id);
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return settingsService.asMap();
    }

    @PutMapping("/settings")
    public void updateSettings(@RequestBody Map<String, String> body) {
        body.forEach(settingsService::set);
    }

    @GetMapping("/export")
    public String exportBank() {
        return importExportService.exportAll();
    }

    @PostMapping("/import")
    public Map<String, Object> importBank(@RequestBody Map<String, Object> body) {
        String json = (String) body.get("json");
        boolean replace = Boolean.TRUE.equals(body.get("replace"));
        int imported = importExportService.importAll(json, replace);
        return Map.of("importedQuestions", imported);
    }
}
