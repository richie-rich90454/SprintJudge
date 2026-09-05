package com.sprintjudge.controller;

import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.service.AdminSettingsService;
import com.sprintjudge.service.ImportExportService;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AdminSettingsService settingsService;
    private final ImportExportService importExportService;
    private final com.sprintjudge.repository.UserRepository userRepository;
    private final com.sprintjudge.service.GameRoomManager roomManager;
    private final com.sprintjudge.service.MetricsService metricsService;

    public AdminController(QuizRepository quizRepository, QuestionRepository questionRepository,
                           AdminSettingsService settingsService,
                           ImportExportService importExportService,
                           com.sprintjudge.repository.UserRepository userRepository,
                           com.sprintjudge.service.GameRoomManager roomManager,
                           com.sprintjudge.service.MetricsService metricsService) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.settingsService = settingsService;
        this.importExportService = importExportService;
        this.userRepository = userRepository;
        this.roomManager = roomManager;
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metricsService.snapshot();
    }

    @GetMapping("/quizzes")
    public List<Quiz> quizzes() {
        return quizRepository.findAll();
    }

    @PostMapping("/quizzes")
    public Quiz createQuiz(@Valid @RequestBody Quiz quiz) {
        if (quiz.id() != null && quizRepository.findById(quiz.id()).isPresent()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Quiz id already exists");
        }
        return quizRepository.create(quiz);
    }

    @PutMapping("/quizzes/{id}")
    public Quiz updateQuiz(@PathVariable String id, @RequestBody Map<String, String> body) {
        var existing = quizRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Quiz not found"));
        String title = body.getOrDefault("title", existing.title());
        if (title == null || title.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Title must not be blank");
        }
        if (title.length() > 200) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Title exceeds 200 characters");
        }
        String description = body.getOrDefault("description", existing.description());
        if (description != null && description.length() > 4000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Description exceeds 4000 characters");
        }
        com.sprintjudge.domain.models.Quiz updated = new com.sprintjudge.domain.models.Quiz(
                id, title, description, existing.createdBy(), existing.createdAt(), existing.template());
        return quizRepository.update(updated);
    }

    @DeleteMapping("/quizzes/{id}")
    public org.springframework.http.ResponseEntity<Void> deleteQuiz(@PathVariable String id) {
        quizRepository.delete(id);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping("/quizzes/{id}/questions")
    public List<Question> questions(@PathVariable String id) {
        return questionRepository.findByQuiz(id);
    }

    @PostMapping("/quizzes/{id}/questions")
    public Question addQuestion(@PathVariable String id, @Valid @RequestBody Question question) {
        requireKnownType(question.questionType());
        Question q = new Question(question.id(), id, question.title(), question.description(),
                question.questionType(), question.languagesAllowed(), question.timeLimitSec(),
                question.pointsBase(), question.config(), question.orderIndex(), question.createdAt());
        return questionRepository.save(q);
    }

    @PutMapping("/questions/{id}")
    public Question updateQuestion(@PathVariable String id, @Valid @RequestBody Question question) {
        Question existing = questionRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Question not found"));
        requireKnownType(question.questionType());
        // The quiz home comes from the stored record, never the client body.
        Question q = new Question(id, existing.quizId(), question.title(), question.description(),
                question.questionType(), question.languagesAllowed(), question.timeLimitSec(),
                question.pointsBase(), question.config(), question.orderIndex(), question.createdAt());
        return questionRepository.save(q);
    }

    private static void requireKnownType(String type) {
        try {
            com.sprintjudge.domain.enums.QuestionType.from(type);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown question type: " + type);
        }
    }

    @DeleteMapping("/questions/{id}")
    public org.springframework.http.ResponseEntity<Void> deleteQuestion(@PathVariable String id) {
        questionRepository.delete(id);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return settingsService.asMap();
    }

    /**
     * Host attribution is resolved server-side from the authenticated login
     * session — the client never gets to declare who the host is.
     */
    @PostMapping("/games")
    public com.sprintjudge.domain.models.GameSession createGame(@RequestBody Map<String, String> body) {
        String quizId = body.get("quizId");
        if (quizId == null || quizId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "quizId is required");
        }
        String gameModeStr = body.getOrDefault("gameMode", "STANDARD");
        com.sprintjudge.service.GameRoom.GameMode gameMode;
        try {
            gameMode = com.sprintjudge.service.GameRoom.GameMode.valueOf(gameModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown gameMode: " + gameModeStr);
        }
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = "system@sprintjudge.local";
        String name = "System";
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth) {
            String e = oauth.<String>getAttribute("email");
            if (e != null && !e.isBlank()) email = e;
            String n = oauth.<String>getAttribute("name");
            if (n != null && !n.isBlank()) name = n;
        } else if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails user) {
            email = user.getUsername();
            name = user.getUsername();
        } else if (auth != null && auth.getPrincipal() instanceof String s && !s.isBlank()) {
            email = s;
            name = s;
        }
        com.sprintjudge.domain.models.User host = userRepository.upsertByEmail(email, name, null);
        return roomManager.createRoom(quizId, host.id(), gameMode);
    }

    @PutMapping("/settings")
    public void updateSettings(@RequestBody Map<String, String> body) {
        body.forEach((k, v) -> {
            if (v == null) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Setting value must not be null: " + k);
            }
            settingsService.set(k, v);
        });
    }

    @GetMapping("/export")
    public String exportBank() {
        return importExportService.exportAll();
    }

    @PostMapping("/import")
    public Map<String, Object> importBank(@RequestBody Map<String, Object> body) {
        Object raw = body.get("json");
        if (!(raw instanceof String json) || json.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Missing or invalid 'json' field");
        }
        boolean replace = Boolean.TRUE.equals(body.get("replace"))
                || (body.get("replace") instanceof String s && s.equalsIgnoreCase("true"));
        int imported = importExportService.importAll(json, replace);
        if (imported < 0) imported = 0;
        return Map.of("importedQuestions", imported);
    }
}
