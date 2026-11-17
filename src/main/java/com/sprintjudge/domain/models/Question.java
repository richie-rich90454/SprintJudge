package com.sprintjudge.domain.models;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class Question {

    private String id;

    private String quizId;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 4000)
    private String description;

    private String questionType;

    private List<String> languagesAllowed;

    @Min(1)
    private int timeLimitSec;

    @Min(0)
    private int pointsBase;

    private String config;

    private int orderIndex;

    private Instant createdAt;

    public Question() {
    }

    public Question(String id, String quizId, String title, String description, String questionType,
            List<String> languagesAllowed, int timeLimitSec, int pointsBase, String config, int orderIndex,
            Instant createdAt) {
        this.id = id;
        this.quizId = quizId;
        this.title = title;
        this.description = description;
        this.questionType = questionType;
        this.languagesAllowed = languagesAllowed;
        this.timeLimitSec = timeLimitSec;
        this.pointsBase = pointsBase;
        this.config = config;
        this.orderIndex = orderIndex;
        this.createdAt = createdAt;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public List<String> getLanguagesAllowed() {
        return languagesAllowed;
    }

    public void setLanguagesAllowed(List<String> languagesAllowed) {
        this.languagesAllowed = languagesAllowed;
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

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Question question = (Question) obj;
        return timeLimitSec == question.timeLimitSec && pointsBase == question.pointsBase
                && orderIndex == question.orderIndex && Objects.equals(id, question.id)
                && Objects.equals(quizId, question.quizId) && Objects.equals(title, question.title)
                && Objects.equals(description, question.description)
                && Objects.equals(questionType, question.questionType)
                && Objects.equals(languagesAllowed, question.languagesAllowed)
                && Objects.equals(config, question.config) && Objects.equals(createdAt, question.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, quizId, title, description, questionType, languagesAllowed, timeLimitSec,
                pointsBase, config, orderIndex, createdAt);
    }

    @Override
    public String toString() {
        return "Question[id=" + id + ", quizId=" + quizId + ", title=" + title + ", description=" + description
                + ", questionType=" + questionType + ", languagesAllowed=" + languagesAllowed + ", timeLimitSec="
                + timeLimitSec + ", pointsBase=" + pointsBase + ", config=" + config + ", orderIndex=" + orderIndex
                + ", createdAt=" + createdAt + "]";
    }
}
