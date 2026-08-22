package com.openquiz.service.executor;

public interface CodeExecutor {

    JudgeResult judge(JudgeRequest request);

    boolean supports(String language);
}
