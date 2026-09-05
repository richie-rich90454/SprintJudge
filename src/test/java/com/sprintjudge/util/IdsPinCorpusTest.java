package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdsPinCorpusTest {

    @Test
    void thousandPinsAllSixDigitInRange() {
        for (int i = 0; i < 1000; i++) {
            String pin = Ids.pin();
            assertEquals(6, pin.length(), pin);
            for (char c : pin.toCharArray()) assertTrue(c >= '0' && c <= '9', pin);
            int n = Integer.parseInt(pin);
            assertTrue(n >= 100000 && n <= 999999, pin);
        }
    }

    @Test
    void thousandPinsAreHighlyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) seen.add(Ids.pin());
        assertTrue(seen.size() > 950, "only " + seen.size() + " distinct");
    }

    @Test
    void thousandUuidsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) seen.add(Ids.uuid());
        assertEquals(1000, seen.size());
    }

    @Test
    void uuidIsVersionFour() {
        for (int i = 0; i < 20; i++) {
            assertEquals(4, UUID.fromString(Ids.uuid()).version());
        }
    }

    @Test
    void uuidVariantIsIetf() {
        for (int i = 0; i < 20; i++) {
            assertEquals(2, UUID.fromString(Ids.uuid()).variant());
        }
    }

    @Test
    void pinsHaveNoWhitespaceOrSign() {
        for (int i = 0; i < 100; i++) {
            String pin = Ids.pin();
            assertEquals(pin.strip(), pin);
            assertTrue(pin.chars().allMatch(Character::isDigit));
        }
    }

    @Test
    void consecutivePinsAreNotSequentialCounters() {
        Set<String> seen = new HashSet<>();
        String prev = Ids.pin();
        int equalNeighbors = 0;
        for (int i = 0; i < 200; i++) {
            String next = Ids.pin();
            if (next.equals(prev)) equalNeighbors++;
            seen.add(next);
            prev = next;
        }
        assertTrue(equalNeighbors < 5);
        assertTrue(seen.size() > 150);
    }

    @Test
    void uuidLowercaseHexOnly() {
        for (int i = 0; i < 20; i++) {
            assertTrue(Ids.uuid().matches("[0-9a-f\\-]{36}"));
        }
    }
}
