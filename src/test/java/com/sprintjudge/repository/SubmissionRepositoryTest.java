package com.sprintjudge.repository;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.models.Submission;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionRepositoryTest {

    private DSLContext dsl;
    private SubmissionRepository repo;

    @BeforeEach
    void setup() throws Exception {
        dsl = TestDb.inMemory();
        repo = new SubmissionRepository(dsl);
    }

    private Submission sub(String id, String sess, String q, String uuid, int score, boolean correct) {
        return new Submission(id, sess, q, "player", uuid, "data", score, correct, "log", 1, Instant.now());
    }

    @Test
    void saveInsertGeneratesId() {
        Submission s = repo.save(sub(null, "s1", "q1", "u1", 5, true));
        assertNotNull(s.id());
    }

    @Test
    void saveInsertWithProvidedNewId() {
        Submission s = repo.save(sub("sub-1", "s1", "q1", "u1", 5, true));
        assertEquals("sub-1", s.id());
    }

    @Test
    void saveUpdateExisting() {
        repo.save(sub("sub-1", "s1", "q1", "u1", 5, true));
        repo.save(sub("sub-1", "s1", "q1", "u1", 9, false));
        Submission got = repo.findBest("s1", "q1", "u1").orElseThrow();
        assertEquals(9, got.scoreEarned());
        assertFalse(got.correct());
    }

    @Test
    void saveAllEmptyReturns() {
        repo.saveAll(List.of());
    }

    @Test
    void saveAllNonEmpty() {
        repo.saveAll(List.of(
                sub(null, "s2", "q2", "u2", 1, true),
                sub("sub-x", "s2", "q2", "u3", 2, false)));
        assertEquals(2, repo.findBySession("s2").size());
    }

    @Test
    void findBestFound() {
        repo.save(sub("a", "s1", "q1", "u1", 5, true));
        repo.save(sub("b", "s1", "q1", "u1", 9, true));
        Optional<Submission> best = repo.findBest("s1", "q1", "u1");
        assertTrue(best.isPresent());
        assertEquals(9, best.get().scoreEarned());
    }

    @Test
    void findBestNotFound() {
        assertTrue(repo.findBest("sX", "qX", "uX").isEmpty());
    }

    @Test
    void findBySessionQuestionFound() {
        repo.save(sub("a", "s1", "q1", "u1", 5, true));
        assertEquals(1, repo.findBySessionQuestion("s1", "q1").size());
    }

    @Test
    void findBySessionQuestionEmpty() {
        assertTrue(repo.findBySessionQuestion("s1", "qx").isEmpty());
    }

    @Test
    void findBySessionFound() {
        repo.save(sub("a", "s1", "q1", "u1", 5, true));
        repo.save(sub("b", "s1", "q2", "u1", 5, true));
        assertEquals(2, repo.findBySession("s1").size());
    }

    @Test
    void findBySessionEmpty() {
        assertTrue(repo.findBySession("s1").isEmpty());
    }

    @Test
    void findHighestByPlayerFound() {
        repo.save(sub("a", "s1", "q1", "u1", 5, true));
        repo.save(sub("b", "s1", "q2", "u1", 8, true));
        Optional<Submission> best = repo.findHighestByPlayer("s1", "u1");
        assertTrue(best.isPresent());
        assertEquals(8, best.get().scoreEarned());
    }

    @Test
    void findHighestByPlayerNotFound() {
        assertTrue(repo.findHighestByPlayer("s1", "uX").isEmpty());
    }

    @Test
    void toSubmissionNullCorrectAndAt() {
        dsl.insertInto(Tables.SUBMISSIONS)
                .columns(Tables.SUB_ID, Tables.SUB_SESS, Tables.SUB_QUESTION, Tables.SUB_PNAME,
                        Tables.SUB_PUUID, Tables.SUB_DATA, Tables.SUB_SCORE, Tables.SUB_CORRECT,
                        Tables.SUB_ATTEMPTS, Tables.SUB_AT)
                .values("sc", "s1", "q1", "p", "u", "d", 3, null, 1, null)
                .execute();
        Submission s = repo.findBySessionQuestion("s1", "q1").get(0);
        assertFalse(s.correct());
        assertNull(s.submittedAt());
    }

    @Test
    void toSubmissionCorrectFalse() {
        repo.save(sub("cf", "s1", "q1", "u1", 4, false));
        Submission got = repo.findBest("s1", "q1", "u1").orElseThrow();
        assertFalse(got.correct());
    }
}
