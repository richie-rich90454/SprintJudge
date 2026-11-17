package com.sprintjudge.domain.dto;

import java.util.Objects;

public final class QuestionStart {
	private String type;
	private QuestionDto question;
	private int timeLimitSec;
	private long startedAtEpochMs;
	private long serverNowEpochMs;

	public QuestionStart() {
	}

	public QuestionStart(String type, QuestionDto question, int timeLimitSec, long startedAtEpochMs,
			long serverNowEpochMs) {
		this.type = type;
		this.question = question;
		this.timeLimitSec = timeLimitSec;
		this.startedAtEpochMs = startedAtEpochMs;
		this.serverNowEpochMs = serverNowEpochMs;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	public QuestionDto getQuestion() {
		return question;
	}
	public void setQuestion(QuestionDto question) {
		this.question = question;
	}

	public int getTimeLimitSec() {
		return timeLimitSec;
	}
	public void setTimeLimitSec(int timeLimitSec) {
		this.timeLimitSec = timeLimitSec;
	}

	public long getStartedAtEpochMs() {
		return startedAtEpochMs;
	}
	public void setStartedAtEpochMs(long startedAtEpochMs) {
		this.startedAtEpochMs = startedAtEpochMs;
	}

	public long getServerNowEpochMs() {
		return serverNowEpochMs;
	}
	public void setServerNowEpochMs(long serverNowEpochMs) {
		this.serverNowEpochMs = serverNowEpochMs;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof QuestionStart))
			return false;
		QuestionStart other = (QuestionStart) o;
		return timeLimitSec == other.timeLimitSec && startedAtEpochMs == other.startedAtEpochMs
				&& serverNowEpochMs == other.serverNowEpochMs && Objects.equals(type, other.type)
				&& Objects.equals(question, other.question);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, question, timeLimitSec, startedAtEpochMs, serverNowEpochMs);
	}

	@Override
	public String toString() {
		return "QuestionStart[type=" + type + ", question=" + question + ", timeLimitSec=" + timeLimitSec
				+ ", startedAtEpochMs=" + startedAtEpochMs + ", serverNowEpochMs=" + serverNowEpochMs + "]";
	}
}
