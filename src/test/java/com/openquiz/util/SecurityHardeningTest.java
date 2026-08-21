package com.openquiz.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security tests: XSS payloads, SQL injection attempts and malformed JSON
 * must never survive into game state or broadcasts.
 */
class SecurityHardeningTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "<script>alert(1)</script>",
        "<img src=x onerror=alert(1)>",
        "Alice<script>alert(1)</script>",
        "javascript:alert(1)",
        "Robert'); DROP TABLE players;--",
        "' OR '1'='1",
        "\" onmouseover=\"alert(1)"
    })
    void hostileNamesAreNeutralized(String payload) {
        String safe = NameSanitizer.sanitize(payload);
        assertTrue(!safe.contains("<") && !safe.contains(">") && !safe.contains("'"),
            "Hostile characters must not survive sanitization: " + safe);
    }

    @Test
    void malformedJsonThrowsRatherThanPassingThrough() {
        try {
            Json.readTree("{not valid json");
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Malformed JSON should not parse silently");
    }

    @Test
    void emptyNameIsRejected() {
        assertEquals("", NameSanitizer.sanitize(""));
        assertEquals("", NameSanitizer.sanitize(null));
    }
}
