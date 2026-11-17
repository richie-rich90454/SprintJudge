package com.sprintjudge.domain.dto;

import java.util.List;
import java.util.Objects;

public final class QuestionDto {
	private String id;
	private String type;
	private String title;
	private String description;
	private int timeLimitSec;
	private int pointsBase;
	private List<String> languagesAllowed;
	private Object config;

	public QuestionDto() {
	}

	public QuestionDto(String id, String type, String title, String description, int timeLimitSec, int pointsBase,
			List<String> languagesAllowed, Object config) {
		this.id = id;
		this.type = type;
		this.title = title;
		this.description = description;
		this.timeLimitSec = timeLimitSec;
		this.pointsBase = pointsBase;
		this.languagesAllowed = languagesAllowed;
		this.config = config;
	}

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
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

	public int getTimeLimitSec() {
		return timeLimitSec;
	}
	public void setTimeLimitSec(int timeLimitSec) {
		this.timeLimitSec = timeLimitSec;
	}

	public int getPointsBase() {
		return pointsBase;
	}
	public void setPointsBase(int pointsBase) {
		this.pointsBase = pointsBase;
	}

	public List<String> getLanguagesAllowed() {
		return languagesAllowed;
	}
	public void setLanguagesAllowed(List<String> languagesAllowed) {
		this.languagesAllowed = languagesAllowed;
	}

	public Object getConfig() {
		return config;
	}
	public void setConfig(Object config) {
		this.config = config;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof QuestionDto))
			return false;
		QuestionDto other = (QuestionDto) o;
		return timeLimitSec == other.timeLimitSec && pointsBase == other.pointsBase && Objects.equals(id, other.id)
				&& Objects.equals(type, other.type) && Objects.equals(title, other.title)
				&& Objects.equals(description, other.description)
				&& Objects.equals(languagesAllowed, other.languagesAllowed) && Objects.equals(config, other.config);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, type, title, description, timeLimitSec, pointsBase, languagesAllowed, config);
	}

	@Override
	public String toString() {
		return "QuestionDto[id=" + id + ", type=" + type + ", title=" + title + ", description=" + description
				+ ", timeLimitSec=" + timeLimitSec + ", pointsBase=" + pointsBase + ", languagesAllowed="
				+ languagesAllowed + ", config=" + config + "]";
	}
}
