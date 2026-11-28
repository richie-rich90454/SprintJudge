package com.sprintjudge.domain.assignment;

import org.eclipse.jdt.annotation.Nullable;

/** Per-student progress and grade for one assignment. */
public final class StudentProgress {

	private String id;

	private String assignmentId;

	private String studentId;

	private String status;

	private @Nullable Long startedAt;

	private @Nullable Long submittedAt;

	private @Nullable Integer score;

	private @Nullable Integer maxScore;

	private @Nullable Long gradedAt;

	@SuppressWarnings("all")
	public StudentProgress() {
	}

	public StudentProgress(String id, String assignmentId, String studentId, String status, @Nullable Long startedAt,
			@Nullable Long submittedAt, @Nullable Integer score, @Nullable Integer maxScore, @Nullable Long gradedAt) {
		this.id = id;
		this.assignmentId = assignmentId;
		this.studentId = studentId;
		this.status = status;
		this.startedAt = startedAt;
		this.submittedAt = submittedAt;
		this.score = score;
		this.maxScore = maxScore;
		this.gradedAt = gradedAt;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getAssignmentId() {
		return assignmentId;
	}

	public void setAssignmentId(String assignmentId) {
		this.assignmentId = assignmentId;
	}

	public String getStudentId() {
		return studentId;
	}

	public void setStudentId(String studentId) {
		this.studentId = studentId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public @Nullable Long getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(@Nullable Long startedAt) {
		this.startedAt = startedAt;
	}

	public @Nullable Long getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(@Nullable Long submittedAt) {
		this.submittedAt = submittedAt;
	}

	public @Nullable Integer getScore() {
		return score;
	}

	public void setScore(@Nullable Integer score) {
		this.score = score;
	}

	public @Nullable Integer getMaxScore() {
		return maxScore;
	}

	public void setMaxScore(@Nullable Integer maxScore) {
		this.maxScore = maxScore;
	}

	public @Nullable Long getGradedAt() {
		return gradedAt;
	}

	public void setGradedAt(@Nullable Long gradedAt) {
		this.gradedAt = gradedAt;
	}
}
