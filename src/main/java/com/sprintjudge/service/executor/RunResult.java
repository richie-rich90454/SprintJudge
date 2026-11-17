package com.sprintjudge.service.executor;

import java.util.Objects;

public final class RunResult {
	private boolean ok;
	private String output;
	private String error;
	private String status;

	public RunResult() {
	}

	public RunResult(boolean ok, String output, String error, String status) {
		this.ok = ok;
		this.output = output;
		this.error = error;
		this.status = status;
	}

	public boolean isOk() {
		return ok;
	}
	public void setOk(boolean ok) {
		this.ok = ok;
	}

	public String getOutput() {
		return output;
	}
	public void setOutput(String output) {
		this.output = output;
	}

	public String getError() {
		return error;
	}
	public void setError(String error) {
		this.error = error;
	}

	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof RunResult))
			return false;
		RunResult other = (RunResult) o;
		return ok == other.ok && Objects.equals(output, other.output) && Objects.equals(error, other.error)
				&& Objects.equals(status, other.status);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ok, output, error, status);
	}

	@Override
	public String toString() {
		return "RunResult[ok=" + ok + ", output=" + output + ", error=" + error + ", status=" + status + "]";
	}
}
