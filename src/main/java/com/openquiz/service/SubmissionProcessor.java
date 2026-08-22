package com.openquiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.openquiz.domain.enums.QuestionType;
import com.openquiz.domain.models.Question;
import com.openquiz.domain.models.Submission;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.SubmissionRepository;
import com.openquiz.service.executor.CodeExecutor;
import com.openquiz.service.executor.JudgeRequest;
import com.openquiz.service.executor.JudgeResult;
import com.openquiz.util.Ids;
import com.openquiz.util.Json;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Async judge pipeline. Returns {@code false} only when the concurrency
 * budget is saturated so the caller can answer with a friendly retry —
 * the queue never blocks a WebSocket thread.
 */
@Service
public class SubmissionProcessor {

    private static final int MAX_SOURCE_CHARS = 65_536;
    private static final int MAX_ATTEMPTS_PER_QUESTION = 50;

    private final CodeExecutor executor;
    private final SubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;
    private final ScoringEngine scoringEngine;
    private final GameRoomManager roomManager;
    private final SubmissionWriteBuffer writeBuffer;
    private final Semaphore slot;

    public SubmissionProcessor(CodeExecutor executor,
                               SubmissionRepository submissionRepository,
                               QuestionRepository questionRepository,
                               ScoringEngine scoringEngine,
                               GameRoomManager roomManager,
                               SubmissionWriteBuffer writeBuffer,
                               Semaphore executionSlots) {
        this.executor = executor;
        this.submissionRepository = submissionRepository;
        this.questionRepository = questionRepository;
        this.scoringEngine = scoringEngine;
        this.roomManager = roomManager;
        this.writeBuffer = writeBuffer;
        this.slot = executionSlots;
    }

    @Async("virtualThreadExecutor")
    public boolean processCoding(String sessionId, String questionId, String playerName,
                                 String playerUuid, String language, String sourceCode,
                                 Map<String, Object> settings) {
        // Edge case Y companion: never block the caller on a saturated judge.
        if (!slot.tryAcquire()) {
            return false;
        }
        JudgeResult result = null;
        try {
            result = judge(sessionId, questionId, playerName, playerUuid, language, sourceCode, settings);
            return true;
        } finally {
            slot.release();
            if (result != null) {
                roomManager.broadcastLeaderboard(publishableSessionId(sessionId));
            }
        }
    }

    private String publishableSessionId(String sessionId) {
        // Session ids are room-internal; leaderboard fan-out is pin-keyed by the manager.
        return sessionId;
    }

    private JudgeResult judge(String sessionId, String questionId, String playerName,
                              String playerUuid, String language, String sourceCode,
                              Map<String, Object> settings) {
        Question question = questionRepository.findById(questionId).orElse(null);
        if (question == null) return null;
        QuestionType type = QuestionType.from(question.questionType());

        // Async-boundary guard: misrouted selection answers must never reach tools.
        if (!type.isCoding()) {
            recordRejected(sessionId, questionId, playerName, playerUuid, "not_a_coding_question");
            return new JudgeResult(0, 0, false, List.of());
        }

        // Defense-in-depth: the WS layer already caps this; enforce again here.
        if (sourceCode != null && sourceCode.length() > MAX_SOURCE_CHARS) {
            recordRejected(sessionId, questionId, playerName, playerUuid, "source_too_large");
            return new JudgeResult(0, 0, false, List.of());
        }

        // Bound the judge queue: hard cap on attempts per player per question.
        int priorAttempts = submissionRepository.findBySessionQuestion(sessionId, questionId).stream()
                .filter(s -> s.playerUuid().equals(playerUuid))
                .mapToInt(s -> s.attemptCount()).sum();
        if (priorAttempts >= MAX_ATTEMPTS_PER_QUESTION) {
            return null;
        }

        JsonNode config = Json.readTree(question.config());
        List<com.openquiz.service.executor.TestCase> cases = new ArrayList<>();
        if (config.has("testCases")) {
            for (JsonNode tc : config.get("testCases")) {
                cases.add(new com.openquiz.service.executor.TestCase(
                        tc.path("input").asText(""),
                        tc.path("expectedOutput").asText(""),
                        tc.path("isHidden").asBoolean(false)));
            }
        }
        int memMb = config.path("memoryLimitMb").asInt(256);
        int timeout = Math.max(5, question.timeLimitSec());

        JudgeRequest req = new JudgeRequest(language, sourceCode, cases, timeout, memMb);
        JudgeResult result = executor.judge(req);

        int attempts = priorAttempts + 1;
        int score = scoringEngine.scoreCoding(
                result.passed(), result.total(), question.pointsBase(),
                result.allPassed(), 0, question.timeLimitSec(), attempts, settings);

        Submission best = submissionRepository.findBest(sessionId, questionId, playerUuid).orElse(null);
        if (best == null || score > best.scoreEarned()) {
            writeBuffer.offer(new Submission(
                    Ids.uuid(), sessionId, questionId, playerName, playerUuid,
                    Json.write(Map.of("language", language, "source", sourceCode)),
                    score, result.allPassed(), Json.write(result), attempts, Instant.now()));
        }
        return result;
    }

    private void recordRejected(String sessionId, String questionId, String playerName,
                                String playerUuid, String reason) {
        writeBuffer.offer(new Submission(Ids.uuid(), sessionId, questionId, playerName,
                playerUuid, Json.write(Map.of("rejected", reason)),
                0, false, reason, 1, Instant.now()));
    }
}
