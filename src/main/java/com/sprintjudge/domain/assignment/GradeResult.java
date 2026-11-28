package com.sprintjudge.domain.assignment;

/**
 * Outcome of recording a grade, including the generic LMS grade-passback
 * payload. The payload is a documented placeholder, not a signed AGS call.
 */
public final class GradeResult {

	private String assignmentId;

	private String studentId;

	private int score;

	private int maxScore;

	private String grade;

	private String passbackPayload;

	@SuppressWarnings("all")
	public GradeResult() {
	}

	public GradeResult(String assignmentId, String studentId, int score, int maxScore, String grade,
			String passbackPayload) {
		this.assignmentId = assignmentId;
		this.studentId = studentId;
		this.score = score;
		this.maxScore = maxScore;
		this.grade = grade;
		this.passbackPayload = passbackPayload;
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

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public int getMaxScore() {
		return maxScore;
	}

	public void setMaxScore(int maxScore) {
		this.maxScore = maxScore;
	}

	public String getGrade() {
		return grade;
	}

	public void setGrade(String grade) {
		this.grade = grade;
	}

	public String getPassbackPayload() {
		return passbackPayload;
	}

	public void setPassbackPayload(String passbackPayload) {
		this.passbackPayload = passbackPayload;
	}
}
