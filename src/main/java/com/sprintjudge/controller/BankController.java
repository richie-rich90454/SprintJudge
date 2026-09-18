package com.sprintjudge.controller;

import com.sprintjudge.bank.BankStore;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * Creates the controller over the shared bank store.
     *
     * @param store classpath question bank
     */
    public BankController(BankStore store) {
        this.store = store;
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
}
