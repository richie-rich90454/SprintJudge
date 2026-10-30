package com.sprintjudge.controller;

import com.sprintjudge.bank.BankStore;
import com.sprintjudge.service.BankQuizBuilder;
import com.sprintjudge.service.TeachNextService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin question-bank headers. Answer-free by construction: only id, subject,
 * unit, topic, scope, format, and difficulty cross the wire. Auth is inherited
 * from the {@code /api/admin/**} rule, so no security wiring lives here.
 */
@RestController
@RequestMapping("/api/admin/bank")
public class BankController {

    private final BankStore store;
    private final BankQuizBuilder builder;
    private final TeachNextService teachNextService;

    /**
     * Creates the controller over the shared bank store and quiz builder.
     *
     * @param store classpath question bank
     * @param builder playable quiz generator
     */
    public BankController(BankStore store, BankQuizBuilder builder) {
        this(store, builder, null);
    }

    /**
     * Creates the controller with a teach-next recommendation service.
     *
     * @param store classpath question bank
     * @param builder playable quiz generator
     * @param teachNextService recommendation service (may be null in tests)
     */
    @org.springframework.beans.factory.annotation.Autowired
    public BankController(BankStore store, BankQuizBuilder builder, TeachNextService teachNextService) {
        this.store = store;
        this.builder = builder;
        this.teachNextService = teachNextService;
    }

    /**
     * Searches the bank by subject, unit, scope, and format.
     *
     * @param subject required subject name
     * @param unit optional unit filter, blank means all
     * @param scope optional scope filter, blank or everything means all
     * @param format optional format filter, blank means all
     * @return matching answer-free headers
     */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(required = false) String subject,
                                            @RequestParam(required = false) String unit,
                                            @RequestParam(required = false) String scope,
                                            @RequestParam(required = false) String format) {
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject is required");
        }
        return store.search(subject, unit, scope, format);
    }

    /**
     * Fetches one answer-free question header.
     *
     * @param id question id
     * @return answer-free header
     */
    @GetMapping("/question/{id}")
    public Map<String, Object> question(@PathVariable String id) {
        return store.headerById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown question id"));
    }

    /**
     * Reports per-unit format and scope coverage for one subject.
     *
     * @param subject subject name
     * @return unit name to total, formats, and scopes
     */
    @GetMapping("/coverage/{subject}")
    public Map<String, Map<String, Object>> coverage(@PathVariable String subject) {
        return store.coverage(subject);
    }

    /**
     * Reports question totals per subject.
     *
     * @return subject name to total
     */
    @GetMapping("/volume")
    public Map<String, Integer> volume() {
        return store.volume();
    }

    /**
     * Builds a playable quiz from bank records. Admin-only: the builder copies
     * answer keys and explanations into the generated quiz config.
     */
    @PostMapping("/build")
    public BankQuizBuilder.BuildResult build(@RequestBody BankQuizBuilder.BuildRequest request) {
        try {
            return builder.build(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Turns a finished game's weakest topic into a bank practice suggestion.
     *
     * @param sessionId finished game session id
     * @return subject, unit, topic, and count
     */
    @GetMapping("/teach-next")
    public TeachNextService.Recommendation teachNext(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        }
        if (teachNextService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "teach-next service unavailable");
        }
        return teachNextService.recommendPractice(sessionId);
    }

    /** Four CSA-aligned class presets for the teacher home, sourced from the featured manifest. */
    @GetMapping("/csa")
    public List<BankQuizBuilder.CsaPreset> csaPresets() {
        return builder.csaPresets();
    }
}
