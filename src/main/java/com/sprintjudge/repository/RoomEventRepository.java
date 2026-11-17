package com.sprintjudge.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import com.sprintjudge.util.RepoUtil;

/**
 * Append-only event store. Writers never update or delete; readers fold.
 */
@Repository
public class RoomEventRepository {

    private final DSLContext dsl;

    public RoomEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Stored row for replay and audit. */
    public static final class StoredEvent {
        private String roomId;
        private long seq;
        private String type;
        private String actorId;
        private String payloadJson;
        private Instant createdAt;

        public StoredEvent() {
        }

        public StoredEvent(String roomId, long seq, String type, String actorId, String payloadJson, Instant createdAt) {
            this.roomId = roomId;
            this.seq = seq;
            this.type = type;
            this.actorId = actorId;
            this.payloadJson = payloadJson;
            this.createdAt = createdAt;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public long getSeq() {
            return seq;
        }

        public void setSeq(long seq) {
            this.seq = seq;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getActorId() {
            return actorId;
        }

        public void setActorId(String actorId) {
            this.actorId = actorId;
        }

        public String getPayloadJson() {
            return payloadJson;
        }

        public void setPayloadJson(String payloadJson) {
            this.payloadJson = payloadJson;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StoredEvent that = (StoredEvent) o;
            return seq == that.seq
                    && java.util.Objects.equals(roomId, that.roomId)
                    && java.util.Objects.equals(type, that.type)
                    && java.util.Objects.equals(actorId, that.actorId)
                    && java.util.Objects.equals(payloadJson, that.payloadJson)
                    && java.util.Objects.equals(createdAt, that.createdAt);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(roomId, seq, type, actorId, payloadJson, createdAt);
        }

        @Override
        public String toString() {
            return "StoredEvent[roomId=" + roomId + ", seq=" + seq + ", type=" + type + ", actorId=" + actorId
                    + ", payloadJson=" + payloadJson + ", createdAt=" + createdAt + "]";
        }
    }

    /**
     * Append one event. Duplicate sequence numbers are ignored so retried
     * broadcasts stay idempotent.
     */
    public void append(String roomId, long seq, String type, String actorId, String payloadJson) {
        dsl.insertInto(Tables.ROOM_EVENTS)
                .columns(Tables.EVT_ROOM, Tables.EVT_SEQ, Tables.EVT_TYPE, Tables.EVT_ACTOR,
                        Tables.EVT_PAYLOAD, Tables.EVT_AT)
                .values(roomId, seq, type, actorId, payloadJson, Instant.now().toString())
                .onConflict(Tables.EVT_ROOM, Tables.EVT_SEQ).doNothing()
                .execute();
    }

    /** Events after a sequence number, in order, for resume catch-up. */
    public List<StoredEvent> eventsAfter(String roomId, long seq) {
        return dsl.selectFrom(Tables.ROOM_EVENTS).where(Tables.EVT_ROOM.eq(roomId).and(Tables.EVT_SEQ.gt(seq)))
                .orderBy(Tables.EVT_SEQ.asc()).fetch(new org.jooq.RecordMapper<org.jooq.Record, StoredEvent>() {
                    @Override
                    public StoredEvent map(org.jooq.Record r) {
                        return toStored(r);
                    }
                });
    }

    /** Highest stored sequence for a room, empty when no events exist. */
    public Optional<Long> latestSeq(String roomId) {
        Long max = dsl.select(org.jooq.impl.DSL.max(Tables.EVT_SEQ)).from(Tables.ROOM_EVENTS)
                .where(Tables.EVT_ROOM.eq(roomId)).fetchOne(0, Long.class);
        if (max != null) {
            return Optional.of(max);
        }
        return Optional.empty();
    }

    private StoredEvent toStored(org.jooq.Record record) {
        // seq and created_at are NOT NULL columns written only by append()
        long seq = RepoUtil.asLong(record.get(Tables.EVT_SEQ));
        String at = record.get(Tables.EVT_AT);
        return new StoredEvent(record.get(Tables.EVT_ROOM), seq, record.get(Tables.EVT_TYPE),
                record.get(Tables.EVT_ACTOR), record.get(Tables.EVT_PAYLOAD), Instant.parse(at));
    }
}