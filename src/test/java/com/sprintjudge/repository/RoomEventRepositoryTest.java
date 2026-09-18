package com.sprintjudge.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sprintjudge.TestDb;

/** Append-only store stays idempotent and replays in order. */
class RoomEventRepositoryTest {

    private DSLContext dsl;
    private RoomEventRepository repo;

    @BeforeEach
    void setup() throws Exception {
        dsl = TestDb.inMemory();
        repo = new RoomEventRepository(dsl);
    }

    @Test
    void appendThenReplayAfterSeq() {
        repo.append("r1", 1, "RoomCreated", null, "{}");
        repo.append("r1", 2, "PlayerJoined", "p1", "{\"nickname\":\"Ada\"}");
        repo.append("r1", 2, "PlayerJoined", "p1", "{\"nickname\":\"Ada\"}");
        List<RoomEventRepository.StoredEvent> after = repo.eventsAfter("r1", 1);
        assertEquals(1, after.size());
        assertEquals(2L, after.get(0).seq());
        assertEquals("PlayerJoined", after.get(0).type());
    }

    @Test
    void latestSeqEmptyThenPresent() {
        assertTrue(repo.latestSeq("missing").isEmpty());
        repo.append("r2", 5, "GameEnded", null, "{}");
        assertEquals(5L, repo.latestSeq("r2").orElseThrow());
    }

    @Test
    void emptyReplayWhenNothingAfter() {
        repo.append("r3", 1, "RoomCreated", null, "{}");
        assertTrue(repo.eventsAfter("r3", 1).isEmpty());
    }
}
