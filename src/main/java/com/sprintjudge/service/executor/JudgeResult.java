package com.sprintjudge.service.executor;

import java.util.List;

public record JudgeResult(
        int passed,
        int total,
        boolean allPassed,
        List<CaseResult> cases
) {
    public record CaseResult(int index, boolean passed, String expected, String actual, String error) {}
}
