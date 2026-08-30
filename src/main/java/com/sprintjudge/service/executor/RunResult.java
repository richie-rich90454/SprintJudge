package com.sprintjudge.service.executor;

public record RunResult(boolean ok, String output, String error, String status) {}
