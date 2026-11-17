package com.sprintjudge.domain.dto;

import java.util.Objects;

public final class TimerUpdate {
    private String type;
    private long newEndEpochMs;
    private long extendSec;

    public TimerUpdate() {}

    public TimerUpdate(String type, long newEndEpochMs, long extendSec) {
        this.type = type;
        this.newEndEpochMs = newEndEpochMs;
        this.extendSec = extendSec;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getNewEndEpochMs() { return newEndEpochMs; }
    public void setNewEndEpochMs(long newEndEpochMs) { this.newEndEpochMs = newEndEpochMs; }

    public long getExtendSec() { return extendSec; }
    public void setExtendSec(long extendSec) { this.extendSec = extendSec; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimerUpdate)) return false;
        TimerUpdate other = (TimerUpdate) o;
        return newEndEpochMs == other.newEndEpochMs
                && extendSec == other.extendSec
                && Objects.equals(type, other.type);
    }

    @Override public int hashCode() { return Objects.hash(type, newEndEpochMs, extendSec); }

    @Override public String toString() {
        return "TimerUpdate[type=" + type
                + ", newEndEpochMs=" + newEndEpochMs
                + ", extendSec=" + extendSec + "]";
    }
}
