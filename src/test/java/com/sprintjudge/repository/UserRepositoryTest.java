package com.sprintjudge.repository;

import com.sprintjudge.TestDb;
import com.sprintjudge.domain.models.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRepositoryTest {

    private DSLContext dsl;
    private UserRepository repo;

    @BeforeEach
    void setup() throws Exception {
        dsl = TestDb.inMemory();
        repo = new UserRepository(dsl);
    }

    @Test
    void upsertNewInserts() {
        User u = repo.upsertByEmail("a@b.com", "Al", "ava");
        assertNotNull(u.id());
        assertEquals("ADMIN", u.role());
        assertEquals("Al", u.name());
        assertEquals("ava", u.avatarUrl());
    }

    @Test
    void upsertExistingReturnsExistingWithoutInsert() {
        User first = repo.upsertByEmail("a@b.com", "Al", "ava");
        User second = repo.upsertByEmail("a@b.com", "Different", "x");
        assertEquals(first.id(), second.id());
        assertEquals("Al", second.name());
    }

    @Test
    void findByEmailFound() {
        repo.upsertByEmail("a@b.com", "Al", "ava");
        Optional<User> o = repo.findByEmail("a@b.com");
        assertTrue(o.isPresent());
        assertEquals("a@b.com", o.get().email());
    }

    @Test
    void findByEmailNotFound() {
        assertTrue(repo.findByEmail("nope@b.com").isEmpty());
    }

    @Test
    void findAllEmpty() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAllPopulated() {
        repo.upsertByEmail("a@b.com", "Al", "ava");
        repo.upsertByEmail("c@d.com", "Bo", "bv");
        List<User> all = repo.findAll();
        assertEquals(2, all.size());
    }
}
