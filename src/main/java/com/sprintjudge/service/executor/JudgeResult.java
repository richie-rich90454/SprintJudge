package com.sprintjudge.service.executor;

import org.eclipse.jdt.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public final class JudgeResult {
	private int passed;
	private int total;
	private boolean allPassed;
	private List<CaseResult> cases;

	@SuppressWarnings("null")
	public JudgeResult() {
	}

	public JudgeResult(int passed, int total, boolean allPassed, List<CaseResult> cases) {
		this.passed = passed;
		this.total = total;
		this.allPassed = allPassed;
		this.cases = cases;
	}

	public int getPassed() {
		return passed;
	}
	public void setPassed(int passed) {
		this.passed = passed;
	}

	public int getTotal() {
		return total;
	}
	public void setTotal(int total) {
		this.total = total;
	}

	public boolean isAllPassed() {
		return allPassed;
	}
	public void setAllPassed(boolean allPassed) {
		this.allPassed = allPassed;
	}

	public List<CaseResult> getCases() {
		return cases;
	}
	public void setCases(List<CaseResult> cases) {
		this.cases = cases;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o)
			return true;
		if (!(o instanceof JudgeResult))
			return false;
		JudgeResult other = (JudgeResult) o;
		return passed == other.passed && total == other.total && allPassed == other.allPassed
				&& Objects.equals(cases, other.cases);
	}

	@Override
	public int hashCode() {
		return Objects.hash(passed, total, allPassed, cases);
	}

	@Override
	public String toString() {
		return "JudgeResult[passed=" + passed + ", total=" + total + ", allPassed=" + allPassed + ", cases=" + cases
				+ "]";
	}

	public static final class CaseResult {
		private int index;
		private boolean passed;
		private String expected;
		private String actual;
		private String error;

		@SuppressWarnings("null")
		public CaseResult() {
		}

		public CaseResult(int index, boolean passed, String expected, String actual, String error) {
			this.index = index;
			this.passed = passed;
			this.expected = expected;
			this.actual = actual;
			this.error = error;
		}

		public int getIndex() {
			return index;
		}
		public void setIndex(int index) {
			this.index = index;
		}

		public boolean isPassed() {
			return passed;
		}
		public void setPassed(boolean passed) {
			this.passed = passed;
		}

		public String getExpected() {
			return expected;
		}
		public void setExpected(String expected) {
			this.expected = expected;
		}

		public String getActual() {
			return actual;
		}
		public void setActual(String actual) {
			this.actual = actual;
		}

		public String getError() {
			return error;
		}
		public void setError(String error) {
			this.error = error;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (!(o instanceof CaseResult))
				return false;
			CaseResult other = (CaseResult) o;
			return index == other.index && passed == other.passed && Objects.equals(expected, other.expected)
					&& Objects.equals(actual, other.actual) && Objects.equals(error, other.error);
		}

		@Override
		public int hashCode() {
			return Objects.hash(index, passed, expected, actual, error);
		}

		@Override
		public String toString() {
			return "CaseResult[index=" + index + ", passed=" + passed + ", expected=" + expected + ", actual=" + actual
					+ ", error=" + error + "]";
		}
	}
}
