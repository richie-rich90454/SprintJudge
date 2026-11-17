package com.sprintjudge.service.executor;

import java.util.List;
import java.util.Objects;

public final class JudgeRequest {
	private String language;
	private String sourceCode;
	private List<TestCase> testCases;
	private int timeoutSec;
	private int memoryLimitMb;

	public JudgeRequest() {
	}

	public JudgeRequest(String language, String sourceCode, List<TestCase> testCases, int timeoutSec,
			int memoryLimitMb) {
		this.language = language;
		this.sourceCode = sourceCode;
		this.testCases = testCases;
		this.timeoutSec = timeoutSec;
		this.memoryLimitMb = memoryLimitMb;
	}

	public String getLanguage() {
		return language;
	}
	public void setLanguage(String language) {
		this.language = language;
	}

	public String getSourceCode() {
		return sourceCode;
	}
	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	public List<TestCase> getTestCases() {
		return testCases;
	}
	public void setTestCases(List<TestCase> testCases) {
		this.testCases = testCases;
	}

	public int getTimeoutSec() {
		return timeoutSec;
	}
	public void setTimeoutSec(int timeoutSec) {
		this.timeoutSec = timeoutSec;
	}

	public int getMemoryLimitMb() {
		return memoryLimitMb;
	}
	public void setMemoryLimitMb(int memoryLimitMb) {
		this.memoryLimitMb = memoryLimitMb;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof JudgeRequest))
			return false;
		JudgeRequest other = (JudgeRequest) o;
		return timeoutSec == other.timeoutSec && memoryLimitMb == other.memoryLimitMb
				&& Objects.equals(language, other.language) && Objects.equals(sourceCode, other.sourceCode)
				&& Objects.equals(testCases, other.testCases);
	}

	@Override
	public int hashCode() {
		return Objects.hash(language, sourceCode, testCases, timeoutSec, memoryLimitMb);
	}

	@Override
	public String toString() {
		return "JudgeRequest[language=" + language + ", sourceCode=" + sourceCode + ", testCases=" + testCases
				+ ", timeoutSec=" + timeoutSec + ", memoryLimitMb=" + memoryLimitMb + "]";
	}
}
