package com.openquiz.controller;

import com.openquiz.domain.models.GameSession;
import com.openquiz.domain.models.Question;
import com.openquiz.domain.models.Quiz;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.QuizRepository;
import com.openquiz.service.GameRoomManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@CrossOrigin(originPatterns = "*")
public class PublicController {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final GameRoomManager roomManager;

    public PublicController(QuizRepository quizRepository, QuestionRepository questionRepository, GameRoomManager roomManager) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.roomManager = roomManager;
    }

    @GetMapping("/quizzes")
    public List<Quiz> listQuizzes() {
        return quizRepository.findAll();
    }

    @GetMapping("/quizzes/{id}")
    public Map<String, Object> getQuiz(@PathVariable String id) {
        Quiz quiz = quizRepository.findById(id).orElseThrow();
        List<Question> questions = questionRepository.findByQuiz(id);
        return Map.of("quiz", quiz, "questions", questions);
    }

    @PostMapping("/games")
    public GameSession createGame(@RequestBody Map<String, String> body) {
        String quizId = body.get("quizId");
        String hostUserId = body.getOrDefault("hostUserId", "system");
        return roomManager.createRoom(quizId, hostUserId);
    }
}
