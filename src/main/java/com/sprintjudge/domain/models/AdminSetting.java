package com.sprintjudge.domain.models;

import java.time.Instant;
import java.util.Objects;

public final class AdminSetting {

    private String key;

    private String value;

    private Instant updatedAt;

    public AdminSetting() {
    }

    public AdminSetting(String key, String value, Instant updatedAt) {
        this.key = key;
        this.value = value;
        this.updatedAt = updatedAt;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AdminSetting that = (AdminSetting) obj;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, updatedAt);
    }

    @Override
    public String toString() {
        return "AdminSetting[key=" + key + ", value=" + value + ", updatedAt=" + updatedAt + "]";
    }
}
