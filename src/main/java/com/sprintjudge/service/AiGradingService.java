package com.sprintjudge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI grading service for coding submissions. Supports two providers:
 * <ul>
 *   <li>{@code openai} — any OpenAI-compatible API (cloud or local)</li>
 *   <li>{@code llamacpp} — local llama.cpp server with OpenAI-compatible endpoint</li>
 * </ul>
 * Configured via {@code sprintjudge.ai.*} properties. When disabled or
 * unreachable, falls back to judge-only scoring (no AI feedback).
 */
@Service
public class AiGradingService {

    private static final Logger log = LoggerFactory.getLogger(AiGradingService.class);

    @Value("${sprintjudge.ai.enabled:false}")
    private boolean enabled;

    @Value("${sprintjudge.ai.provider:openai}")
    private String provider;

    @Value("${sprintjudge.ai.endpoint:}")
    private String endpoint;

    @Value("${sprintjudge.ai.model:}")
    private String model;

    @Value("${sprintjudge.ai.api-key:}")
    private String apiKey;

    @Value("${sprintjudge.ai.timeout-sec:30}")
    private int timeoutSec;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final int MAX_CACHE_SIZE = 500;

    /** Simple cache to avoid repeated grading of identical submissions. */
    private final ConcurrentHashMap<String, AiGradeResult> cache = new ConcurrentHashMap<>();

    public record AiGradeResult(boolean available, String feedback, int suggestedScore, String status) {
        public static AiGradeResult unavailable() {
            return new AiGradeResult(false, "", 0, "unavailable");
        }
    }

    /**
     * Grades a coding submission using AI. Returns feedback and a suggested
     * score adjustment. Falls back gracefully if the AI service is unavailable.
     */
    public AiGradeResult grade(String language, String sourceCode, String questionTitle,
                                String questionDescription, boolean allPassed,
                                int judgeScore, int pointsBase) {
        if (!enabled || endpoint.isBlank()) {
            return AiGradeResult.unavailable();
        }

        String cacheKey = sha256(language + "||" + sourceCode + "||" + questionTitle);
        AiGradeResult cached = cache.get(cacheKey);
        if (cached != null) return cached;

        try {
            String prompt = buildPrompt(language, sourceCode, questionTitle,
                    questionDescription, allPassed, judgeScore, pointsBase);
            String response = callLlm(prompt);
            AiGradeResult result = parseResponse(response, judgeScore, pointsBase);
            // ponytail: simple size cap, replace with LRU if eviction pattern matters
            cache.put(cacheKey, result);
            if (cache.size() > MAX_CACHE_SIZE * 2) cache.clear();
            return result;
        } catch (Exception e) {
            log.warn("AI grading failed: {}", e.getMessage());
            return AiGradeResult.unavailable();
        }
    }

    private String buildPrompt(String language, String source, String title,
                                String description, boolean passed, int score, int maxScore) {
        return """
                You are a CS education code grader. Grade this student submission.

                ## Question
                Title: %s
                Description: %s

                ## Student Code (%s)
                ```%s
                %s
                ```

                ## Judge Result
                Tests passed: %s
                Score: %d / %d

                ## Instructions
                Analyze the code for:
                1. Correctness (does it solve the problem?)
                2. Code quality (readability, naming, structure)
                3. Efficiency (time/space complexity)
                4. Edge cases handled

                Respond in JSON:
                {"feedback": "<1-2 sentence constructive feedback>", "suggestedScore": <0-%d>}
                If the code is good, suggest the current score. Adjust only for notable quality issues or improvements.
                """.formatted(title, description, language, language, source,
                passed, score, maxScore, maxScore);
    }

    private String callLlm(String prompt) throws Exception {
        String url = endpoint.endsWith("/") ? endpoint + "chat/completions" : endpoint + "/chat/completions";
        String body = """
                {
                    "model": "%s",
                    "messages": [
                        {"role": "system", "content": "You are a helpful CS education code grader. Respond only in valid JSON."},
                        {"role": "user", "content": %s}
                    ],
                    "temperature": 0.3,
                    "max_tokens": 256
                }
                """.formatted(model, toJsonString(prompt));

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json");

        if (!apiKey.isBlank()) {
            req.header("Authorization", "Bearer " + apiKey);
        }

        req.POST(HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private AiGradeResult parseResponse(String llmResponse, int judgeScore, int maxScore) {
        try {
            // Extract content from OpenAI-compatible response format using Jackson.
            com.fasterxml.jackson.databind.JsonNode root = com.sprintjudge.util.Json.MAPPER.readTree(llmResponse);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) return AiGradeResult.unavailable();

            com.fasterxml.jackson.databind.JsonNode parsed = com.sprintjudge.util.Json.MAPPER.readTree(content);
            String feedback = parsed.path("feedback").asText("");
            int suggestedScore = parsed.path("suggestedScore").asInt(0);
            suggestedScore = Math.max(0, Math.min(suggestedScore, maxScore));

            return new AiGradeResult(true, feedback, suggestedScore, "ok");
        } catch (Exception e) {
            log.debug("Failed to parse AI response: {}", e.getMessage());
            return AiGradeResult.unavailable();
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by every JVM — unreachable
            throw new IllegalStateException(e);
        }
    }

    private String toJsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    public boolean isEnabled() { return enabled; }
    public String getProvider() { return provider; }
}
