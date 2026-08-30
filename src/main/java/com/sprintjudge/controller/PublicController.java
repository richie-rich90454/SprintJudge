package com.sprintjudge.controller;

import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.service.executor.CodeExecutor;
import com.sprintjudge.service.executor.RunRequest;
import com.sprintjudge.service.executor.RunResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Iterator;
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
public class PublicController {

    private final QuizRepository quizRepository;
    private final CodeExecutor executor;

    /** Fixed-window per-IP rate limit for the live runner (abuse guard). */
    private final Map<String, long[]> runWindow = new ConcurrentHashMap<>();
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
    public RunResult run(@RequestBody RunRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        long now = System.currentTimeMillis();
        synchronized (runWindow) {
            Iterator<Map.Entry<String, long[]>> it = runWindow.entrySet().iterator();
            while (it.hasNext()) {
                long[] w = it.next().getValue();
                if (now - w[0] > STALE_MS) it.remove();
            }
            long[] window = runWindow.computeIfAbsent(ip, k -> new long[]{0, 0});
            if (now - window[0] > WINDOW_MS) {
                window[0] = now;
                window[1] = 0;
            }
            if (window[1] >= RUN_LIMIT_PER_MIN) {
                return new RunResult(false, "", "", "rate_limited");
            }
            window[1]++;
        }
        return executor.run(request);
    }
}
