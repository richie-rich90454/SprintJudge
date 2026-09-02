package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.executor.CodeExecutor;
import com.sprintjudge.service.executor.JudgeRequest;
import com.sprintjudge.service.executor.JudgeResult;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Async judge pipeline. Returns {@code false} only when the concurrency
 * budget is saturated so the caller can answer with a friendly retry the
 * queue never blocks a WebSocket thread.
 *
 * <p>Attempt-cap and language enforcement happen in the caller
 * ({@link GameRoomManager}); this stage judges and reports the outcome back
 * through {@link CodingOutcomeConsumer}, which applies the score to the room
 * leaderboard (the OJ pipeline used to award zero visible points).
 */
@Service
public class SubmissionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SubmissionProcessor.class);
    private static final int MAX_SOURCE_CHARS = 65_536;

    private final CodeExecutor executor;
    private final SubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;
    private final ScoringEngine scoringEngine;
    private final LeaderboardBroadcaster leaderboardBroadcaster;
    private final SubmissionWriteBuffer writeBuffer;
    private final AiGradingService aiGradingService;
    private final Semaphore slot;

    public SubmissionProcessor(CodeExecutor executor,
                                SubmissionRepository submissionRepository,
                                QuestionRepository questionRepository,
                                ScoringEngine scoringEngine,
                                LeaderboardBroadcaster leaderboardBroadcaster,
                                SubmissionWriteBuffer writeBuffer,
                                AiGradingService aiGradingService,
                                Semaphore executionSlots) {
        this.executor = executor;
        this.submissionRepository = submissionRepository;
        this.questionRepository = questionRepository;
        this.scoringEngine = scoringEngine;
        this.leaderboardBroadcaster = leaderboardBroadcaster;
        this.writeBuffer = writeBuffer;
        this.aiGradingService = aiGradingService;
        this.slot = executionSlots;
    }

    @Async("virtualThreadExecutor")
    public CompletableFuture<Boolean> processCoding(String sessionId, String pin, String questionId, String playerName,
                                  String playerUuid, String language, String sourceCode, int attemptsUsed,
                                  Map<String, Object> settings, long timeTakenSec, CodingOutcomeConsumer handler) {
        // Edge case Y companion: never block the caller on a saturated judge.
        if (!slot.tryAcquire()) {
            return CompletableFuture.completedFuture(false);
        }
        JudgeResult result = null;
        try {
            result = judge(sessionId, questionId, playerName, playerUuid, language, sourceCode,
                    attemptsUsed, settings, timeTakenSec, handler);
            return CompletableFuture.completedFuture(true);
        } finally {
            slot.release();
            if (result != null) {
                leaderboardBroadcaster.broadcastLeaderboard(pin);
            }
        }
    }

    private JudgeResult judge(String sessionId, String questionId, String playerName,
                               String playerUuid, String language, String sourceCode,
                               int attemptsUsed, Map<String, Object> settings,
                               long timeTakenSec, CodingOutcomeConsumer handler) {
        Question question = questionRepository.findById(questionId).orElse(null);
        if (question == null) return null;
        QuestionType type = QuestionType.from(question.questionType());

        // Async-boundary guard: misrouted selection answers must never reach tools.
        if (!type.isCoding()) {
            recordRejected(sessionId, questionId, playerName, playerUuid, "not_a_coding_question");
            return new JudgeResult(0, 0, false, List.of());
        }

        // Defense-in-depth: the WS layer already caps this; enforce again here.
        if (sourceCode == null || sourceCode.length() > MAX_SOURCE_CHARS) {
            recordRejected(sessionId, questionId, playerName, playerUuid,
                    sourceCode == null ? "source_missing" : "source_too_large");
            return new JudgeResult(0, 0, false, List.of());
        }

        JsonNode config = Json.readTree(question.config());
        List<com.sprintjudge.service.executor.TestCase> cases = new ArrayList<>();
        if (config.has("testCases")) {
            for (JsonNode tc : config.get("testCases")) {
                cases.add(new com.sprintjudge.service.executor.TestCase(
                        tc.path("input").asText(""),
                        tc.path("expectedOutput").asText(""),
                        tc.path("isHidden").asBoolean(false)));
            }
        }
        int memMb = config.path("memoryLimitMb").asInt(256);
        int timeout = Math.max(5, question.timeLimitSec());

        JudgeRequest req = new JudgeRequest(language, sourceCode, cases, timeout, memMb);
        JudgeResult result = executor.judge(req);

        int score = scoringEngine.scoreCoding(
                result.passed(), result.total(), question.pointsBase(),
                result.allPassed(), timeTakenSec, question.timeLimitSec(), attemptsUsed, settings);

        Submission best = submissionRepository.findBest(sessionId, questionId, playerUuid).orElse(null);
        if (best == null || score > best.scoreEarned()) {
            writeBuffer.offer(new Submission(
                    Ids.uuid(), sessionId, questionId, playerName, playerUuid,
                    Json.write(Map.of("language", language, "source", sourceCode)),
                    score, result.allPassed(), Json.write(result), attemptsUsed, Instant.now()));
        }

        // AI grading: when tests fail and AI is enabled, get feedback.
        String aiFeedback = null;
        if (!result.allPassed() && aiGradingService.isEnabled()) {
            try {
                var aiResult = aiGradingService.grade(language, sourceCode,
                        question.title(), question.description(),
                        result.allPassed(), score, question.pointsBase());
                if (aiResult.available()) aiFeedback = aiResult.feedback();
            } catch (Exception e) {
                log.debug("AI grading failed for question {}: {}", questionId, e.getMessage());
            }
        }

        handler.accept(playerUuid, score, result.allPassed(), result.passed(), result.total(), aiFeedback);
        return result;
    }

    private void recordRejected(String sessionId, String questionId, String playerName,
                                 String playerUuid, String reason) {
        writeBuffer.offer(new Submission(Ids.uuid(), sessionId, questionId, playerName,
                playerUuid, Json.write(Map.of("rejected", reason)),
                0, false, reason, 1, Instant.now()));
    }
}
