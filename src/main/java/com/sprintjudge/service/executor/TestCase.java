package com.sprintjudge.service.executor;

import java.util.Objects;

public final class TestCase {
    private String input;
    private String expectedOutput;
    private boolean hidden;

    public TestCase() {}

    public TestCase(String input, String expectedOutput, boolean hidden) {
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.hidden = hidden;
    }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestCase)) return false;
        TestCase other = (TestCase) o;
        return hidden == other.hidden
                && Objects.equals(input, other.input)
                && Objects.equals(expectedOutput, other.expectedOutput);
    }

    @Override public int hashCode() { return Objects.hash(input, expectedOutput, hidden); }

    @Override public String toString() {
        return "TestCase[input=" + input + ", expectedOutput=" + expectedOutput
                + ", hidden=" + hidden + "]";
    }
}
