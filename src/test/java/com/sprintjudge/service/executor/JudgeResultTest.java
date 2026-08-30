package com.sprintjudge.service.executor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JudgeResultTest {

    @Test
    void caseResultAccessors() {
        JudgeResult.CaseResult c = new JudgeResult.CaseResult(0, true, "expected", "actual", null);
        assertEquals(0, c.index());
        assertTrue(c.passed());
        assertEquals("expected", c.expected());
        assertEquals("actual", c.actual());
        assertNull(c.error());
        assertTrue(c.toString().contains("CaseResult"));

        JudgeResult.CaseResult equal = new JudgeResult.CaseResult(0, true, "expected", "actual", null);
        assertEquals(c, equal);
        assertEquals(c.hashCode(), equal.hashCode());

        JudgeResult.CaseResult different = new JudgeResult.CaseResult(1, false, "x", "y", "e");
        assertNotEquals(c, different);
        assertNotEquals(c, "not a case");
        assertNotEquals(c, null);
    }

    @Test
    void judgeResultAccessorsAndEquality() {
        JudgeResult.CaseResult c = new JudgeResult.CaseResult(0, true, "e", "a", null);
        JudgeResult r = new JudgeResult(1, 1, true, List.of(c));
        assertEquals(1, r.passed());
        assertEquals(1, r.total());
        assertTrue(r.allPassed());
        assertEquals(1, r.cases().size());
        assertSame(c, r.cases().get(0));
        assertTrue(r.toString().contains("JudgeResult"));

        JudgeResult same = new JudgeResult(1, 1, true, List.of(c));
        assertEquals(r, same);
        assertEquals(r.hashCode(), same.hashCode());

        JudgeResult different = new JudgeResult(0, 2, false, List.of());
        assertNotEquals(r, different);
        assertNotEquals(r, null);
        assertNotEquals(r, "not a result");
    }
}
