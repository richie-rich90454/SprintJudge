package com.sprintjudge.controller;

import com.sprintjudge.scoring.SjsCalculator;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public speed-accuracy signal. Delegates the math to {@link SjsCalculator};
 * the copy strings come from the copy deck, in plain language only.
 */
@RestController
@RequestMapping("/api/public/scoring")
public class ScoringController {

    /**
     * Computes the speed-accuracy signal, its plain-language copy, and the SJS
     * tier for one student snapshot.
     *
     * @param medianSeconds student median response seconds, at least zero
     * @param questionMedian question median response seconds, at least zero
     * @param accuracy student accuracy from zero to one
     * @param sjs SprintJudge Score used only for the tier band
     * @return signal, copy, and tier
     */
    @GetMapping("/signal")
    public Map<String, Object> signal(@RequestParam(required = false) Double medianSeconds,
                                      @RequestParam(required = false) Double questionMedian,
                                      @RequestParam(required = false) Double accuracy,
                                      @RequestParam(defaultValue = "0") double sjs) {
        if (medianSeconds == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medianSeconds is required");
        }
        if (questionMedian == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionMedian is required");
        }
        if (accuracy == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accuracy is required");
        }
        if (!(medianSeconds >= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medianSeconds must be >= 0");
        }
        if (!(questionMedian >= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionMedian must be >= 0");
        }
        if (!(accuracy >= 0 && accuracy <= 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accuracy must be between 0 and 1");
        }
        String signal = SjsCalculator.speedAccuracySignal(medianSeconds, questionMedian, accuracy);
        return Map.of("signal", signal, "copy", copyOf(signal), "tier", SjsCalculator.tier(sjs));
    }

    static String copyOf(String signal) {
        return switch (signal) {
            case "FLUENT" -> "You know this cold.";
            case "GUESSING" -> "You are fast but guessing on this topic. Try slowing down.";
            case "CAREFUL" -> "You know it, but fluency needs work.";
            case "STRUGGLING" -> "This topic needs reteaching. Try practice mode.";
            default -> "This topic needs reteaching. Try practice mode.";
        };
    }
}
