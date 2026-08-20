package com.openquiz.service.executor;

import java.util.List;

public interface CodeExecutor {

    JudgeResult judge(JudgeRequest request);

    boolean supports(String language);
}
