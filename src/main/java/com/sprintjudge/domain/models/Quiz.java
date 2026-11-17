package com.sprintjudge.domain.models;

import org.eclipse.jdt.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class Quiz {

	private String id;

	@NotBlank
	@Size(max = 200)
	private String title;

	@Size(max = 4000)
	private String description;

	private String createdBy;

	private Instant createdAt;

	private boolean template;

	@SuppressWarnings("null")
	public Quiz() {
	}

	public Quiz(String id, String title, String description, String createdBy, Instant createdAt, boolean template) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.template = template;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public boolean isTemplate() {
		return template;
	}

	public void setTemplate(boolean template) {
		this.template = template;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		Quiz quiz = (Quiz) obj;
		return template == quiz.template && Objects.equals(id, quiz.id) && Objects.equals(title, quiz.title)
				&& Objects.equals(description, quiz.description) && Objects.equals(createdBy, quiz.createdBy)
				&& Objects.equals(createdAt, quiz.createdAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, title, description, createdBy, createdAt, template);
	}

	@Override
	public String toString() {
		return "Quiz[id=" + id + ", title=" + title + ", description=" + description + ", createdBy=" + createdBy
				+ ", createdAt=" + createdAt + ", template=" + template + "]";
	}
}
