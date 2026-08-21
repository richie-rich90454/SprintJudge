package com.openquiz.service;

import com.openquiz.domain.enums.QuestionType;
import com.openquiz.domain.models.Question;
import com.openquiz.util.Json;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationServiceTest {

    private final EvaluationService service = new EvaluationService();

    private Question q(String type, String config) {
        return new Question("q1", "qz", "t", "d", type,
                null, 30, 100, config, 0, null);
    }

    @Test
    void mcqCorrect() {
        var question = q("MCQ", Json.write(Map.of("correctIndex", 2)));
        double score = service.evaluateCorrectness(question, Json.readTree("{\"selectedIndex\":2}"));
        assertEquals(1.0, score, 0.001);
    }

    @Test
    void mcqWrong() {
        var question = q("MCQ", Json.write(Map.of("correctIndex", 2)));
        double score = service.evaluateCorrectness(question, Json.readTree("{\"selectedIndex\":0}"));
        assertEquals(0.0, score, 0.001);
    }

    @Test
    void numericTolerance() {
        var question = q("NUMERIC", Json.write(Map.of("answer", 3.14, "tolerance", 0.01)));
        double ok = service.evaluateCorrectness(question, Json.readTree("{\"value\":3.141}"));
        double bad = service.evaluateCorrectness(question, Json.readTree("{\"value\":4.0}"));
        assertEquals(1.0, ok, 0.001);
        assertEquals(0.0, bad, 0.001);
    }

    @Test
    void multipleSelectPartial() {
        var question = q("MULTIPLE_SELECT", Json.write(Map.of("correctIndices", java.util.List.of(0, 1))));
        double half = service.evaluateCorrectness(question, Json.readTree("{\"selectedIndices\":[0]}"));
        double full = service.evaluateCorrectness(question, Json.readTree("{\"selectedIndices\":[0,1]}"));
        assertEquals(0.5, half, 0.001);
        assertEquals(1.0, full, 0.001);
    }

    @Test
    void clickBugLine() {
        var question = q("CLICK_BUG", Json.write(Map.of("bugLine", 3)));
        double ok = service.evaluateCorrectness(question, Json.readTree("{\"line\":3}"));
        assertEquals(1.0, ok, 0.001);
    }
}
