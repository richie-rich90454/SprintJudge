package com.sprintjudge.service.executor;

public interface CodeExecutor {

    JudgeResult judge(JudgeRequest request);

    RunResult run(RunRequest request);

    boolean supports(String language);
}
