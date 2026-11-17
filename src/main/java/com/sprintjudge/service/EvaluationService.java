package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.util.Json;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Determines how correct a player's response is, per question type. Returns a
 * 0..1 fraction so MULTIPLE_SELECT partial scoring and NUMERIC tolerance are
 * handled uniformly.
 */
@Service
public class EvaluationService {

	public double evaluateCorrectness(Question question, JsonNode response) {
		String type = QuestionType.from(question.getQuestionType());
		JsonNode config = Json.readTree(question.getConfig());
		if (response == null || response.isNull())
			return 0.0;
		double result;
		switch (type) {
			case "MCQ" :
			case "OUTPUT_PRED" :
			case "COMPLEXITY" :
				result = response.path("selectedIndex").asInt(-1) == config.path("correctIndex").asInt(-2) ? 1.0 : 0.0;
				break;
			case "TRUE_FALSE" : {
				// Sentinel mismatch: a missing/empty value must not coincidentally
				// match a `false` answer. Require the field to be present.
				if (!response.has("value")) {
					result = 0.0;
					break;
				}
				boolean ans = config.path("correct").asBoolean();
				result = response.path("value").asBoolean() == ans ? 1.0 : 0.0;
				break;
			}
			case "MULTIPLE_SELECT" :
				result = evaluateMulti(config, response);
				break;
			case "NUMERIC" :
				result = evaluateNumeric(config, response);
				break;
			case "FILL_BLANK" :
				result = normalize(response.path("text").asText()).equals(normalize(config.path("answer").asText()))
						? 1.0
						: 0.0;
				break;
			case "CLICK_BUG" :
				result = response.path("line").asInt(-1) == config.path("bugLine").asInt(-2) ? 1.0 : 0.0;
				break;
			case "DRAG_SORT" :
				result = evaluateOrder(config, response);
				break;
			case "CODE_COMPLETION" :
				result = normalize(response.path("code").asText()).equals(normalize(config.path("expected").asText()))
						? 1.0
						: 0.0;
				break;
			case "OJ_FULL" :
			case "OJ_PATCH" :
				result = 1.0; // judged by executor
				break;
			default :
				result = 0.0;
		}
		return result;
	}

	private double evaluateMulti(JsonNode config, JsonNode response) {
		Set<Integer> correct = new HashSet<>();
		for (JsonNode n : config.path("correctIndices"))
			correct.add(n.asInt());
		Set<Integer> chosen = new HashSet<>();
		for (JsonNode n : response.path("selectedIndices"))
			chosen.add(n.asInt());
		if (correct.isEmpty() || chosen.isEmpty())
			return 0.0;
		int intersect = 0;
		for (int c : chosen)
			if (correct.contains(c))
				intersect++;
		int missed = correct.size() - intersect;
		int extra = chosen.size() - intersect;
		// Selecting everything must never farm credit: any wrong option
		// beyond the correct set means the answer is simply wrong.
		if (extra > 0 && extra >= intersect)
			return 0.0;
		double score = 1.0 - (missed * 0.5 + extra * 0.5) / Math.max(1, correct.size());
		return Math.max(0.0, score);
	}

	private double evaluateNumeric(JsonNode config, JsonNode response) {
		double ans = config.path("answer").asDouble(Double.NaN);
		if (Double.isNaN(ans))
			return 0.0;
		double val = response.path("value").asDouble(Double.NaN);
		if (Double.isNaN(val))
			return 0.0;
		double tol = config.path("tolerance").asDouble(0.0);
		return Math.abs(val - ans) <= tol ? 1.0 : 0.0;
	}

	private double evaluateOrder(JsonNode config, JsonNode response) {
		JsonNode correctArr = config.path("correctOrder");
		JsonNode chosenArr = response.path("order");
		if (!correctArr.isArray() || !chosenArr.isArray())
			return 0.0;
		int n = correctArr.size();
		if (chosenArr.size() != n)
			return 0.0;
		int ok = 0;
		for (int i = 0; i < n; i++) {
			if (correctArr.get(i).asText().equals(chosenArr.get(i).asText()))
				ok++;
		}
		return (double) ok / n;
	}

	private String normalize(String s) {
		return s.strip().replaceAll("\\s+", " ");
	}
}
