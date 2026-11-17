package com.sprintjudge.domain.models;

import org.eclipse.jdt.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;

public final class User {

	private String id;

	private String email;

	private String name;

	private String avatarUrl;

	private String role;

	private Instant createdAt;

	@SuppressWarnings("all")
	public User() {
	}

	public User(String id, String email, String name, String avatarUrl, String role, Instant createdAt) {
		this.id = id;
		this.email = email;
		this.name = name;
		this.avatarUrl = avatarUrl;
		this.role = role;
		this.createdAt = createdAt;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public void setAvatarUrl(String avatarUrl) {
		this.avatarUrl = avatarUrl;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		User user = (User) obj;
		return Objects.equals(id, user.id) && Objects.equals(email, user.email) && Objects.equals(name, user.name)
				&& Objects.equals(avatarUrl, user.avatarUrl) && Objects.equals(role, user.role)
				&& Objects.equals(createdAt, user.createdAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, email, name, avatarUrl, role, createdAt);
	}

	@Override
	public String toString() {
		return "User[id=" + id + ", email=" + email + ", name=" + name + ", avatarUrl=" + avatarUrl + ", role=" + role
				+ ", createdAt=" + createdAt + "]";
	}
}
