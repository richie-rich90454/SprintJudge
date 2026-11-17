package com.sprintjudge.domain.dto.export;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExportBundle {
	private String version;
	private long exportedAt;
	private List<QuizExport> quizzes;
	private Map<String, String> adminSettings;

	public ExportBundle() {
	}

	public ExportBundle(String version, long exportedAt, List<QuizExport> quizzes, Map<String, String> adminSettings) {
		this.version = version;
		this.exportedAt = exportedAt;
		this.quizzes = quizzes;
		this.adminSettings = adminSettings;
	}

	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}

	public long getExportedAt() {
		return exportedAt;
	}
	public void setExportedAt(long exportedAt) {
		this.exportedAt = exportedAt;
	}

	public List<QuizExport> getQuizzes() {
		return quizzes;
	}
	public void setQuizzes(List<QuizExport> quizzes) {
		this.quizzes = quizzes;
	}

	public Map<String, String> getAdminSettings() {
		return adminSettings;
	}
	public void setAdminSettings(Map<String, String> adminSettings) {
		this.adminSettings = adminSettings;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ExportBundle))
			return false;
		ExportBundle other = (ExportBundle) o;
		return exportedAt == other.exportedAt && Objects.equals(version, other.version)
				&& Objects.equals(quizzes, other.quizzes) && Objects.equals(adminSettings, other.adminSettings);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, exportedAt, quizzes, adminSettings);
	}

	@Override
	public String toString() {
		return "ExportBundle[version=" + version + ", exportedAt=" + exportedAt + ", quizzes=" + quizzes
				+ ", adminSettings=" + adminSettings + "]";
	}

	public static final class QuizExport {
		private String id;
		private String title;
		private String description;
		private boolean template;
		private List<QuestionExport> questions;

		public QuizExport() {
		}

		public QuizExport(String id, String title, String description, boolean template,
				List<QuestionExport> questions) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.template = template;
			this.questions = questions;
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

		public boolean isTemplate() {
			return template;
		}
		public void setTemplate(boolean template) {
			this.template = template;
		}

		public List<QuestionExport> getQuestions() {
			return questions;
		}
		public void setQuestions(List<QuestionExport> questions) {
			this.questions = questions;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof QuizExport))
				return false;
			QuizExport other = (QuizExport) o;
			return template == other.template && Objects.equals(id, other.id) && Objects.equals(title, other.title)
					&& Objects.equals(description, other.description) && Objects.equals(questions, other.questions);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, title, description, template, questions);
		}

		@Override
		public String toString() {
			return "QuizExport[id=" + id + ", title=" + title + ", description=" + description + ", template="
					+ template + ", questions=" + questions + "]";
		}
	}

	public static final class QuestionExport {
		private String id;
		private String type;
		private String title;
		private String description;
		private int timeLimitSec;
		private int pointsBase;
		private Map<String, Object> config;
		private List<String> languagesAllowed;

		public QuestionExport() {
		}

		public QuestionExport(String id, String type, String title, String description, int timeLimitSec,
				int pointsBase, Map<String, Object> config, List<String> languagesAllowed) {
			this.id = id;
			this.type = type;
			this.title = title;
			this.description = description;
			this.timeLimitSec = timeLimitSec;
			this.pointsBase = pointsBase;
			this.config = config;
			this.languagesAllowed = languagesAllowed;
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

		public Map<String, Object> getConfig() {
			return config;
		}
		public void setConfig(Map<String, Object> config) {
			this.config = config;
		}

		public List<String> getLanguagesAllowed() {
			return languagesAllowed;
		}
		public void setLanguagesAllowed(List<String> languagesAllowed) {
			this.languagesAllowed = languagesAllowed;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof QuestionExport))
				return false;
			QuestionExport other = (QuestionExport) o;
			return timeLimitSec == other.timeLimitSec && pointsBase == other.pointsBase && Objects.equals(id, other.id)
					&& Objects.equals(type, other.type) && Objects.equals(title, other.title)
					&& Objects.equals(description, other.description) && Objects.equals(config, other.config)
					&& Objects.equals(languagesAllowed, other.languagesAllowed);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, type, title, description, timeLimitSec, pointsBase, config, languagesAllowed);
		}

		@Override
		public String toString() {
			return "QuestionExport[id=" + id + ", type=" + type + ", title=" + title + ", description=" + description
					+ ", timeLimitSec=" + timeLimitSec + ", pointsBase=" + pointsBase + ", config=" + config
					+ ", languagesAllowed=" + languagesAllowed + "]";
		}
	}
}
