package com.sprintjudge.domain.assignment;

import org.eclipse.jdt.annotation.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An async assignment created from a quiz, with an optional release/due window. */
public final class Assignment {

	private String id;

	@NotBlank
	@Size(max = 200)
	private String quizId;

	@NotBlank
	@Size(max = 200)
	private String title;

	private @Nullable String description;

	/** Epoch seconds; null means available immediately. */
	private @Nullable Long releaseAt;

	/** Epoch seconds; null means no deadline. */
	private @Nullable Long dueAt;

	private String createdBy;

	private Long createdAt;

	private @Nullable String ltiDeploymentId;

	@SuppressWarnings("all")
	public Assignment() {
	}

	public Assignment(String id, String quizId, String title, @Nullable String description, @Nullable Long releaseAt,
			@Nullable Long dueAt, String createdBy, Long createdAt, @Nullable String ltiDeploymentId) {
		this.id = id;
		this.quizId = quizId;
		this.title = title;
		this.description = description;
		this.releaseAt = releaseAt;
		this.dueAt = dueAt;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.ltiDeploymentId = ltiDeploymentId;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getQuizId() {
		return quizId;
	}

	public void setQuizId(String quizId) {
		this.quizId = quizId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public @Nullable String getDescription() {
		return description;
	}

	public void setDescription(@Nullable String description) {
		this.description = description;
	}

	public @Nullable Long getReleaseAt() {
		return releaseAt;
	}

	public void setReleaseAt(@Nullable Long releaseAt) {
		this.releaseAt = releaseAt;
	}

	public @Nullable Long getDueAt() {
		return dueAt;
	}

	public void setDueAt(@Nullable Long dueAt) {
		this.dueAt = dueAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public Long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Long createdAt) {
		this.createdAt = createdAt;
	}

	public @Nullable String getLtiDeploymentId() {
		return ltiDeploymentId;
	}

	public void setLtiDeploymentId(@Nullable String ltiDeploymentId) {
		this.ltiDeploymentId = ltiDeploymentId;
	}
}
