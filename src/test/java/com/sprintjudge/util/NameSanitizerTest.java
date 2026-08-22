package com.sprintjudge.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @ParameterizedTest
    @ValueSource(strings = {
        "<img src=x onerror=alert(1)>",
        "javascript:alert(1)",
        "Robert'); DROP TABLE players;--",
        "' OR '1'='1",
        "\" onmouseover=\"alert(1)",
        "{{7*7}}",
        "${jndi:ldap://evil}",
        "\u0000null-byte"
    })
    void hostilePayloadsLeaveNoMetacharacters(String payload) {
        String safe = NameSanitizer.sanitize(payload);
        assertTrue(safe.matches("[A-Za-z0-9 _\\-]*"), "unsafe residue: " + safe);
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbb",
                             "ccccccccccccccccccccc", "dddddddddddddddddddddddddddddddd"})
    void truncatesAtTwentyCharacters(String raw) {
        String out = NameSanitizer.sanitize(raw);
        assertTrue(out.length() <= NameSanitizer.MAX_LENGTH);
        assertEquals(Math.min(20, raw.length()), out.length());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "!!!", "***", "<<<>>>"})
    void rejectsEmptyAfterSanitize(String raw) {
        assertEquals("", NameSanitizer.sanitize(raw));
    }

    @Test
    void unicodeLettersArePreserved() {
        // Character.isLetterOrDigit covers non-ASCII letters by design.
        assertEquals("José", NameSanitizer.sanitize("José"));
    }

    @Test
    void htmlEscapeAddsNothingForWhitelistedChars() {
        // Whitelist already excludes & < > " ' so HtmlUtils must be a no-op.
        assertEquals("Ann-Lee_2", NameSanitizer.sanitize("Ann-Lee_2"));
    }
}
