package com.sprintjudge.controller;

import com.sprintjudge.repository.Tables;
import com.sprintjudge.service.CalibrationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin calibration readout and trigger. Reads come straight from the persisted
 * {@code question_calibration} rows; the trigger recomputes every entry from
 * submissions through {@link CalibrationService} and persists them. Auth is
 * inherited from the {@code /api/admin/**} rule, so no security wiring lives here.
 */
@RestController
@RequestMapping("/api/admin/bank/calibration")
public class CalibrationController {

    private final CalibrationService service;
    private final DSLContext dsl;

    /**
     * Creates the controller over the calibration service and jOOQ context.
     *
     * @param service calibration computer and persister, never null
     * @param dsl injected jOOQ context for calibration-row reads, never null
     */
    public CalibrationController(CalibrationService service, DSLContext dsl) {
        this.service = service;
        this.dsl = dsl;
    }

    /**
     * Reads one persisted calibration row.
     *
     * @param questionId calibrated question id; blank is rejected
     * @return snapshot with questionId, pValue, discrimination, and attempts
     */
    @GetMapping("/{questionId}")
    public Map<String, Object> get(@PathVariable String questionId) {
        if (questionId == null || questionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question id is required");
        }
        return dsl.select(Tables.CAL_QID, Tables.CAL_PVALUE, Tables.CAL_DISC, Tables.CAL_ATTEMPTS)
                .from(Tables.CALIBRATION)
                .where(Tables.CAL_QID.eq(questionId))
                .fetchOptional(row -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("questionId", row.get(Tables.CAL_QID));
                    out.put("pValue", row.get(Tables.CAL_PVALUE));
                    out.put("discrimination", row.get(Tables.CAL_DISC));
                    out.put("attempts", row.get(Tables.CAL_ATTEMPTS));
                    return out;
                })
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown question id"));
    }

    /**
     * Recomputes calibrations from submissions and persists every entry.
     *
     * @return calibrated entry count under the calibrated key
     */
    @PostMapping("/run")
    public Map<String, Object> run() {
        Map<String, CalibrationService.Calibration> all = service.calibrateAll();
        for (CalibrationService.Calibration calibration : all.values()) {
            service.upsert(calibration);
        }
        return Map.of("calibrated", all.size());
    }
}
