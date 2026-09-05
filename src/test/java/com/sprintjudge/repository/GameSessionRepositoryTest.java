package com.sprintjudge.repository;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.models.GameSession;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionRepositoryTest {

    private DSLContext dsl;
    private GameSessionRepository repo;

    @BeforeEach
    void setup() throws Exception {
        dsl = TestDb.inMemory();
        repo = new GameSessionRepository(dsl);
    }

    @Test
    void createSetsDefaults() {
        GameSession s = repo.create("quiz1", "host1", "123456", "{}");
        assertNotNull(s.id());
        assertEquals("LOBBY", s.status());
        assertNull(s.startedAt());
        assertNull(s.endedAt());
        assertNotNull(s.createdAt());
    }

    @Test
    void createWithNullOverride() {
        GameSession s = repo.create("quiz1", "host1", "654321", null);
        assertNull(s.settingsOverride());
    }

    @Test
    void findByPinFound() {
        repo.create("quiz1", "host1", "111111", "{}");
        assertTrue(repo.findByPin("111111").isPresent());
    }

    @Test
    void findByPinNotFound() {
        assertTrue(repo.findByPin("000000").isEmpty());
    }

    @Test
    void findByIdFound() {
        GameSession s = repo.create("quiz1", "host1", "222222", "{}");
        assertTrue(repo.findById(s.id()).isPresent());
    }

    @Test
    void findByIdNotFound() {
        assertTrue(repo.findById("x").isEmpty());
    }

    @Test
    void updateStatusActiveSetsStarted() {
        GameSession s = repo.create("quiz1", "host1", "333333", "{}");
        repo.updateStatus(s.id(), "ACTIVE");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("ACTIVE", got.status());
        assertNotNull(got.startedAt());
        assertNull(got.endedAt());
    }

    @Test
    void updateStatusEndedSetsEnded() {
        GameSession s = repo.create("quiz1", "host1", "444444", "{}");
        repo.updateStatus(s.id(), "ENDED");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("ENDED", got.status());
        assertNotNull(got.endedAt());
    }

    @Test
    void setCurrentIndex() {
        GameSession s = repo.create("quiz1", "host1", "555555", "{}");
        repo.setCurrentIndex(s.id(), 3);
        assertEquals(3, repo.findById(s.id()).orElseThrow().currentQuestionIndex());
    }

    @Test
    void setOverride() {
        GameSession s = repo.create("quiz1", "host1", "666666", "{}");
        repo.setOverride(s.id(), "override-json");
        assertEquals("override-json", repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void toSessionNullColumns() {
        dsl.insertInto(Tables.GAME_SESSIONS)
                .columns(Tables.SESS_ID, Tables.SESS_QUIZ_ID, Tables.SESS_PIN,
                        Tables.SESS_HOST, Tables.SESS_STATUS, Tables.SESS_INDEX)
                .values("sn", "quiz1", "777777", "host1", "LOBBY", 0)
                .execute();
        GameSession s = repo.findByPin("777777").orElseThrow();
        assertNull(s.startedAt());
        assertNull(s.endedAt());
        assertNull(s.createdAt());
    }

    @Test
    void updateStatusLobbySetsNoTimestamps() {
        GameSession s = repo.create("quiz1", "host1", "888881", "{}");
        repo.updateStatus(s.id(), "LOBBY");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("LOBBY", got.status());
        assertNull(got.startedAt());
        assertNull(got.endedAt());
    }

    @Test
    void updateStatusReviewSetsNoTimestamps() {
        GameSession s = repo.create("quiz1", "host1", "888882", "{}");
        repo.updateStatus(s.id(), "REVIEW");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("REVIEW", got.status());
        assertNull(got.startedAt());
        assertNull(got.endedAt());
    }

    @Test
    void activeThenEndedKeepsStarted() {
        GameSession s = repo.create("quiz1", "host1", "888883", "{}");
        repo.updateStatus(s.id(), "ACTIVE");
        repo.updateStatus(s.id(), "ENDED");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("ENDED", got.status());
        assertNotNull(got.startedAt());
        assertNotNull(got.endedAt());
    }

    @Test
    void setCurrentIndexTwice() {
        GameSession s = repo.create("quiz1", "host1", "888884", "{}");
        repo.setCurrentIndex(s.id(), 1);
        repo.setCurrentIndex(s.id(), 5);
        assertEquals(5, repo.findById(s.id()).orElseThrow().currentQuestionIndex());
    }

    @Test
    void setOverrideNull() {
        GameSession s = repo.create("quiz1", "host1", "888885", "{}");
        repo.setOverride(s.id(), null);
        assertNull(repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void findByPinAfterStatusChange() {
        GameSession s = repo.create("quiz1", "host1", "888886", null);
        repo.updateStatus(s.id(), "ACTIVE");
        assertTrue(repo.findByPin("888886").isPresent());
        assertEquals("ACTIVE", repo.findByPin("888886").orElseThrow().status());
    }
}
