package com.sprintjudge.service;

import com.sprintjudge.TestDb;
import com.sprintjudge.repository.Tables;
import com.sprintjudge.service.CalibrationService.Calibration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationServiceTest {

    private DSLContext dsl;
    private CalibrationService svc;

    @BeforeEach
    void setup() throws Exception {
        dsl = TestDb.inMemory();
        svc = new CalibrationService(dsl);
    }

    private void insert(String id, String questionId, boolean correct, long submittedAt) {
        dsl.insertInto(Tables.SUBMISSIONS)
                .columns(Tables.SUB_ID, Tables.SUB_SESS, Tables.SUB_QUESTION,
                        Tables.SUB_PUUID, Tables.SUB_CORRECT, Tables.SUB_AT)
                .values(id, "s1", questionId, "u-" + id, correct, submittedAt)
                .execute();
    }

    @Test
    void emptyStoreCalibratesToEmptyMap() {
        assertTrue(svc.calibrateAll().isEmpty());
    }

    @Test
    void knownMixGivesExactPValue() {
        insert("m1", "q-mix", true, 1);
        insert("m2", "q-mix", true, 2);
        insert("m3", "q-mix", true, 3);
        insert("m4", "q-mix", false, 4);
        Map<String, Calibration> out = svc.calibrateAll();
        assertEquals(0.75, out.get("q-mix").pValue(), 1e-9);
        assertEquals(4, out.get("q-mix").attempts());
    }

    @Test
    void discriminationSignCorrectOnEarlyLateSplit() {
        insert("e1", "q-early", true, 1);
        insert("e2", "q-early", true, 2);
        insert("e3", "q-early", false, 3);
        insert("e4", "q-early", false, 4);
        insert("l1", "q-late", false, 1);
        insert("l2", "q-late", false, 2);
        insert("l3", "q-late", true, 3);
        insert("l4", "q-late", true, 4);
        Map<String, Calibration> out = svc.calibrateAll();
        assertEquals(1.0, out.get("q-early").discrimination(), 1e-9);
        assertEquals(-1.0, out.get("q-late").discrimination(), 1e-9);
    }

    @Test
    void singleAttemptHasZeroDiscrimination() {
        insert("s1", "q-solo", true, 1);
        Calibration c = svc.calibrateAll().get("q-solo");
        assertEquals(1.0, c.pValue(), 1e-9);
        assertEquals(0.0, c.discrimination(), 1e-9);
        assertEquals(1, c.attempts());
    }

    @Test
    void upsertInsertsThenUpdates() {
        svc.upsert(new Calibration("q1", 0.75, 0.5, 4));
        assertEquals(0.75, read("q1", Tables.CAL_PVALUE), 1e-9);
        assertEquals(4, readAttempts("q1"));
        svc.upsert(new Calibration("q1", 0.25, -0.5, 8));
        assertEquals(0.25, read("q1", Tables.CAL_PVALUE), 1e-9);
        assertEquals(-0.5, read("q1", Tables.CAL_DISC), 1e-9);
        assertEquals(8, readAttempts("q1"));
    }

    @Test
    void timingMinusOneDefaultPreserved() {
        svc.upsert(new Calibration("q1", 0.5, 0.0, 2));
        assertEquals(-1.0, read("q1", Tables.CAL_P25), 1e-9);
        assertEquals(-1.0, read("q1", Tables.CAL_MED), 1e-9);
        assertEquals(-1.0, read("q1", Tables.CAL_P75), 1e-9);
        svc.upsert(new Calibration("q1", 0.9, 0.1, 5));
        assertEquals(-1.0, read("q1", Tables.CAL_P25), 1e-9);
        assertEquals(-1.0, read("q1", Tables.CAL_MED), 1e-9);
        assertEquals(-1.0, read("q1", Tables.CAL_P75), 1e-9);
    }

    private double read(String questionId, org.jooq.Field<Double> field) {
        var row = dsl.selectFrom(Tables.CALIBRATION)
                .where(Tables.CAL_QID.eq(questionId))
                .fetchOne();
        assertNotNull(row);
        return ((Number) row.get(field.getName())).doubleValue();
    }

    private int readAttempts(String questionId) {
        var row = dsl.selectFrom(Tables.CALIBRATION)
                .where(Tables.CAL_QID.eq(questionId))
                .fetchOne();
        assertNotNull(row);
        return ((Number) row.get(Tables.CAL_ATTEMPTS.getName())).intValue();
    }
}
