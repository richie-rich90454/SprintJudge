package com.sprintjudge.controller;

import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.service.executor.CodeExecutor;
import com.sprintjudge.service.executor.RunRequest;
import com.sprintjudge.service.executor.RunResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deliberately minimal public surface. Question payloads (which embed answer
 * keys in their config) are NEVER exposed here — they are admin-only, so a
 * player cannot fetch correct answers before a round.
 */
@RestController
@RequestMapping("/api/public")
@EnableScheduling
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    private final QuizRepository quizRepository;
    private final CodeExecutor executor;

    /** Fixed-window per-IP rate limit for the live runner (abuse guard). */
    private final ConcurrentHashMap<String, long[]> runWindow = new ConcurrentHashMap<>();
    private static final int RUN_LIMIT_PER_MIN = 30;
    private static final long WINDOW_MS = 60_000;
    private static final long STALE_MS = 120_000;

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
    public RunResult run(@Valid @RequestBody RunRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        long now = System.currentTimeMillis();
        long[] window = runWindow.compute(ip, (k, v) -> {
            if (v == null || now - v[0] > WINDOW_MS) {
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        });
        if (window[1] > RUN_LIMIT_PER_MIN) {
            log.warn("Rate limit exceeded for IP: {}", ip);
            return new RunResult(false, "", "", "rate_limited");
        }
        return executor.run(request);
    }

    @Scheduled(fixedRate = 60_000)
    public void evictStaleRateLimits() {
        long now = System.currentTimeMillis();
        runWindow.entrySet().removeIf(e -> now - e.getValue()[0] > STALE_MS);
    }
}
