package com.sprintjudge.service.executor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunRequest(
        @NotBlank String language,
        @NotBlank @Size(max = 65536) String sourceCode,
        @Size(max = 10000) String stdin,
        @Max(30) int timeoutSec) {}
