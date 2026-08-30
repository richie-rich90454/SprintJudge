package com.sprintjudge.controller;

import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.service.executor.CodeExecutor;
import com.sprintjudge.service.executor.RunRequest;
import com.sprintjudge.service.executor.RunResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final CodeExecutor executor;

    /** Fixed-window per-IP rate limit for the live runner (abuse guard). */
    private final Map<String, AtomicInteger> runWindow = new ConcurrentHashMap<>();
    private static final int RUN_LIMIT_PER_MIN = 30;

    public PublicController(QuizRepository quizRepository, CodeExecutor executor) {
        this.quizRepository = quizRepository;
        this.executor = executor;
    }

    @GetMapping("/quizzes")
    public List<Quiz> listQuizzes() {
        return quizRepository.findAll();
    }

    /**
     * Live code execution for the interactive console. Compiles + runs with the
     * supplied stdin and returns combined output. Rate-limited per IP.
     */
    @PostMapping("/run")
    public RunResult run(@RequestBody RunRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        AtomicInteger used = runWindow.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (used.incrementAndGet() > RUN_LIMIT_PER_MIN) {
            return new RunResult(false, "", "", "rate_limited");
        }
        return executor.run(request);
    }
}
