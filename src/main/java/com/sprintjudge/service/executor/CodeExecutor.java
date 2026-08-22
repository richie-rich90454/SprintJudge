package com.sprintjudge.service.executor;

public interface CodeExecutor {

    JudgeResult judge(JudgeRequest request);

    boolean supports(String language);
}
