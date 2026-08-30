package com.sprintjudge.service.executor;

public record RunRequest(String language, String sourceCode, String stdin, int timeoutSec) {}
