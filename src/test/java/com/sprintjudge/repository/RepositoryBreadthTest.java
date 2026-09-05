package com.sprintjudge.repository;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.domain.models.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryBreadthTest {

    private Submission sub(String id, String sess, String q, String uuid, int score, boolean correct) {
        return new Submission(id, sess, q, "player", uuid, "{\"a\":1}", score, correct, "log", 2, Instant.now());
    }

    private Question question(String id, String quiz, int order) {
        return new Question(id, quiz, "T-" + id, "D", "MCQ", null, 30, 100, "{}", order, Instant.now());
    }

    @Test
    void findBestPicksMaxOfThree() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(sub("a", "s1", "q1", "u1", 30, true));
        repo.save(sub("b", "s1", "q1", "u1", 90, true));
        repo.save(sub("c", "s1", "q1", "u1", 60, false));
        assertEquals(90, repo.findBest("s1", "q1", "u1").orElseThrow().scoreEarned());
    }

    @Test
    void findBestIsolatesPlayers() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(sub("a", "s1", "q1", "u1", 95, true));
        repo.save(sub("b", "s1", "q1", "u2", 10, false));
        assertEquals(95, repo.findBest("s1", "q1", "u1").orElseThrow().scoreEarned());
        assertEquals(10, repo.findBest("s1", "q1", "u2").orElseThrow().scoreEarned());
    }

    @Test
    void findHighestSpansQuestions() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(sub("a", "s1", "q1", "u1", 40, true));
        repo.save(sub("b", "s1", "q2", "u1", 85, true));
        assertEquals(85, repo.findHighestByPlayer("s1", "u1").orElseThrow().scoreEarned());
    }

    @Test
    void saveAllBatchOfFivePersistsAll() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        List<Submission> batch = new ArrayList<>();
        for (int i = 0; i < 5; i++) batch.add(sub("b" + i, "s9", "q9", "u" + i, i, true));
        repo.saveAll(batch);
        assertEquals(5, repo.findBySession("s9").size());
    }

    @Test
    void saveAllGeneratesIdsForNullEntries() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.saveAll(List.of(sub(null, "s1", "q1", "u1", 1, true), sub(null, "s1", "q1", "u2", 2, true)));
        assertEquals(2, repo.findBySession("s1").size());
    }

    @Test
    void saveRoundTripsEveryField() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(sub("full", "s1", "q1", "u7", 77, true));
        Submission got = repo.findBest("s1", "q1", "u7").orElseThrow();
        assertEquals("{\"a\":1}", got.responseData());
        assertEquals("log", got.judgeLog());
        assertEquals(2, got.attemptCount());
        assertEquals("player", got.playerName());
        assertEquals(77, got.scoreEarned());
    }

    @Test
    void findBySessionIsolatesSessions() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(sub("a", "s1", "q1", "u1", 5, true));
        repo.save(sub("b", "s2", "q1", "u1", 5, true));
        assertEquals(1, repo.findBySession("s1").size());
        assertEquals(1, repo.findBySession("s2").size());
    }

    @Test
    void overwriteVisibleInMap() throws Exception {
        AdminSettingsRepository repo = new AdminSettingsRepository(TestDb.inMemory());
        repo.put("k", "v1");
        repo.put("k", "v2");
        assertEquals("v2", repo.findAllAsMap().get("k"));
        assertEquals(1, repo.findAllAsMap().size());
    }

    @Test
    void threeKeysRoundTrip() throws Exception {
        AdminSettingsRepository repo = new AdminSettingsRepository(TestDb.inMemory());
        repo.put("a", "1");
        repo.put("b", "2");
        repo.put("c", "3");
        assertEquals(3, repo.findAllAsMap().size());
        assertEquals("2", repo.findByKey("b").orElseThrow().value());
    }

    @Test
    void emptyStringValueRoundTrips() throws Exception {
        AdminSettingsRepository repo = new AdminSettingsRepository(TestDb.inMemory());
        repo.put("e", "");
        assertEquals("", repo.findByKey("e").orElseThrow().value());
    }

    @Test
    void unicodeValueRoundTrips() throws Exception {
        AdminSettingsRepository repo = new AdminSettingsRepository(TestDb.inMemory());
        repo.put("u", "h\u00e9llo w\u00f6rld");
        assertEquals("h\u00e9llo w\u00f6rld", repo.findByKey("u").orElseThrow().value());
    }

    @Test
    void threeRowsOrderByIndex() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        repo.save(question("q3", "z", 30));
        repo.save(question("q1", "z", 10));
        repo.save(question("q2", "z", 20));
        List<Question> list = repo.findByQuiz("z");
        assertEquals("q1", list.get(0).id());
        assertEquals("q2", list.get(1).id());
        assertEquals("q3", list.get(2).id());
    }

    @Test
    void deleteByQuizRemovesOnlyItsQuiz() throws Exception {
        DSLContext dsl = TestDb.inMemory();
        QuestionRepository repo = new QuestionRepository(dsl);
        repo.save(question("a1", "qa", 0));
        repo.save(question("b1", "qb", 0));
        repo.deleteByQuiz("qa");
        assertTrue(repo.findByQuiz("qa").isEmpty());
        assertEquals(1, repo.findByQuiz("qb").size());
    }

    @Test
    void deleteMissingQuestionIsNoop() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        repo.save(question("a1", "qa", 0));
        repo.delete("ghost");
        assertEquals(1, repo.findByQuiz("qa").size());
    }

    @Test
    void findByQuizIsolatesQuizzes() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        repo.save(question("a1", "qa", 0));
        repo.save(question("a2", "qa", 1));
        repo.save(question("b1", "qb", 0));
        assertEquals(2, repo.findByQuiz("qa").size());
        assertEquals(1, repo.findByQuiz("qb").size());
    }

    @Test
    void createNullDescriptionRoundTrips() throws Exception {
        QuizRepository repo = new QuizRepository(TestDb.inMemory());
        Quiz created = repo.create(new Quiz(null, "T", null, null, Instant.now(), false));
        assertEquals("T", repo.findById(created.id()).orElseThrow().title());
    }

    @Test
    void twoQuizzesBothListed() throws Exception {
        QuizRepository repo = new QuizRepository(TestDb.inMemory());
        repo.create(new Quiz("x1", "A", null, null, Instant.now(), false));
        repo.create(new Quiz("x2", "B", null, null, Instant.now(), true));
        assertEquals(2, repo.findAll().size());
        assertEquals(2, repo.count());
    }

    @Test
    void twoSessionsOnOneQuizStayIndependent() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        GameSession a = repo.create("quiz1", "host", "100001", null);
        GameSession b = repo.create("quiz1", "host", "100002", null);
        assertTrue(!a.id().equals(b.id()));
        repo.updateStatus(a.id(), "ACTIVE");
        assertEquals("ACTIVE", repo.findByPin("100001").orElseThrow().status());
        assertEquals("LOBBY", repo.findByPin("100002").orElseThrow().status());
    }

    @Test
    void overrideEmptyStringRoundTrips() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        GameSession s = repo.create("quiz1", "host", "100003", null);
        repo.setOverride(s.id(), "");
        assertEquals("", repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void createPreservesPinAndQuiz() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        GameSession s = repo.create("quiz-77", "host-77", "100004", "{\"x\":1}");
        assertEquals("100004", s.pinCode());
        assertEquals("quiz-77", s.quizId());
        assertEquals("{\"x\":1}", repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void upsertKeepsOriginalAvatarOnConflict() throws Exception {
        UserRepository repo = new UserRepository(TestDb.inMemory());
        User first = repo.upsertByEmail("u@x.com", "Al", "ava-1");
        User second = repo.upsertByEmail("u@x.com", "Al", "ava-2");
        assertEquals(first.id(), second.id());
        assertEquals("ava-1", second.avatarUrl());
    }

    @Test
    void threeUsersAllListed() throws Exception {
        UserRepository repo = new UserRepository(TestDb.inMemory());
        repo.upsertByEmail("a@x.com", "A", null);
        repo.upsertByEmail("b@x.com", "B", null);
        repo.upsertByEmail("c@x.com", "C", null);
        assertEquals(3, repo.findAll().size());
        assertFalse(repo.findByEmail("z@x.com").isPresent());
        assertNotNull(repo.findByEmail("b@x.com").orElseThrow().id());
    }

    @Test
    void mxSubBestMissingIsEmpty() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        assertTrue(repo.findBest("no-sess", "no-q", "no-u").isEmpty());
    }

    @Test
    void mxSubHighestMissingIsEmpty() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        assertTrue(repo.findHighestByPlayer("no-sess", "no-u").isEmpty());
    }

    @Test
    void mxSubUpsertSameIdOverwritesScore() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(sub("dup", "s1", "q1", "u1", 10, false));
        repo.save(sub("dup", "s1", "q1", "u1", 99, true));
        assertEquals(99, repo.findBest("s1", "q1", "u1").orElseThrow().scoreEarned());
        assertEquals(1, repo.findBySession("s1").size());
    }

    @Test
    void mxSubBySessionQuestionIsolatesQuestions() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(sub("a", "s1", "q1", "u1", 5, true));
        repo.save(sub("b", "s1", "q2", "u1", 6, true));
        repo.save(sub("c", "s1", "q1", "u2", 7, true));
        assertEquals(2, repo.findBySessionQuestion("s1", "q1").size());
        assertEquals(1, repo.findBySessionQuestion("s1", "q2").size());
        assertTrue(repo.findBySessionQuestion("s1", "qx").isEmpty());
    }

    @Test
    void mxSubEmptyStringFieldsRoundTrip() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(new Submission("e1", "s1", "q1", "", "u1", "", 0, false, "", 1, Instant.now()));
        Submission got = repo.findBest("s1", "q1", "u1").orElseThrow();
        assertEquals("", got.playerName());
        assertEquals("", got.responseData());
        assertEquals("", got.judgeLog());
    }

    @Test
    void mxSubUnicodeFieldsRoundTrip() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.save(new Submission("u1", "s1", "q1", "h\u00e9llo \u4e2d\u6587", "uu",
                "{\"code\":\"print('\u00fc')\"}", 8, true, "log-\u00fc", 1, Instant.now()));
        Submission got = repo.findBest("s1", "q1", "uu").orElseThrow();
        assertEquals("h\u00e9llo \u4e2d\u6587", got.playerName());
        assertEquals("log-\u00fc", got.judgeLog());
    }

    @Test
    void mxSubBulk100AllInSession() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        List<Submission> batch = new ArrayList<>();
        for (int i = 0; i < 100; i++) batch.add(sub("bulk" + i, "sb", "qb", "u" + i, i, true));
        repo.saveAll(batch);
        assertEquals(100, repo.findBySession("sb").size());
    }

    @Test
    void mxSubBulk100HighestIs99() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        List<Submission> batch = new ArrayList<>();
        for (int i = 0; i < 100; i++) batch.add(sub("h" + i, "sh", "qh", "solo", i, true));
        repo.saveAll(batch);
        assertEquals(99, repo.findHighestByPlayer("sh", "solo").orElseThrow().scoreEarned());
    }

    @Test
    void mxSubSaveAllEmptyIsNoop() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        repo.saveAll(List.of());
        assertTrue(repo.findBySession("sb").isEmpty());
    }

    @Test
    void mxSubNullIdGeneratesId() throws Exception {
        SubmissionRepository repo = new SubmissionRepository(TestDb.inMemory());
        Submission saved = repo.save(sub(null, "s1", "q1", "u1", 3, true));
        assertNotNull(saved.id());
        assertFalse(saved.id().isBlank());
    }

    @Test
    void mxQuestionNullIdGeneratesId() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        Question saved = repo.save(question(null, "qk", 0));
        assertNotNull(saved.id());
        assertEquals(1, repo.findByQuiz("qk").size());
    }

    @Test
    void mxQuestionFindByIdMissingIsEmpty() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void mxQuestionSaveSameIdOverwrites() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        repo.save(question("w1", "qk", 0));
        Question changed = new Question("w1", "qk", "CHANGED", "D", "MCQ", null, 30, 100, "{}", 0,
                Instant.now());
        repo.save(changed);
        assertEquals("CHANGED", repo.findById("w1").orElseThrow().title());
        assertEquals(1, repo.findByQuiz("qk").size());
    }

    @Test
    void mxQuestionDeleteByQuizMissingIsNoop() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        repo.save(question("a1", "qa", 0));
        repo.deleteByQuiz("nope");
        assertEquals(1, repo.findByQuiz("qa").size());
    }

    @Test
    void mxQuestionEmptyTitleRoundTrips() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        repo.save(new Question("e1", "qe", "", "D", "MCQ", null, 30, 100, "{}", 0, Instant.now()));
        assertEquals("", repo.findById("e1").orElseThrow().title());
    }

    @Test
    void mxQuestionUnicodeTitleRoundTrips() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        repo.save(new Question("u1", "qu", "h\u00e9llo \u4e2d\u6587 \ud83c\udf89", "D", "MCQ", null,
                30, 100, "{}", 0, Instant.now()));
        assertEquals("h\u00e9llo \u4e2d\u6587 \ud83c\udf89", repo.findById("u1").orElseThrow().title());
    }

    @Test
    void mxQuestionBulk100OrderedByIndex() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        for (int i = 99; i >= 0; i--) repo.save(question("o" + i, "qo", i));
        List<Question> list = repo.findByQuiz("qo");
        assertEquals(100, list.size());
        for (int i = 0; i < 100; i++) assertEquals(i, list.get(i).orderIndex());
    }

    @Test
    void mxQuestionNullLanguagesRoundTrips() throws Exception {
        QuestionRepository repo = new QuestionRepository(TestDb.inMemory());
        repo.save(new Question("l1", "ql", "T", "D", "MCQ", null, 30, 100, "{}", 0, Instant.now()));
        assertTrue(repo.findById("l1").isPresent());
    }

    @Test
    void mxQuizDuplicateIdThrows() throws Exception {
        QuizRepository repo = new QuizRepository(TestDb.inMemory());
        repo.create(new Quiz("dup", "A", null, null, Instant.now(), false));
        boolean thrown = false;
        try {
            repo.create(new Quiz("dup", "B", null, null, Instant.now(), false));
        } catch (RuntimeException e) {
            thrown = true;
        }
        assertTrue(thrown);
        assertEquals("A", repo.findById("dup").orElseThrow().title());
    }

    @Test
    void mxQuizEmptyTitleRoundTrips() throws Exception {
        QuizRepository repo = new QuizRepository(TestDb.inMemory());
        Quiz created = repo.create(new Quiz(null, "", null, null, Instant.now(), false));
        assertEquals("", repo.findById(created.id()).orElseThrow().title());
    }

    @Test
    void mxQuizUnicodeTitleRoundTrips() throws Exception {
        QuizRepository repo = new QuizRepository(TestDb.inMemory());
        Quiz created = repo.create(new Quiz(null, "h\u00e9llo \u4e2d\u6587", "d-\u00fc", null,
                Instant.now(), false));
        Quiz got = repo.findById(created.id()).orElseThrow();
        assertEquals("h\u00e9llo \u4e2d\u6587", got.title());
        assertEquals("d-\u00fc", got.description());
    }

    @Test
    void mxQuizBulk100CountAndSpotReads() throws Exception {
        QuizRepository repo = new QuizRepository(TestDb.inMemory());
        for (int i = 0; i < 100; i++) repo.create(new Quiz("bulk" + i, "T" + i, null, null,
                Instant.now(), i % 2 == 0));
        assertEquals(100, repo.count());
        assertEquals(100, repo.findAll().size());
        assertEquals("T42", repo.findById("bulk42").orElseThrow().title());
        assertTrue(repo.findById("bulk42").orElseThrow().template());
        assertFalse(repo.findById("bulk43").orElseThrow().template());
    }

    @Test
    void mxQuizNullCreatedByRoundTrips() throws Exception {
        QuizRepository repo = new QuizRepository(TestDb.inMemory());
        Quiz created = repo.create(new Quiz(null, "T", "D", null, Instant.now(), false));
        assertTrue(repo.findById(created.id()).isPresent());
    }

    @Test
    void mxSessionFindByIdMissingIsEmpty() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void mxSessionSetNegativeIndex() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        GameSession s = repo.create("q1", "h", "200001", null);
        repo.setCurrentIndex(s.id(), -1);
        assertEquals(-1, repo.findById(s.id()).orElseThrow().currentQuestionIndex());
    }

    @Test
    void mxSessionUnicodeOverrideRoundTrips() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        GameSession s = repo.create("q1", "h", "200002", null);
        repo.setOverride(s.id(), "{\"name\":\"h\u00e9llo \u4e2d\u6587\"}");
        assertEquals("{\"name\":\"h\u00e9llo \u4e2d\u6587\"}",
                repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void mxSessionBulk100AllPinsFindable() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        for (int i = 0; i < 100; i++) repo.create("q1", "h", String.format("%06d", 300000 + i), null);
        assertTrue(repo.findByPin("300000").isPresent());
        assertTrue(repo.findByPin("300099").isPresent());
        assertTrue(repo.findByPin("399999").isEmpty());
    }

    @Test
    void mxSessionResetToLobbyKeepsStarted() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        GameSession s = repo.create("q1", "h", "200003", null);
        repo.updateStatus(s.id(), "ACTIVE");
        repo.updateStatus(s.id(), "LOBBY");
        GameSession got = repo.findById(s.id()).orElseThrow();
        assertEquals("LOBBY", got.status());
        assertNotNull(got.startedAt());
    }

    @Test
    void mxSessionLongOverrideRoundTrips() throws Exception {
        GameSessionRepository repo = new GameSessionRepository(TestDb.inMemory());
        GameSession s = repo.create("q1", "h", "200004", null);
        String big = "{\"x\":\"" + "y".repeat(5000) + "\"}";
        repo.setOverride(s.id(), big);
        assertEquals(big, repo.findById(s.id()).orElseThrow().settingsOverride());
    }

    @Test
    void mxUserFindMissingIsEmpty() throws Exception {
        UserRepository repo = new UserRepository(TestDb.inMemory());
        assertTrue(repo.findByEmail("nobody@x.com").isEmpty());
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void mxUserUpsertConflictKeepsNameAndRole() throws Exception {
        UserRepository repo = new UserRepository(TestDb.inMemory());
        User first = repo.upsertByEmail("k@x.com", "First", "av1");
        User second = repo.upsertByEmail("k@x.com", "Second", "av2");
        assertEquals(first.id(), second.id());
        assertEquals("First", second.name());
        assertEquals("ADMIN", second.role());
    }

    @Test
    void mxUserUnicodeNameRoundTrips() throws Exception {
        UserRepository repo = new UserRepository(TestDb.inMemory());
        repo.upsertByEmail("uni@x.com", "h\u00e9llo \u4e2d\u6587", null);
        assertEquals("h\u00e9llo \u4e2d\u6587", repo.findByEmail("uni@x.com").orElseThrow().name());
    }

    @Test
    void mxUserBulk100AllListed() throws Exception {
        UserRepository repo = new UserRepository(TestDb.inMemory());
        for (int i = 0; i < 100; i++) repo.upsertByEmail("bulk" + i + "@x.com", "N" + i, null);
        assertEquals(100, repo.findAll().size());
        assertTrue(repo.findByEmail("bulk99@x.com").isPresent());
    }

    @Test
    void mxSettingsMissingKeyIsEmpty() throws Exception {
        AdminSettingsRepository repo = new AdminSettingsRepository(TestDb.inMemory());
        assertTrue(repo.findByKey("ghost").isEmpty());
        assertTrue(repo.findAllAsMap().isEmpty());
    }

    @Test
    void mxSettingsBulk100RoundTrip() throws Exception {
        AdminSettingsRepository repo = new AdminSettingsRepository(TestDb.inMemory());
        for (int i = 0; i < 100; i++) repo.put("k" + i, "v" + i);
        assertEquals(100, repo.findAllAsMap().size());
        assertEquals("v42", repo.findByKey("k42").orElseThrow().value());
    }

    @Test
    void mxSettingsEmptyKeyRoundTrips() throws Exception {
        AdminSettingsRepository repo = new AdminSettingsRepository(TestDb.inMemory());
        repo.put("", "empty-key");
        assertEquals("empty-key", repo.findByKey("").orElseThrow().value());
    }

    @Test
    void mxSettingsRepeatedOverwriteKeepsLast() throws Exception {
        AdminSettingsRepository repo = new AdminSettingsRepository(TestDb.inMemory());
        for (int i = 0; i < 10; i++) repo.put("flip", "v" + i);
        assertEquals("v9", repo.findByKey("flip").orElseThrow().value());
        assertEquals(1, repo.findAllAsMap().size());
    }
}
