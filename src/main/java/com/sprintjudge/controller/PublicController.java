package com.sprintjudge.controller;

import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.domain.models.User;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.UserRepository;
import com.sprintjudge.service.GameRoom;
import com.sprintjudge.service.GameRoomManager;
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
    private final GameSessionRepository sessionRepository;
    private final CodeExecutor executor;
    private final UserRepository userRepository;
    private final GameRoomManager roomManager;

    /** Fixed-window per-IP rate limit for the live runner (abuse guard). */
    private final ConcurrentHashMap<String, long[]> runWindow = new ConcurrentHashMap<>();
    private static final int RUN_LIMIT_PER_MIN = 30;
    private final ConcurrentHashMap<String, long[]> practiceWindow = new ConcurrentHashMap<>();
    private static final int PRACTICE_LIMIT_PER_MIN = 10;
    private static final long WINDOW_MS = 60_000;
    private static final long STALE_MS = 120_000;

    public PublicController(QuizRepository quizRepository, GameSessionRepository sessionRepository,
                              CodeExecutor executor, UserRepository userRepository,
                              GameRoomManager roomManager) {
        this.quizRepository = quizRepository;
        this.sessionRepository = sessionRepository;
        this.executor = executor;
        this.userRepository = userRepository;
        this.roomManager = roomManager;
    }

    /** Shared fixed-window limiter; true when the caller is over budget. */
    private static boolean overLimit(ConcurrentHashMap<String, long[]> window, String ip, int limit) {
        long now = System.currentTimeMillis();
        long[] slot = window.compute(ip, (k, v) -> {
            if (v == null || now - v[0] > WINDOW_MS) {
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        });
        return slot[1] > limit;
    }

    /**
     * PIN-scoped quiz preview for the join flow. The full bank is never
     * listed anonymously — without a live PIN there is nothing to see.
     */
    @GetMapping("/quizzes")
    public List<Quiz> listQuizzes(@RequestParam(value = "pin", required = false) String pin) {
        if (pin == null || pin.isBlank()) return List.of();
        return sessionRepository.findByPin(pin.trim())
                .filter(s -> !"ENDED".equals(s.status()))
                .flatMap(s -> quizRepository.findById(s.quizId()))
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * Live code execution for the interactive console. Compiles + runs with the
     * supplied stdin and returns combined output. Rate-limited per IP.
     */
    @PostMapping("/run")
    public RunResult run(@Valid @RequestBody RunRequest request, HttpServletRequest http) {
        if (overLimit(runWindow, http.getRemoteAddr(), RUN_LIMIT_PER_MIN)) {
            log.warn("Rate limit exceeded for IP: {}", http.getRemoteAddr());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
        }
        return executor.run(request);
    }

    /**
     * One-click solo practice. Spins up a PRACTICE-mode room on the practice
     * set (first template quiz, else first quiz) with a server-side practice
     * host — no admin session involved, so players never touch /admin.
     */
    @PostMapping("/practice")
    public com.sprintjudge.domain.models.GameSession practice(HttpServletRequest http) {
        if (overLimit(practiceWindow, http.getRemoteAddr(), PRACTICE_LIMIT_PER_MIN)) {
            log.warn("Practice rate limit exceeded for IP: {}", http.getRemoteAddr());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
        }
        List<Quiz> quizzes = quizRepository.findAll();
        Quiz quiz = quizzes.stream().filter(q -> q.template()).findFirst()
                .or(() -> quizzes.stream().findFirst())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "no practice set yet"));
        User host = userRepository.upsertByEmail("practice@sprintjudge.local", "Practice", null);
        return roomManager.createRoom(quiz.id(), host.id(), GameRoom.GameMode.PRACTICE);
    }

    @Scheduled(fixedRate = 60_000)
    public void evictStaleRateLimits() {
        long now = System.currentTimeMillis();
        runWindow.entrySet().removeIf(e -> now - e.getValue()[0] > STALE_MS);
        practiceWindow.entrySet().removeIf(e -> now - e.getValue()[0] > STALE_MS);
    }
}
