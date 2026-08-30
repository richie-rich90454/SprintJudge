package com.sprintjudge.repository;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.models.Question;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionRepositoryTest {

    private DSLContext dsl;
    private QuestionRepository repo;

    @BeforeEach
    void setup() throws Exception {
        dsl = TestDb.inMemory();
        repo = new QuestionRepository(dsl);
    }

    private Question sample(String id, List<String> langs) {
        return new Question(id, "quiz1", "Title", "Desc", "MCQ", langs, 30, 10, "{}", 0, Instant.now());
    }

    @Test
    void saveInsertGeneratesId() {
        Question q = repo.save(sample(null, List.of("java")));
        assertNotNull(q.id());
    }

    @Test
    void saveInsertWithProvidedId() {
        Question q = repo.save(sample("q-1", List.of("java")));
        assertEquals("q-1", q.id());
    }

    @Test
    void saveUpdateExisting() {
        repo.save(sample("q-1", List.of("java")));
        Question updated = repo.save(new Question("q-1", "quiz1", "NewTitle", "Desc", "MCQ",
                List.of("python"), 40, 20, "{}", 1, Instant.now()));
        assertEquals("NewTitle", updated.title());
        assertEquals(1, repo.findById("q-1").orElseThrow().orderIndex());
    }

    @Test
    void saveLanguagesNull() {
        repo.save(sample("ql", null));
        Question got = repo.findById("ql").orElseThrow();
        assertNull(got.languagesAllowed());
    }

    @Test
    void saveLanguagesBlankStoredAsNull() {
        repo.save(new Question("qb", "quiz1", "T", null, "MCQ", List.of(), 10, 1, "{}", 0, Instant.now()));
        Question got = repo.findById("qb").orElseThrow();
        assertNull(got.languagesAllowed());
    }

    @Test
    void saveLanguagesList() {
        repo.save(sample("qx", List.of("java", "python")));
        Question got = repo.findById("qx").orElseThrow();
        assertEquals(List.of("java", "python"), got.languagesAllowed());
    }

    @Test
    void findByQuizOrdersByIndex() {
        repo.save(new Question("q1", "z", "A", null, "MCQ", null, 10, 1, "{}", 2, Instant.now()));
        repo.save(new Question("q2", "z", "B", null, "MCQ", null, 10, 1, "{}", 1, Instant.now()));
        List<Question> list = repo.findByQuiz("z");
        assertEquals(2, list.size());
        assertEquals("B", list.get(0).title());
    }

    @Test
    void findByQuizEmpty() {
        assertTrue(repo.findByQuiz("nope").isEmpty());
    }

    @Test
    void findByIdNotFound() {
        assertTrue(repo.findById("x").isEmpty());
    }

    @Test
    void deleteRemovesRow() {
        repo.save(sample("qd", null));
        repo.delete("qd");
        assertTrue(repo.findById("qd").isEmpty());
    }
}
