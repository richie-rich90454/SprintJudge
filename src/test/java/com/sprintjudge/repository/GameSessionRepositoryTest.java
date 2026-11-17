package com.sprintjudge.repository;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.models.GameSession;
import org.jooq.DSLContext;



import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void findAllNonEndedEmpty() {
        assertTrue(repo.findAllNonEnded().isEmpty());
    }

    @Test
    void findAllNonEndedFiltersEnded() {
        repo.create("quiz1", "host1", "111111", "{}");
        GameSession s = repo.create("quiz1", "host1", "222222", "{}");
        repo.updateStatus(s.getId(), "ENDED");
        java.util.List<GameSession> all = repo.findAllNonEnded();
        assertEquals(1, all.size());
        assertEquals("111111", all.get(0).getPinCode());
    }

    @Test
    void createSetsDefaults() {
        GameSession s = repo.create("quiz1", "host1", "123456", "{}");
        assertNotNull(s.getId());
        assertEquals("LOBBY", s.getStatus());
        assertNull(s.getStartedAt());
        assertNull(s.getEndedAt());
        assertNotNull(s.getCreatedAt());
    }

    @Test
    void createWithNullOverride() {
        GameSession s = repo.create("quiz1", "host1", "654321", null);
        assertNull(s.getSettingsOverride());
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
        assertTrue(repo.findById(s.getId()).isPresent());
    }

    @Test
    void findByIdNotFound() {
        assertTrue(repo.findById("x").isEmpty());
    }

    @Test
    void updateStatusActiveSetsStarted() {
        GameSession s = repo.create("quiz1", "host1", "333333", "{}");
        repo.updateStatus(s.getId(), "ACTIVE");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("ACTIVE", got.getStatus());
        assertNotNull(got.getStartedAt());
        assertNull(got.getEndedAt());
    }

    @Test
    void updateStatusEndedSetsEnded() {
        GameSession s = repo.create("quiz1", "host1", "444444", "{}");
        repo.updateStatus(s.getId(), "ENDED");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("ENDED", got.getStatus());
        assertNotNull(got.getEndedAt());
    }

    @Test
    void setCurrentIndex() {
        GameSession s = repo.create("quiz1", "host1", "555555", "{}");
        repo.setCurrentIndex(s.getId(), 3);
        assertEquals(3, repo.findById(s.getId()).orElseThrow().getCurrentQuestionIndex());
    }

    @Test
    void setOverride() {
        GameSession s = repo.create("quiz1", "host1", "666666", "{}");
        repo.setOverride(s.getId(), "override-json");
        assertEquals("override-json", repo.findById(s.getId()).orElseThrow().getSettingsOverride());
    }

    @Test
    void toSessionNullColumns() {
        dsl.insertInto(Tables.GAME_SESSIONS)
                .columns(Tables.SESS_ID, Tables.SESS_QUIZ_ID, Tables.SESS_PIN,
                        Tables.SESS_HOST, Tables.SESS_STATUS, Tables.SESS_INDEX)
                .values("sn", "quiz1", "777777", "host1", "LOBBY", 0)
                .execute();
        GameSession s = repo.findByPin("777777").orElseThrow();
        assertNull(s.getStartedAt());
        assertNull(s.getEndedAt());
        assertNull(s.getCreatedAt());
    }

    @Test
    void updateStatusLobbySetsNoTimestamps() {
        GameSession s = repo.create("quiz1", "host1", "888881", "{}");
        repo.updateStatus(s.getId(), "LOBBY");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("LOBBY", got.getStatus());
        assertNull(got.getStartedAt());
        assertNull(got.getEndedAt());
    }

    @Test
    void updateStatusReviewSetsNoTimestamps() {
        GameSession s = repo.create("quiz1", "host1", "888882", "{}");
        repo.updateStatus(s.getId(), "REVIEW");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("REVIEW", got.getStatus());
        assertNull(got.getStartedAt());
        assertNull(got.getEndedAt());
    }

    @Test
    void activeThenEndedKeepsStarted() {
        GameSession s = repo.create("quiz1", "host1", "888883", "{}");
        repo.updateStatus(s.getId(), "ACTIVE");
        repo.updateStatus(s.getId(), "ENDED");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("ENDED", got.getStatus());
        assertNotNull(got.getStartedAt());
        assertNotNull(got.getEndedAt());
    }

    @Test
    void setCurrentIndexTwice() {
        GameSession s = repo.create("quiz1", "host1", "888884", "{}");
        repo.setCurrentIndex(s.getId(), 1);
        repo.setCurrentIndex(s.getId(), 5);
        assertEquals(5, repo.findById(s.getId()).orElseThrow().getCurrentQuestionIndex());
    }

    @Test
    void setOverrideNull() {
        GameSession s = repo.create("quiz1", "host1", "888885", "{}");
        repo.setOverride(s.getId(), null);
        assertNull(repo.findById(s.getId()).orElseThrow().getSettingsOverride());
    }

    @Test
    void findByPinAfterStatusChange() {
        GameSession s = repo.create("quiz1", "host1", "888886", null);
        repo.updateStatus(s.getId(), "ACTIVE");
        assertTrue(repo.findByPin("888886").isPresent());
        assertEquals("ACTIVE", repo.findByPin("888886").orElseThrow().getStatus());
    }

    @Test
    void mxSetCurrentIndexZero() {
        GameSession s = repo.create("quiz1", "host1", "900001", null);
        repo.setCurrentIndex(s.getId(), 7);
        repo.setCurrentIndex(s.getId(), 0);
        assertEquals(0, repo.findById(s.getId()).orElseThrow().getCurrentQuestionIndex());
    }

    @Test
    void mxSetCurrentIndexNegative() {
        GameSession s = repo.create("quiz1", "host1", "900002", null);
        repo.setCurrentIndex(s.getId(), -3);
        assertEquals(-3, repo.findById(s.getId()).orElseThrow().getCurrentQuestionIndex());
    }

    @Test
    void mxSetCurrentIndexLarge() {
        GameSession s = repo.create("quiz1", "host1", "900003", null);
        repo.setCurrentIndex(s.getId(), Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, repo.findById(s.getId()).orElseThrow().getCurrentQuestionIndex());
    }

    @Test
    void mxUpdateStatusSameActiveTwiceKeepsStarted() {
        GameSession s = repo.create("quiz1", "host1", "900004", null);
        repo.updateStatus(s.getId(), "ACTIVE");
        repo.updateStatus(s.getId(), "ACTIVE");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("ACTIVE", got.getStatus());
        assertNotNull(got.getStartedAt());
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
        repo.setOverride(s.getId(), "");
        assertEquals("", repo.findById(s.getId()).orElseThrow().getSettingsOverride());
    }

    @Test
    void mxSetOverrideUnicodeRoundTrips() {
        GameSession s = repo.create("quiz1", "host1", "900006", null);
        repo.setOverride(s.getId(), "{\"t\":\"h\u00e9llo \u4e2d\u6587\"}");
        assertEquals("{\"t\":\"h\u00e9llo \u4e2d\u6587\"}",
                repo.findById(s.getId()).orElseThrow().getSettingsOverride());
    }

    @Test
    void mxSetOverrideLongJsonRoundTrips() {
        GameSession s = repo.create("quiz1", "host1", "900007", null);
        String big = "{\"pads\":[" + "\"x\",".repeat(2000) + "\"y\"]}";
        repo.setOverride(s.getId(), big);
        assertEquals(big, repo.findById(s.getId()).orElseThrow().getSettingsOverride());
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
        repo.updateStatus(s.getId(), "ACTIVE");
        repo.updateStatus(s.getId(), "LOBBY");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("LOBBY", got.getStatus());
        assertNotNull(got.getStartedAt());
    }

    @Test
    void mxCreateOverrideJsonRoundTrips() {
        GameSession s = repo.create("quiz1", "host1", "900011", "{\"timer\":30}");
        assertEquals("{\"timer\":30}", repo.findById(s.getId()).orElseThrow().getSettingsOverride());
    }

    @Test
    void mxCreateReturnsDistinctIds() {
        GameSession a = repo.create("quiz1", "host1", "900012", null);
        GameSession b = repo.create("quiz1", "host1", "900013", null);
        assertTrue(!a.getId().equals(b.getId()));
        assertTrue(repo.findById(a.getId()).isPresent());
        assertTrue(repo.findById(b.getId()).isPresent());
    }

    @Test
    void mxFindByIdAfterStatusChange() {
        GameSession s = repo.create("quiz1", "host1", "900014", null);
        repo.updateStatus(s.getId(), "ENDED");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("ENDED", got.getStatus());
        assertNotNull(got.getEndedAt());
    }

    @Test
    void mxEndedWithoutActiveLeavesStartedNull() {
        GameSession s = repo.create("quiz1", "host1", "900015", null);
        repo.updateStatus(s.getId(), "ENDED");
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("ENDED", got.getStatus());
        assertNotNull(got.getEndedAt());
        assertNull(got.getStartedAt());
    }

    @Test
    void mxUpdateStatusEndedTwiceKeepsEnded() {
        GameSession s = repo.create("quiz1", "host1", "900016", null);
        repo.updateStatus(s.getId(), "ENDED");
        repo.updateStatus(s.getId(), "ENDED");
        assertEquals("ENDED", repo.findById(s.getId()).orElseThrow().getStatus());
    }

    @Test
    void mxSetOverrideTwiceKeepsLast() {
        GameSession s = repo.create("quiz1", "host1", "900017", null);
        repo.setOverride(s.getId(), "first");
        repo.setOverride(s.getId(), "second");
        assertEquals("second", repo.findById(s.getId()).orElseThrow().getSettingsOverride());
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
        GameSession got = repo.findById(s.getId()).orElseThrow();
        assertEquals("quiz-abc", got.getQuizId());
        assertEquals("host-xyz", got.getHostUserId());
        assertEquals("900019", got.getPinCode());
    }

    @Test
    void createDefaultsToStandardMode() {
        GameSession s = repo.create("quiz1", "host1", "900020", null);
        assertEquals("STANDARD", s.getGameMode());
        assertEquals("STANDARD", repo.findById(s.getId()).orElseThrow().getGameMode());
    }

    @Test
    void createWithExplicitModeRoundTrips() {
        repo.create("quiz1", "host1", "900021", null, "EXAM");
        assertEquals("EXAM", repo.findByPin("900021").orElseThrow().getGameMode());
    }

    @Test
    void createWithNullModeFallsBackToStandard() {
        GameSession s = repo.create("quiz1", "host1", "900022", null, null);
        assertEquals("STANDARD", s.getGameMode());
    }

    @Test
    void createWithBlankModeFallsBackToStandard() {
        GameSession s = repo.create("quiz1", "host1", "900026", null, "  ");
        assertEquals("STANDARD", s.getGameMode());
    }

    @Test
    void deleteByPinRemovesEndedRow() {
        GameSession s = repo.create("quiz1", "host1", "900023", null);
        repo.updateStatus(s.getId(), "ENDED");
        assertEquals(1, repo.deleteByPin("900023"));
        assertTrue(repo.findByPin("900023").isEmpty());
    }

    @Test
    void deleteByPinMissingIsZero() {
        assertEquals(0, repo.deleteByPin("900024"));
    }

    @Test
    void legacyBlankModeReadsAsStandard() {
        dsl.insertInto(Tables.GAME_SESSIONS)
                .columns(Tables.SESS_ID, Tables.SESS_QUIZ_ID, Tables.SESS_PIN,
                        Tables.SESS_HOST, Tables.SESS_STATUS, Tables.SESS_MODE, Tables.SESS_INDEX)
                .values("legacy", "quiz1", "900025", "host1", "LOBBY", "", 0)
                .execute();
        assertEquals("STANDARD", repo.findByPin("900025").orElseThrow().getGameMode());
    }

    @Test
    void legacyNullModeReadsAsStandard() {
        dsl.insertInto(Tables.GAME_SESSIONS)
                .columns(Tables.SESS_ID, Tables.SESS_QUIZ_ID, Tables.SESS_PIN,
                        Tables.SESS_HOST, Tables.SESS_STATUS, Tables.SESS_MODE, Tables.SESS_INDEX)
                .values("legacy-null", "quiz1", "900027", "host1", "LOBBY", null, 0)
                .execute();
        assertEquals("STANDARD", repo.findByPin("900027").orElseThrow().getGameMode());
    }
}
