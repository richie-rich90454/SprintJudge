package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.util.Json;

import java.util.Map;
import java.util.Set;

/**
 * Per-type config hygiene for the player-facing wire.
 *
 * <p>Questions carry answer keys in their {@code config} (correctIndex,
 * correct, correctIndices, answer, correctOrder, bugLine, expected, hidden
 * test cases). Those must never reach players during a round — only the
 * public reveal (after the round ends) gets the answer-bearing subset, built
 * by {@link #answerPayload}. {@link #sanitize} strips all answer keys plus
 * hidden OJ test cases, leaving the rendering fields (options, lines, code,
 * starter, …) intact.
 */
public final class QuestionAnswers {

    private QuestionAnswers() {}

    private static final Set<String> ANSWER_KEYS = Set.of(
            "correctIndex", "correct", "correctIndices", "answer", "correctOrder", "bugLine", "expected");

    // Answer-bearing keys revealed post-round, per (non-coding) question type.
    private static final Map<QuestionType, String[]> REVEAL_KEYS = Map.of(
            QuestionType.MCQ, new String[]{"correctIndex"},
            QuestionType.OUTPUT_PRED, new String[]{"correctIndex"},
            QuestionType.COMPLEXITY, new String[]{"correctIndex"},
            QuestionType.TRUE_FALSE, new String[]{"correct"},
            QuestionType.MULTIPLE_SELECT, new String[]{"correctIndices"},
            QuestionType.NUMERIC, new String[]{"answer", "tolerance"},
            QuestionType.FILL_BLANK, new String[]{"answer"},
            QuestionType.DRAG_SORT, new String[]{"correctOrder"},
            QuestionType.CLICK_BUG, new String[]{"bugLine"},
            QuestionType.CODE_COMPLETION, new String[]{"expected"});

    /** Player-facing config: answer keys + hidden OJ test cases removed. */
    public static JsonNode sanitize(QuestionType type, JsonNode raw) {
        if (raw == null || raw.isNull()) return raw;
        ObjectNode out = raw.deepCopy();
        ANSWER_KEYS.forEach(out::remove);
        if (type.isCoding() && out.has("testCases")) {
            // Keep only sample (non-hidden) test cases; expected output stays
            // hidden so later games reusing this question can't be pre-solved.
            ArrayNode visible = out.arrayNode();
            out.get("testCases").forEach(tc -> {
                if (!tc.path("isHidden").asBoolean(false)) visible.add(tc);
            });
            out.set("testCases", visible);
        }
        return out;
    }

    /** Reveal payload (post-round): only the answer-bearing keys, never test cases. */
    public static JsonNode answerPayload(QuestionType type, JsonNode raw) {
        if (raw == null || raw.isNull()) return null;
        if (type.isCoding()) return null; // OJ results are per-player, never a shared key
        String[] keys = REVEAL_KEYS.get(type);
        if (keys == null) return null; // ponytail: unknown type -> empty reveal, not NPE
        ObjectNode out = Json.MAPPER.createObjectNode();
        for (String key : keys) copy(out, raw, key);
        return out;
    }

    private static void copy(ObjectNode out, JsonNode src, String key) {
        JsonNode v = src.get(key);
        if (v != null) out.set(key, v);
    }
}
