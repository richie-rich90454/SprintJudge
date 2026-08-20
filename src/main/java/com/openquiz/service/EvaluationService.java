package com.openquiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.openquiz.domain.enums.QuestionType;
import com.openquiz.domain.models.Question;
import com.openquiz.util.Json;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Determines how correct a player's response is, per question type.
 * Returns a 0..1 fraction so MULTIPLE_SELECT partial scoring and
 * NUMERIC tolerance are handled uniformly.
 */
@Service
public class EvaluationService {

    public double evaluateCorrectness(Question question, JsonNode response) {
        QuestionType type = QuestionType.from(question.questionType());
        JsonNode config = Json.readTree(question.config());
        if (response == null || response.isNull()) return 0.0;
        return switch (type) {
            case MCQ, OUTPUT_PRED, COMPLEXITY ->
                    response.path("selectedIndex").asInt(-1) == config.path("correctIndex").asInt(-2) ? 1.0 : 0.0;
            case TRUE_FALSE -> {
                boolean ans = config.path("correct").asBoolean();
                yield response.path("value").asBoolean() == ans ? 1.0 : 0.0;
            }
            case MULTIPLE_SELECT -> evaluateMulti(config, response);
            case NUMERIC -> evaluateNumeric(config, response);
            case FILL_BLANK -> normalize(response.path("text").asText()).equals(normalize(config.path("answer").asText())) ? 1.0 : 0.0;
            case CLICK_BUG -> response.path("line").asInt(-1) == config.path("bugLine").asInt(-2) ? 1.0 : 0.0;
            case DRAG_SORT -> evaluateOrder(config, response);
            case CODE_COMPLETION -> normalize(response.path("code").asText()).equals(normalize(config.path("expected").asText())) ? 1.0 : 0.0;
            case OJ_FULL, OJ_PATCH -> 1.0; // judged by executor
        };
    }

    private double evaluateMulti(JsonNode config, JsonNode response) {
        Set<Integer> correct = new HashSet<>();
        config.path("correctIndices").forEach(n -> correct.add(n.asInt()));
        Set<Integer> chosen = new HashSet<>();
        response.path("selectedIndices").forEach(n -> chosen.add(n.asInt()));
        if (correct.isEmpty() || chosen.isEmpty()) return 0.0;
        int intersect = 0;
        for (int c : chosen) if (correct.contains(c)) intersect++;
        int missed = correct.size() - intersect;
        int extra = chosen.size() - intersect;
        double score = 1.0 - (missed * 0.5 + extra * 0.5) / Math.max(1, correct.size());
        return Math.max(0.0, score);
    }

    private double evaluateNumeric(JsonNode config, JsonNode response) {
        double ans = config.path("answer").asDouble(Double.NaN);
        if (Double.isNaN(ans)) return 0.0;
        double val = response.path("value").asDouble(Double.NaN);
        if (Double.isNaN(val)) return 0.0;
        double tol = config.path("tolerance").asDouble(0.0);
        return Math.abs(val - ans) <= tol ? 1.0 : 0.0;
    }

    private double evaluateOrder(JsonNode config, JsonNode response) {
        JsonNode correctArr = config.path("correctOrder");
        JsonNode chosenArr = response.path("order");
        if (!correctArr.isArray() || !chosenArr.isArray()) return 0.0;
        int n = correctArr.size();
        if (chosenArr.size() != n) return 0.0;
        int ok = 0;
        for (int i = 0; i < n; i++) {
            if (correctArr.get(i).asText().equals(chosenArr.get(i).asText())) ok++;
        }
        return (double) ok / n;
    }

    private String normalize(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }
}
