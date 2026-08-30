package com.sprintjudge.domain.models;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSettingTest {

    private AdminSetting sample() {
        return new AdminSetting("theme", "dark", Instant.ofEpochSecond(2000));
    }

    @Test
    void constructorAndAccessors() {
        AdminSetting s = sample();
        assertEquals("theme", s.key());
        assertEquals("dark", s.value());
        assertEquals(Instant.ofEpochSecond(2000), s.updatedAt());
    }

    @Test
    void equalsSameFieldsIsTrue() {
        assertEquals(sample(), sample());
    }

    @Test
    void equalsDifferentFieldIsFalse() {
        assertNotEquals(new AdminSetting("lang", "dark", null), sample());
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
        assertTrue(sample().toString().contains("AdminSetting"));
    }
}
