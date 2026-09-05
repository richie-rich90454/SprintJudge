package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdsBreadthTest {

    @Test
    void uuidMatchesCanonicalFormat() {
        assertTrue(Ids.uuid().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void uuidIsParseable() {
        UUID parsed = UUID.fromString(Ids.uuid());
        assertEquals(36, parsed.toString().length());
    }

    @Test
    void uuidsAreUniqueAcrossHundredSamples() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) seen.add(Ids.uuid());
        assertEquals(100, seen.size());
    }

    @Test
    void pinIsSixDigits() {
        for (int i = 0; i < 50; i++) assertTrue(Ids.pin().matches("\\d{6}"));
    }

    @Test
    void pinHasNoLeadingZero() {
        for (int i = 0; i < 200; i++) {
            int n = Integer.parseInt(Ids.pin());
            assertTrue(n >= 100000 && n <= 999999);
        }
    }

    @Test
    void pinsVaryAcrossSamples() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) seen.add(Ids.pin());
        assertTrue(seen.size() > 1);
    }

    @Test
    void uuidLengthIsThirtySix() {
        assertEquals(36, Ids.uuid().length());
    }

    @Test
    void pinLengthIsSix() {
        assertEquals(6, Ids.pin().length());
    }
}
