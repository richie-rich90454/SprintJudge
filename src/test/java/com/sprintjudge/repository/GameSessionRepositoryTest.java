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

    @Test
    void mxSetCurrentIndexZero() {
        GameSession s = repo.create("quiz1", "host1", "900001", null);
        repo.setCurrentIndex(s.id(), 7);
        repo.setCurrentIndex(s.id(), 0);
        assertEquals(0, repo.findById(s.id()).orElseThrow().currentQuestionIndex());
    }

    @Test
    void mxSetCurrentIndexNegative() {
        GameSession s = repo.create("quiz1", "host1", "900002", null);
        repo.setCurrentIndex(s.id(), -3);
        assertEquals(-3, repo.findById(s.id()).orElseThrow().currentQuestionIndex());
    }

    @Test
    void mxSetCurrentIndexLarge() {
        GameSession s = repo.create("quiz1", "host1", "900003", null);
        repo.setCurrentIndex(s.id(), Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, repo.findById(s.id()).orElseThrow().currentQuestionIndex());
    }

    @Test
    void mxUpdateStatusSameActiveTwiceKeepsStarted() {
        GameSession s = repo.create("quiz1", "host1", "900004", null);
        repo.updateStatus(s.id(), "ACTIVE");
        repo.updateStatus(s.id(), "ACTIVE");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("ACTIVE", got.status());
        assertNotNull(got.startedAt());
    }

    @Test
    void mxUpdateStatusMissingIdIsNoop() {
        repo.updateStatus("ghost", "ACTIVE");
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void mxSetOverrideMissingIdIsNoop() {
        repo.setOverride("ghost", "x");
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void mxSetCurrentIndexMissingIdIsNoop() {
        repo.setCurrentIndex("ghost", 9);
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void mxSetOverrideEmptyStringRoundTrips() {
        GameSession s = repo.create("quiz1", "host1", "900005", null);
        repo.setOverride(s.id(), "");
        assertEquals("", repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void mxSetOverrideUnicodeRoundTrips() {
        GameSession s = repo.create("quiz1", "host1", "900006", null);
        repo.setOverride(s.id(), "{\"t\":\"h\u00e9llo \u4e2d\u6587\"}");
        assertEquals("{\"t\":\"h\u00e9llo \u4e2d\u6587\"}",
                repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void mxSetOverrideLongJsonRoundTrips() {
        GameSession s = repo.create("quiz1", "host1", "900007", null);
        String big = "{\"pads\":[" + "\"x\",".repeat(2000) + "\"y\"]}";
        repo.setOverride(s.id(), big);
        assertEquals(big, repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void mxBulk20AllPinsFindable() {
        for (int i = 0; i < 20; i++) repo.create("quiz1", "h", String.format("91%04d", i), null);
        assertEquals(20, countSessions());
        assertTrue(repo.findByPin("910000").isPresent());
        assertTrue(repo.findByPin("910019").isPresent());
        assertTrue(repo.findByPin("919999").isEmpty());
    }

    private int countSessions() {
        int n = 0;
        for (int i = 0; i < 20; i++) if (repo.findByPin(String.format("91%04d", i)).isPresent()) n++;
        return n;
    }

    @Test
    void mxFindByPinMissingAmongMany() {
        repo.create("quiz1", "host1", "900008", "{}");
        repo.create("quiz1", "host1", "900009", "{}");
        assertTrue(repo.findByPin("900000").isEmpty());
    }

    @Test
    void mxStatusActiveThenLobbyKeepsStarted() {
        GameSession s = repo.create("quiz1", "host1", "900010", null);
        repo.updateStatus(s.id(), "ACTIVE");
        repo.updateStatus(s.id(), "LOBBY");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("LOBBY", got.status());
        assertNotNull(got.startedAt());
    }

    @Test
    void mxCreateOverrideJsonRoundTrips() {
        GameSession s = repo.create("quiz1", "host1", "900011", "{\"timer\":30}");
        assertEquals("{\"timer\":30}", repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void mxCreateReturnsDistinctIds() {
        GameSession a = repo.create("quiz1", "host1", "900012", null);
        GameSession b = repo.create("quiz1", "host1", "900013", null);
        assertTrue(!a.id().equals(b.id()));
        assertTrue(repo.findById(a.id()).isPresent());
        assertTrue(repo.findById(b.id()).isPresent());
    }

    @Test
    void mxFindByIdAfterStatusChange() {
        GameSession s = repo.create("quiz1", "host1", "900014", null);
        repo.updateStatus(s.id(), "ENDED");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("ENDED", got.status());
        assertNotNull(got.endedAt());
    }

    @Test
    void mxEndedWithoutActiveLeavesStartedNull() {
        GameSession s = repo.create("quiz1", "host1", "900015", null);
        repo.updateStatus(s.id(), "ENDED");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("ENDED", got.status());
        assertNotNull(got.endedAt());
        assertNull(got.startedAt());
    }

    @Test
    void mxUpdateStatusEndedTwiceKeepsEnded() {
        GameSession s = repo.create("quiz1", "host1", "900016", null);
        repo.updateStatus(s.id(), "ENDED");
        repo.updateStatus(s.id(), "ENDED");
        assertEquals("ENDED", repo.findById(s.id()).orElseThrow().status());
    }

    @Test
    void mxSetOverrideTwiceKeepsLast() {
        GameSession s = repo.create("quiz1", "host1", "900017", null);
        repo.setOverride(s.id(), "first");
        repo.setOverride(s.id(), "second");
        assertEquals("second", repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void mxFindByPinIsExactMatch() {
        repo.create("quiz1", "host1", "900018", null);
        assertTrue(repo.findByPin("900018 ").isEmpty());
        assertTrue(repo.findByPin("90001").isEmpty());
        assertTrue(repo.findByPin("900018").isPresent());
    }

    @Test
    void mxCreatePreservesHostAndQuiz() {
        GameSession s = repo.create("quiz-abc", "host-xyz", "900019", null);
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("quiz-abc", got.quizId());
        assertEquals("host-xyz", got.hostUserId());
        assertEquals("900019", got.pinCode());
    }
}
