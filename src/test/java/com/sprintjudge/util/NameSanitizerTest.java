package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameSanitizerTest {

    @Test
    void nullReturnsEmpty() {
        assertEquals("", NameSanitizer.sanitize(null));
    }

    @Test
    void blankReturnsEmpty() {
        assertEquals("", NameSanitizer.sanitize("   "));
    }

    @Test
    void emptyReturnsEmpty() {
        assertEquals("", NameSanitizer.sanitize(""));
    }

    @Test
    void onlyInvalidCharsReturnsEmpty() {
        assertEquals("", NameSanitizer.sanitize("!@#$%"));
    }

    @Test
    void keepsLettersDigitsSpacesHyphensUnderscores() {
        assertEquals("Alice Bob", NameSanitizer.sanitize("Alice Bob"));
        assertEquals("a-b_c", NameSanitizer.sanitize("a-b_c"));
        assertEquals("John2_Doe", NameSanitizer.sanitize("John2_Doe"));
    }

    @Test
    void stripsInvalidChars() {
        assertEquals("badname", NameSanitizer.sanitize("bad#name!"));
    }

    @Test
    void truncatesToMaxLength() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 25; i++) sb.append('a');
        String out = NameSanitizer.sanitize(sb.toString());
        assertEquals(NameSanitizer.MAX_LENGTH, out.length());
        assertEquals("a".repeat(NameSanitizer.MAX_LENGTH), out);
    }

    @Test
    void trimsLeadingTrailingSpaces() {
        assertEquals("alice", NameSanitizer.sanitize("  alice  "));
    }
}
