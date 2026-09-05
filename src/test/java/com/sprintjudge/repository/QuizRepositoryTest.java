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

    private Quiz mxQuiz(String id, String title) {
        return new Quiz(id, title, "D", "u1", Instant.now(), false);
    }

    @Test
    void mxCreateDuplicateIdThrows() {
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
    void mxCreateEmptyTitleRoundTrips() {
        Quiz q = repo.create(mxQuiz(null, ""));
        assertEquals("", repo.findById(q.id()).orElseThrow().title());
    }

    @Test
    void mxCreateUnicodeTitleRoundTrips() {
        Quiz q = repo.create(new Quiz(null, "h\u00e9llo \u4e2d\u6587 \ud83c\udf89", "d", null,
                Instant.now(), false));
        assertEquals("h\u00e9llo \u4e2d\u6587 \ud83c\udf89",
                repo.findById(q.id()).orElseThrow().title());
    }

    @Test
    void mxCreateUnicodeDescriptionRoundTrips() {
        Quiz q = repo.create(new Quiz(null, "T", "d-\u00fc-\u4e2d\u6587", null, Instant.now(), false));
        assertEquals("d-\u00fc-\u4e2d\u6587", repo.findById(q.id()).orElseThrow().description());
    }

    @Test
    void mxCreateEmptyDescriptionRoundTrips() {
        Quiz q = repo.create(new Quiz(null, "T", "", null, Instant.now(), false));
        assertEquals("", repo.findById(q.id()).orElseThrow().description());
    }

    @Test
    void mxCreateNullCreatedByRoundTrips() {
        Quiz q = repo.create(new Quiz(null, "T", "D", null, Instant.now(), false));
        assertTrue(repo.findById(q.id()).isPresent());
    }

    @Test
    void mxBulk30CountMatches() {
        for (int i = 0; i < 30; i++) repo.create(mxQuiz("m" + i, "T" + i));
        assertEquals(30, repo.count());
        assertEquals(30, repo.findAll().size());
    }

    @Test
    void mxBulk30SpotReadById() {
        for (int i = 0; i < 30; i++) repo.create(mxQuiz("s" + i, "T" + i));
        assertEquals("T17", repo.findById("s17").orElseThrow().title());
        assertTrue(repo.findById("s99").isEmpty());
    }

    @Test
    void mxUpdateSetsNullDescription() {
        Quiz created = repo.create(mxQuiz(null, "T"));
        repo.update(new Quiz(created.id(), "T", null, "u1", created.createdAt(), false));
        assertTrue(repo.findById(created.id()).orElseThrow().description() == null);
    }

    @Test
    void mxUpdateKeepsCreatedByColumn() {
        Quiz created = repo.create(new Quiz(null, "T", "D", "author-1", Instant.now(), false));
        repo.update(new Quiz(created.id(), "New", "D2", "hacker", created.createdAt(), false));
        assertEquals("author-1", repo.findById(created.id()).orElseThrow().createdBy());
    }

    @Test
    void mxDeleteThenRecreateSameIdOk() {
        repo.create(new Quiz("cycle", "A", null, null, Instant.now(), false));
        repo.delete("cycle");
        repo.create(new Quiz("cycle", "B", null, null, Instant.now(), false));
        assertEquals("B", repo.findById("cycle").orElseThrow().title());
        assertEquals(1, repo.count());
    }

    @Test
    void mxFindAllContainsBothIds() {
        repo.create(new Quiz("fa", "A", null, null, Instant.now(), false));
        repo.create(new Quiz("fb", "B", null, null, Instant.now(), false));
        List<String> ids = repo.findAll().stream().map(Quiz::id).toList();
        assertTrue(ids.contains("fa"));
        assertTrue(ids.contains("fb"));
    }

    @Test
    void mxCountZeroAfterBulkDeleteAll() {
        for (int i = 0; i < 10; i++) repo.create(mxQuiz("z" + i, "T"));
        for (int i = 0; i < 10; i++) repo.delete("z" + i);
        assertEquals(0, repo.count());
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void mxCreateWithSpecialCharsId() {
        repo.create(new Quiz("id with spaces/\u00fc", "T", null, null, Instant.now(), false));
        assertTrue(repo.findById("id with spaces/\u00fc").isPresent());
    }

    @Test
    void mxUpdateTitleToEmptyString() {
        Quiz created = repo.create(mxQuiz(null, "Old"));
        repo.update(new Quiz(created.id(), "", "D", "u1", created.createdAt(), false));
        assertEquals("", repo.findById(created.id()).orElseThrow().title());
    }

    @Test
    void mxUpdateTitleToUnicode() {
        Quiz created = repo.create(mxQuiz(null, "Old"));
        repo.update(new Quiz(created.id(), "\u4e2d\u6587 title", "D", "u1", created.createdAt(), false));
        assertEquals("\u4e2d\u6587 title", repo.findById(created.id()).orElseThrow().title());
    }

    @Test
    void mxFindByIdMissingAfterDelete() {
        Quiz created = repo.create(mxQuiz(null, "T"));
        repo.delete(created.id());
        assertTrue(repo.findById(created.id()).isEmpty());
    }

    @Test
    void mxCreatePreservesTemplateTrue() {
        repo.create(new Quiz("tt", "T", null, null, Instant.now(), true));
        assertTrue(repo.findById("tt").orElseThrow().template());
    }

    @Test
    void mxCreatePreservesTemplateFalse() {
        repo.create(new Quiz("tf", "T", null, null, Instant.now(), false));
        assertFalse(repo.findById("tf").orElseThrow().template());
    }

    @Test
    void mxDeleteOneKeepsOther() {
        repo.create(new Quiz("k1", "A", null, null, Instant.now(), false));
        repo.create(new Quiz("k2", "B", null, null, Instant.now(), false));
        repo.delete("k1");
        assertTrue(repo.findById("k1").isEmpty());
        assertEquals("B", repo.findById("k2").orElseThrow().title());
        assertEquals(1, repo.count());
    }

    @Test
    void mxLongTitle200RoundTrips() {
        Quiz q = repo.create(mxQuiz(null, "t".repeat(200)));
        assertEquals(200, repo.findById(q.id()).orElseThrow().title().length());
    }

    @Test
    void mxCreatedAtAutoSetOnCreate() {
        Quiz q = repo.create(new Quiz(null, "T", null, null, null, false));
        assertNotNull(repo.findById(q.id()).orElseThrow().createdAt());
    }
}
