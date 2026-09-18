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
    public record StoredEvent(String roomId, long seq, String type, String actorId, String payloadJson,
            Instant createdAt) {
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
                .orderBy(Tables.EVT_SEQ.asc()).fetch(this::toStored);
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
        Long seq = RepoUtil.asLongBoxed(record.get(Tables.EVT_SEQ));
        String at = record.get(Tables.EVT_AT);
        return new StoredEvent(record.get(Tables.EVT_ROOM), seq == null ? 0L : seq, record.get(Tables.EVT_TYPE),
                record.get(Tables.EVT_ACTOR), record.get(Tables.EVT_PAYLOAD),
                at == null ? Instant.EPOCH : Instant.parse(at));
    }
}
