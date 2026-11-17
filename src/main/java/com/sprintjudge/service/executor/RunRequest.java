package com.sprintjudge.service.executor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RunRequest {
	private String language;
	private String sourceCode;
	private String stdin;
	private int timeoutSec;

	@SuppressWarnings("all")
	public RunRequest() {
	}

	public RunRequest(String language, String sourceCode, String stdin, int timeoutSec) {
		this.language = language;
		this.sourceCode = sourceCode;
		this.stdin = stdin;
		this.timeoutSec = timeoutSec;
	}

	@NotBlank
	public String getLanguage() {
		return language;
	}
	public void setLanguage(String language) {
		this.language = language;
	}

	@NotBlank
	@Size(max = 65536)
	public String getSourceCode() {
		return sourceCode;
	}
	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	@Size(max = 10000)
	public String getStdin() {
		return stdin;
	}
	public void setStdin(String stdin) {
		this.stdin = stdin;
	}

	@Max(30)
	public int getTimeoutSec() {
		return timeoutSec;
	}
	public void setTimeoutSec(int timeoutSec) {
		this.timeoutSec = timeoutSec;
	}
}
