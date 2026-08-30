package com.sprintjudge.repository;

import com.sprintjudge.TestDb;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSettingsRepositoryTest {

    private DSLContext dsl;
    private AdminSettingsRepository repo;

    @BeforeEach
    void setup() throws Exception {
        dsl = TestDb.inMemory();
        repo = new AdminSettingsRepository(dsl);
    }

    @Test
    void putInsertsWhenAbsent() {
        repo.put("k1", "v1");
        assertEquals("v1", repo.findByKey("k1").orElseThrow().value());
    }

    @Test
    void putUpdatesWhenPresent() {
        repo.put("k1", "v1");
        repo.put("k1", "v2");
        assertEquals("v2", repo.findByKey("k1").orElseThrow().value());
    }

    @Test
    void findByKeyNotFound() {
        assertTrue(repo.findByKey("missing").isEmpty());
    }

    @Test
    void findAllAsMapEmpty() {
        assertTrue(repo.findAllAsMap().isEmpty());
    }

    @Test
    void findAllAsMapPopulated() {
        repo.put("a", "1");
        repo.put("b", "2");
        Map<String, String> m = repo.findAllAsMap();
        assertEquals(2, m.size());
        assertEquals("1", m.get("a"));
        assertEquals("2", m.get("b"));
    }
}
