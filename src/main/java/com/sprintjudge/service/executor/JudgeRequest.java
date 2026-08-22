package com.sprintjudge.service.executor;

import java.util.List;

public record JudgeRequest(
        String language,
        String sourceCode,
        List<TestCase> testCases,
        int timeoutSec,
        int memoryLimitMb
) {}
