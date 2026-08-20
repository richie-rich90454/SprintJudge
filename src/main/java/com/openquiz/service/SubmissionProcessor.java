package com.openquiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.openquiz.domain.dto.LeaderboardEntry;
import com.openquiz.domain.enums.QuestionType;
import com.openquiz.domain.models.Question;
import com.openquiz.domain.models.Submission;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.SubmissionRepository;
import com.openquiz.service.executor.CodeExecutor;
import com.openquiz.service.executor.JudgeRequest;
import com.openquiz.service.executor.JudgeResult;
import com.openquiz.service.executor.TestCase;
import com.openquiz.util.Ids;
import com.openquiz.util.Json;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
public class SubmissionProcessor {

    private final CodeExecutor executor;
    private final SubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;
    private final ScoringEngine scoringEngine;
    private final GameRoomManager roomManager;
    private final Semaphore slot;

    public SubmissionProcessor(CodeExecutor executor,
                               SubmissionRepository submissionRepository,
                               QuestionRepository questionRepository,
                               ScoringEngine scoringEngine,
                               GameRoomManager roomManager,
                               Semaphore executionSlots) {
        this.executor = executor;
        this.submissionRepository = submissionRepository;
        this.questionRepository = questionRepository;
        this.scoringEngine = scoringEngine;
        this.roomManager = roomManager;
        this.slot = executionSlots;
    }

    @Async("virtualThreadExecutor")
    public void processCoding(String sessionId, String questionId, String playerName,
                              String playerUuid, String language, String sourceCode,
                              Map<String, Object> settings) {
        QuestionType type = null;
        Question question = questionRepository.findById(questionId).orElse(null);
        if (question == null) return;
        type = QuestionType.from(question.questionType());

        JsonNode config = Json.readTree(question.config());
        List<TestCase> cases = new ArrayList<>();
        if (config.has("testCases")) {
            for (JsonNode tc : config.get("testCases")) {
                cases.add(new TestCase(
                        tc.path("input").asText(""),
                        tc.path("expectedOutput").asText(""),
                        tc.path("isHidden").asBoolean(false)));
            }
        }
        int memMb = config.path("memoryLimitMb").asInt(256);
        int timeout = Math.max(5, question.timeLimitSec());

        JudgeRequest req = new JudgeRequest(language, sourceCode, cases, timeout, memMb);

        boolean acquired = false;
        JudgeResult result;
        try {
            slot.acquire();
            acquired = true;
            result = executor.judge(req);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } finally {
            if (acquired) slot.release();
        }

        int attempts = submissionRepository.findBySessionQuestion(sessionId, questionId).stream()
                .filter(s -> s.playerUuid().equals(playerUuid)).mapToInt(Submission::attemptCount).sum() + 1;

        int score = scoringEngine.scoreCoding(
                result.passed(), result.total(), question.pointsBase(),
                result.allPassed(), 0, question.timeLimitSec(), attempts, settings);

        Submission best = submissionRepository.findBest(sessionId, questionId, playerUuid).orElse(null);
        if (best == null || score > best.scoreEarned()) {
            submissionRepository.save(new Submission(
                    Ids.uuid(), sessionId, questionId, playerName, playerUuid,
                    Json.write(Map.of("language", language, "source", sourceCode)),
                    score, result.allPassed(), Json.write(result), attempts, Instant.now()));
        }
        roomManager.broadcastLeaderboard(sessionId);
    }
}
