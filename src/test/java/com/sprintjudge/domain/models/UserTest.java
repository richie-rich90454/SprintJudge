package com.sprintjudge.domain.models;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private User sample() {
        return new User("id1", "a@b.c", "Al", "http://x/y.png", "ADMIN", Instant.ofEpochSecond(1000));
    }

    @Test
    void constructorAndAccessors() {
        User u = sample();
        assertEquals("id1", u.id());
        assertEquals("a@b.c", u.email());
        assertEquals("Al", u.name());
        assertEquals("http://x/y.png", u.avatarUrl());
        assertEquals("ADMIN", u.role());
        assertEquals(Instant.ofEpochSecond(1000), u.createdAt());
    }

    @Test
    void equalsSameFieldsIsTrue() {
        assertEquals(sample(), sample());
    }

    @Test
    void equalsDifferentFieldIsFalse() {
        assertNotEquals(new User("id2", "a@b.c", "Al", null, "ADMIN", null), sample());
    }

    @Test
    void equalsNullIsFalse() {
        assertFalse(sample().equals(null));
    }

    @Test
    void equalsOtherTypeIsFalse() {
        assertFalse(sample().equals(new Object()));
    }

    @Test
    void hashCodeConsistent() {
        assertEquals(sample().hashCode(), sample().hashCode());
    }

    @Test
    void toStringContainsType() {
        assertTrue(sample().toString().contains("User"));
    }
}
