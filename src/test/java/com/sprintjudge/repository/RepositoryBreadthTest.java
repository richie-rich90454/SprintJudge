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
}
