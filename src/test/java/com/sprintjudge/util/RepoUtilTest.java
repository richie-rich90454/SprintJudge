package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepoUtilTest {

    @Test
    void asLongNull() {
        assertEquals(0L, RepoUtil.asLong(null));
    }

    @Test
    void asLongFromLong() {
        assertEquals(42L, RepoUtil.asLong(Long.valueOf(42)));
    }

    @Test
    void asLongFromInt() {
        assertEquals(7L, RepoUtil.asLong(Integer.valueOf(7)));
    }

    @Test
    void asLongFromString() {
        assertEquals(123L, RepoUtil.asLong("123"));
    }

    @Test
    void asLongBoxedNull() {
        assertNull(RepoUtil.asLongBoxed(null));
    }

    @Test
    void asLongBoxedValue() {
        assertEquals(Long.valueOf(5L), RepoUtil.asLongBoxed(Long.valueOf(5)));
    }
}
