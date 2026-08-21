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
        // HTML / injection characters are removed by the whitelist.
        assertEquals("Alice", NameSanitizer.sanitize("<script>Alice</script>"));
        assertEquals("", NameSanitizer.sanitize("<img src=x onerror=alert(1)>"));
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
