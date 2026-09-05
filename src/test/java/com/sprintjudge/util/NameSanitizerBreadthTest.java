package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameSanitizerBreadthTest {

    @Test
    void scriptTagsAreStrippedToBareWords() {
        assertEquals("scriptalert1script", NameSanitizer.sanitize("<script>alert(1)</script>"));
    }

    @Test
    void sqlInjectionKeepsOnlyWhitelistedChars() {
        String out = NameSanitizer.sanitize("a'; DROP TABLE--");
        assertTrue(out.matches("[A-Za-z0-9 _\\-]*"));
    }

    @Test
    void unicodeLettersArePreserved() {
        assertEquals("Caf\u00e9 M\u00fcnch\u00e9n", NameSanitizer.sanitize("Caf\u00e9 M\u00fcnch\u00e9n"));
    }

    @Test
    void emojiIsRemoved() {
        assertEquals("Bob", NameSanitizer.sanitize("Bob\uD83D\uDE00"));
    }

    @Test
    void hundredCharNameTruncatesToTwenty() {
        assertEquals(20, NameSanitizer.sanitize("x".repeat(100)).length());
    }

    @Test
    void truncationKeepsLeadingPrefix() {
        assertEquals("abcdefghijklmnopqrst", NameSanitizer.sanitize("abcdefghijklmnopqrstuvwxyz"));
    }

    @Test
    void exactlyTwentyCharsSurvives() {
        assertEquals("a".repeat(20), NameSanitizer.sanitize("a".repeat(20)));
    }

    @Test
    void twentyOneCharsTruncates() {
        assertEquals("a".repeat(20), NameSanitizer.sanitize("a".repeat(21)));
    }

    @Test
    void trailingPunctuationIsDropped() {
        assertEquals("alice", NameSanitizer.sanitize("alice!!!"));
    }

    @Test
    void internalDoubleSpacesAreKept() {
        assertEquals("a  b", NameSanitizer.sanitize("a  b"));
    }

    @Test
    void newlineAndTabAreRemoved() {
        assertEquals("ab", NameSanitizer.sanitize("a\nb"));
        assertEquals("ab", NameSanitizer.sanitize("a\tb"));
    }

    @Test
    void hyphenUnderscoreOnlyNameSurvives() {
        assertEquals("-_-", NameSanitizer.sanitize("-_-"));
    }
}
