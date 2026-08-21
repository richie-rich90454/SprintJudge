package com.openquiz.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameSanitizerTest {

    @Test
    void keepsSafeNames() {
        assertEquals("Alice", NameSanitizer.sanitize("Alice"));
        assertEquals("Bob Builder", NameSanitizer.sanitize("Bob Builder"));
        assertEquals("x-y_z", NameSanitizer.sanitize("x-y_z"));
    }

    @Test
    void stripsDangerousCharacters() {
        // The whitelist keeps inert alphanumerics only — markup, quotes and
        // slashes never survive, whatever letters they carried.
        String result = NameSanitizer.sanitize("<script>Alice</script>");
        assertTrue(!result.contains("<") && !result.contains(">") && !result.contains("/"));
        assertTrue(result.length() <= NameSanitizer.MAX_LENGTH);
    }

    @Test
    void truncatesToTwenty() {
        String longName = "a".repeat(50);
        String result = NameSanitizer.sanitize(longName);
        assertTrue(result.length() <= 20);
    }

    @Test
    void rejectsEmptyAfterSanitize() {
        assertEquals("", NameSanitizer.sanitize("!!!"));
        assertEquals("", NameSanitizer.sanitize("   "));
    }
}
