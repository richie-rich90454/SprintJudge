package com.sprintjudge.domain.dto;

import org.eclipse.jdt.annotation.Nullable;
import java.util.Objects;

public final class ErrorMessage {
	private String type;
	private String message;

	@SuppressWarnings("null")
	public ErrorMessage() {
	}

	public ErrorMessage(String type, String message) {
		this.type = type;
		this.message = message;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ErrorMessage))
			return false;
		ErrorMessage other = (ErrorMessage) o;
		return Objects.equals(type, other.type) && Objects.equals(message, other.message);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, message);
	}

	@Override
	public String toString() {
		return "ErrorMessage[type=" + type + ", message=" + message + "]";
	}
}
