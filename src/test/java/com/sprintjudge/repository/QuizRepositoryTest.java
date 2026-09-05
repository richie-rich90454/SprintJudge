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

    @Test
    void countEmpty() {
        assertEquals(0, repo.count());
    }

    @Test
    void countAfterCreates() {
        repo.create(new Quiz(null, "A", null, null, Instant.now(), false));
        repo.create(new Quiz(null, "B", null, null, Instant.now(), false));
        assertEquals(2, repo.count());
    }

    @Test
    void updateTitleAndDescription() {
        Quiz created = repo.create(new Quiz(null, "Old", "olddesc", "u1", Instant.now(), false));
        Quiz updated = new Quiz(created.id(), "New", "newdesc", "u1", created.createdAt(), false);
        repo.update(updated);
        Quiz got = repo.findById(created.id()).orElseThrow();
        assertEquals("New", got.title());
        assertEquals("newdesc", got.description());
    }

    @Test
    void updateMissingRowIsNoop() {
        repo.update(new Quiz("ghost", "T", null, null, Instant.now(), false));
        assertTrue(repo.findById("ghost").isEmpty());
    }

    @Test
    void createPreservesCreatedByAndDescription() {
        Quiz q = repo.create(new Quiz(null, "T", "D", "author-9", Instant.now(), false));
        Quiz got = repo.findById(q.id()).orElseThrow();
        assertEquals("author-9", got.createdBy());
        assertEquals("D", got.description());
        assertNotNull(got.createdAt());
    }

    @Test
    void deleteMissingRowIsNoop() {
        repo.delete("nope");
        assertEquals(0, repo.count());
    }

    @Test
    void countReflectsDelete() {
        Quiz q = repo.create(new Quiz(null, "T", null, null, Instant.now(), false));
        assertEquals(1, repo.count());
        repo.delete(q.id());
        assertEquals(0, repo.count());
    }

    @Test
    void templateRoundTrip() {
        repo.create(new Quiz("tpl", "T", null, null, Instant.now(), true));
        assertTrue(repo.findById("tpl").orElseThrow().template());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void countNullFallsBackToZero() {
        DSLContext dsl = org.mockito.Mockito.mock(DSLContext.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(dsl.selectCount().from((org.jooq.Table) org.mockito.ArgumentMatchers.any())
                .fetchOne(0, Integer.class)).thenReturn(null);
        assertEquals(0, new QuizRepository(dsl).count());
    }
}
