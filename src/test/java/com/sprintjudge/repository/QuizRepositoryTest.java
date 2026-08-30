package com.sprintjudge.repository;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.models.Quiz;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizRepositoryTest {

    private DSLContext dsl;
    private QuizRepository repo;

    @BeforeEach
    void setup() throws Exception {
        dsl = TestDb.inMemory();
        repo = new QuizRepository(dsl);
    }

    @Test
    void createGeneratesId() {
        Quiz q = repo.create(new Quiz(null, "T", "D", "u1", Instant.now(), false));
        assertNotNull(q.id());
        assertEquals("T", q.title());
        assertFalse(q.template());
    }

    @Test
    void createWithProvidedId() {
        Quiz q = repo.create(new Quiz("qid-1", "T", "D", "u1", Instant.now(), true));
        assertEquals("qid-1", q.id());
        assertTrue(q.template());
    }

    @Test
    void findByIdFound() {
        repo.create(new Quiz("q1", "T", "D", "u1", Instant.now(), true));
        Optional<Quiz> o = repo.findById("q1");
        assertTrue(o.isPresent());
        assertTrue(o.get().template());
    }

    @Test
    void findByIdNotFound() {
        assertTrue(repo.findById("missing").isEmpty());
    }

    @Test
    void findAllEmpty() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAllPopulated() {
        repo.create(new Quiz(null, "A", null, null, Instant.now(), false));
        repo.create(new Quiz(null, "B", null, null, Instant.now(), true));
        List<Quiz> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void deleteRemovesRow() {
        repo.create(new Quiz("qd", "T", null, null, Instant.now(), false));
        repo.delete("qd");
        assertTrue(repo.findById("qd").isEmpty());
    }

    @Test
    void toQuizTemplateNullBranch() {
        dsl.insertInto(Tables.QUIZZES)
                .columns(Tables.QUIZZES_ID, Tables.QUIZZES_TITLE, Tables.QUIZZES_CREATED_AT, Tables.QUIZZES_TEMPLATE)
                .values("qn", "T", 123L, null)
                .execute();
        Quiz q = repo.findById("qn").orElseThrow();
        assertFalse(q.template());
    }
}
