package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.util.Json;

/**
 * Write-time shape validation for question configs. The judge and the
 * evaluator assume well-formed configs; a bad row used to save fine and
 * detonate mid-round (or leak through review as raw JSON). Every failure is
 * an {@link IllegalArgumentException} so the API answers 400, never 500.
 */
public final class QuestionConfigValidator {

    private QuestionConfigValidator() {}

    public static void requireValid(String type, String config) {
        QuestionType t = QuestionType.from(type);
        JsonNode c = parseObject(config);
        requireNoScript(c, "config");
        switch (t) {
            case MCQ -> {
                int n = requireOptions(c, 2);
                requireIndexInBounds(c, "correctIndex", n);
            }
            case OUTPUT_PRED -> {
                int idx = requireIndex(c, "correctIndex");
                if (c.has("options")) {
                    int n = requireOptions(c, 1);
                    if (idx >= n) throw new IllegalArgumentException("correctIndex out of bounds");
                }
            }
            case COMPLEXITY -> {
                int n = requireOptions(c, 2);
                requireIndexInBounds(c, "correctIndex", n);
            }
            case TRUE_FALSE -> {
                if (!c.path("correct").isBoolean()) {
                    throw new IllegalArgumentException("correct must be true or false");
                }
            }
            case MULTIPLE_SELECT -> {
                int n = requireOptions(c, 2);
                JsonNode indices = c.path("correctIndices");
                if (!indices.isArray() || indices.isEmpty()) {
                    throw new IllegalArgumentException("correctIndices must be a non-empty array");
                }
                for (JsonNode i : indices) {
                    if (!i.isInt() || i.asInt() < 0 || i.asInt() >= n) {
                        throw new IllegalArgumentException("correctIndices out of bounds");
                    }
                }
            }
            case NUMERIC -> {
                if (!c.path("answer").isNumber()) {
                    throw new IllegalArgumentException("answer must be a number");
                }
                if (c.has("tolerance") && (!c.path("tolerance").isNumber() || c.path("tolerance").asDouble() < 0)) {
                    throw new IllegalArgumentException("tolerance must be >= 0");
                }
            }
            case FILL_BLANK -> {
                if (!c.path("answer").isTextual() || c.path("answer").asText().isBlank()) {
                    throw new IllegalArgumentException("answer must not be blank");
                }
            }
            case DRAG_SORT -> {
                JsonNode order = c.path("correctOrder");
                if (!order.isArray() || order.size() < 2) {
                    throw new IllegalArgumentException("correctOrder needs at least 2 lines");
                }
            }
            case CLICK_BUG -> {
                JsonNode lines = c.path("codeLines");
                if (!lines.isArray() || lines.isEmpty()) {
                    throw new IllegalArgumentException("codeLines must be a non-empty array");
                }
                requireIndexInBounds(c, "bugLine", lines.size());
            }
            case CODE_COMPLETION -> {
                if (!c.path("expected").isTextual() || c.path("expected").asText().isBlank()) {
                    throw new IllegalArgumentException("expected must not be blank");
                }
            }
            case OJ_FULL, OJ_PATCH -> {
                JsonNode cases = c.path("testCases");
                if (!cases.isArray() || cases.isEmpty()) {
                    throw new IllegalArgumentException("testCases must be a non-empty array");
                }
                for (JsonNode tc : cases) {
                    if (!tc.isObject() || !tc.path("expectedOutput").isTextual()) {
                        throw new IllegalArgumentException("each testCase needs an expectedOutput string");
                    }
                }
                if (c.has("memoryLimitMb") && (!c.path("memoryLimitMb").isInt() || c.path("memoryLimitMb").asInt() <= 0)) {
                    throw new IllegalArgumentException("memoryLimitMb must be > 0");
                }
            }
        }
    }

    /**
     * Stored-XSS guard for free-text fields broadcast to every player
     * (quiz/question titles and descriptions). Rejects script injection
     * vectors; plain punctuation and code samples stay legal.
     */
    public static void requireCleanText(String field, String value) {
        if (value == null) return;
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("<script") || lower.contains("javascript:")) {
            throw new IllegalArgumentException(field + " contains disallowed markup");
        }
    }

    private static JsonNode parseObject(String config) {
        if (config == null || config.isBlank()) return Json.MAPPER.createObjectNode();
        JsonNode c;
        try {
            c = Json.readTree(config);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("config is not valid JSON");
        }
        if (!c.isObject()) throw new IllegalArgumentException("config must be a JSON object");
        return c;
    }

    private static int requireOptions(JsonNode c, int min) {
        JsonNode options = c.path("options");
        if (!options.isArray() || options.size() < min) {
            throw new IllegalArgumentException("options needs at least " + min + " entries");
        }
        return options.size();
    }

    private static int requireIndex(JsonNode c, String field) {
        JsonNode n = c.path(field);
        if (!n.isInt() || n.asInt() < 0) {
            throw new IllegalArgumentException(field + " must be a non-negative integer");
        }
        return n.asInt();
    }

    private static void requireIndexInBounds(JsonNode c, String field, int size) {
        if (requireIndex(c, field) >= size) {
            throw new IllegalArgumentException(field + " out of bounds");
        }
    }

    private static void requireNoScript(JsonNode node, String field) {
        if (node.isTextual()) {
            requireCleanText(field, node.asText());
        } else if (node.isArray()) {
            node.forEach(child -> requireNoScript(child, field));
        } else if (node.isObject()) {
            node.forEach(child -> requireNoScript(child, field));
        }
    }
}
