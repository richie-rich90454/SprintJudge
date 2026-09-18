package com.sprintjudge.service;

import com.sprintjudge.repository.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives per-question difficulty statistics from real submissions.
 * Difficulty ({@code pValue}) is the mean correctness rate; discrimination is a
 * temporal-split proxy (early-half correct rate minus late-half correct rate by
 * {@code submitted_at} ordering, clamped to [-1, 1]) — deliberately NOT
 * point-biserial, which would require per-player ability scores.
 */
@Service
public class CalibrationService {

    private final DSLContext dsl;

    /**
     * Creates a calibration service over the given jOOQ context.
     *
     * @param dsl injected jOOQ context, never null
     */
    public CalibrationService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Per-question calibration snapshot.
     *
     * @param questionId     calibrated question id
     * @param pValue         mean correctness rate in [0, 1]
     * @param discrimination early-half minus late-half correct rate in [-1, 1]
     * @param attempts       submission count the stats derive from
     */
    public record Calibration(String questionId, double pValue, double discrimination, int attempts) {
    }

    /**
     * Computes one {@link Calibration} per question present in submissions.
     * Attempts are split into early/late halves by {@code submitted_at} ordering;
     * a question with fewer than 2 attempts gets discrimination 0.0.
     * Never returns null; empty store yields an empty map.
     *
     * @return calibrations keyed by question id, never null
     */
    public Map<String, Calibration> calibrateAll() {
        var rows = dsl.select(Tables.SUB_QUESTION, Tables.SUB_CORRECT, Tables.SUB_AT)
                .from(Tables.SUBMISSIONS)
                .orderBy(Tables.SUB_QUESTION.asc(), Tables.SUB_AT.asc())
                .fetch();
        Map<String, List<Boolean>> byQuestion = new LinkedHashMap<>();
        for (var row : rows) {
            byQuestion.computeIfAbsent(row.get(Tables.SUB_QUESTION), k -> new ArrayList<>())
                    .add(Boolean.TRUE.equals(row.get(Tables.SUB_CORRECT)));
        }
        Map<String, Calibration> result = new LinkedHashMap<>();
        for (var entry : byQuestion.entrySet()) {
            List<Boolean> flags = entry.getValue();
            int n = flags.size();
            double pValue = mean(flags, 0, n);
            double discrimination = 0.0;
            if (n > 1) {
                int half = n / 2;
                discrimination = Math.max(-1.0, Math.min(1.0, mean(flags, 0, half) - mean(flags, half, n)));
            }
            result.put(entry.getKey(), new Calibration(entry.getKey(), pValue, discrimination, n));
        }
        return result;
    }

    /**
     * Inserts or updates the {@code question_calibration} row for the given stats.
     * Timing columns (p25/median/p75) are unknown until timing data exists: a fresh
     * row stores -1, and an update leaves existing timing values untouched.
     *
     * @param calibration stats to persist, never null
     */
    public void upsert(Calibration calibration) {
        long now = Instant.now().getEpochSecond();
        dsl.insertInto(Tables.CALIBRATION)
                .columns(Tables.CAL_QID, Tables.CAL_PVALUE, Tables.CAL_DISC,
                        Tables.CAL_P25, Tables.CAL_MED, Tables.CAL_P75,
                        Tables.CAL_ATTEMPTS, Tables.CAL_UPDATED)
                .values(calibration.questionId(), calibration.pValue(), calibration.discrimination(),
                        -1.0, -1.0, -1.0, calibration.attempts(), now)
                .onConflict(Tables.CAL_QID).doUpdate()
                .set(Tables.CAL_PVALUE, calibration.pValue())
                .set(Tables.CAL_DISC, calibration.discrimination())
                .set(Tables.CAL_ATTEMPTS, calibration.attempts())
                .set(Tables.CAL_UPDATED, now)
                .execute();
    }

    /** Mean of flags[from, to) as a 0..1 rate; caller guarantees {@code to > from}. */
    private static double mean(List<Boolean> flags, int from, int to) {
        int correct = 0;
        for (int i = from; i < to; i++) {
            if (flags.get(i)) {
                correct++;
            }
        }
        return (double) correct / (to - from);
    }
}
