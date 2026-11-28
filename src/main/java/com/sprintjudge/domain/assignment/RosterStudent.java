package com.sprintjudge.domain.assignment;

import org.eclipse.jdt.annotation.Nullable;

/** One student in an assignment roster, imported from CSV or an LMS. */
public final class RosterStudent {

	private String id;

	private String assignmentId;

	private String studentId;

	private String name;

	private @Nullable String email;

	@SuppressWarnings("all")
	public RosterStudent() {
	}

	public RosterStudent(String id, String assignmentId, String studentId, String name, @Nullable String email) {
		this.id = id;
		this.assignmentId = assignmentId;
		this.studentId = studentId;
		this.name = name;
		this.email = email;
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public @Nullable String getEmail() {
		return email;
	}

	public void setEmail(@Nullable String email) {
		this.email = email;
	}
}
