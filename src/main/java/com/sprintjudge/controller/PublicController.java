package com.openquiz.controller;

import com.openquiz.domain.models.Quiz;
import com.openquiz.repository.QuizRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Deliberately minimal public surface. Question payloads (which embed answer
 * keys in their config) are NEVER exposed here — they are admin-only, so a
 * player cannot fetch correct answers before a round.
 */
@RestController
@RequestMapping("/api/public")
@CrossOrigin(originPatterns = "*")
public class PublicController {

    private final QuizRepository quizRepository;

    public PublicController(QuizRepository quizRepository) {
        this.quizRepository = quizRepository;
    }

    @GetMapping("/quizzes")
    public List<Quiz> listQuizzes() {
        return quizRepository.findAll();
    }
}
