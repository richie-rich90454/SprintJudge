package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntObjectMapWorkloadTest {

    @Test
    void sequentialThousandPutsAllReadable() {
        IntObjectMap<String> m = new IntObjectMap<>(16);
        for (int i = 0; i < 1000; i++) m.put(i, "v" + i);
        assertEquals(1000, m.size());
        for (int i = 0; i < 1000; i++) assertEquals("v" + i, m.get(i));
    }

    @Test
    void sparseStridedKeysAllReadable() {
        IntObjectMap<Integer> m = new IntObjectMap<>(16);
        for (int i = 0; i < 500; i++) m.put(i * 100003, i);
        assertEquals(500, m.size());
        for (int i = 0; i < 500; i++) assertEquals(i, m.get(i * 100003));
    }

    @Test
    void duplicatePutsKeepLastValueAndSize() {
        IntObjectMap<String> m = new IntObjectMap<>(8);
        for (int i = 0; i < 50; i++) m.put(7, "v" + i);
        assertEquals(1, m.size());
        assertEquals("v49", m.get(7));
    }

    @Test
    void duplicatePutsReturnPreviousValue() {
        IntObjectMap<String> m = new IntObjectMap<>(8);
        assertNull(m.put(9, "first"));
        assertEquals("first", m.put(9, "second"));
        assertEquals("second", m.get(9));
    }

    @Test
    void removeThenReinsertCyclesValue() {
        IntObjectMap<String> m = new IntObjectMap<>(8);
        for (int round = 0; round < 5; round++) {
            m.put(11, "r" + round);
            assertEquals("r" + round, m.get(11));
            assertEquals("r" + round, m.remove(11));
            assertNull(m.get(11));
        }
        assertEquals(0, m.size());
    }

    @Test
    void growPast2048CapacityKeepsEveryEntry() {
        IntObjectMap<Integer> m = new IntObjectMap<>(2048);
        for (int i = 0; i < 2600; i++) m.put(i, i * 2);
        assertEquals(2600, m.size());
        for (int i = 0; i < 2600; i++) assertEquals(i * 2, m.get(i));
    }

    @Test
    void bulkPinRangeKeysRoundTrip() {
        IntObjectMap<String> m = new IntObjectMap<>(512);
        for (int pin = 100000; pin < 101000; pin++) m.put(pin, "room-" + pin);
        assertEquals(1000, m.size());
        for (int pin = 100000; pin < 101000; pin++) assertEquals("room-" + pin, m.get(pin));
    }

    @Test
    void negativeKeyBulkWorkload() {
        IntObjectMap<Integer> m = new IntObjectMap<>(64);
        for (int i = 1; i <= 300; i++) m.put(-i, i);
        assertEquals(300, m.size());
        for (int i = 1; i <= 300; i++) assertEquals(i, m.get(-i));
    }

    @Test
    void interleavedPutRemoveMatchesHashMapOracle() {
        Random rnd = new Random(987654321L);
        IntObjectMap<String> m = new IntObjectMap<>(32);
        Map<Integer, String> oracle = new HashMap<>();
        for (int step = 0; step < 3000; step++) {
            int k = rnd.nextInt(400);
            if (rnd.nextInt(10) == 0) {
                assertEquals(oracle.remove(k), m.remove(k));
            } else {
                String v = "s" + step;
                oracle.put(k, v);
                m.put(k, v);
            }
        }
        assertEquals(oracle.size(), m.size());
        for (Map.Entry<Integer, String> e : oracle.entrySet()) {
            assertEquals(e.getValue(), m.get(e.getKey()));
        }
    }

    @Test
    void removeAllLeavesEmptyReadableMap() {
        IntObjectMap<String> m = new IntObjectMap<>(16);
        for (int i = 0; i < 200; i++) m.put(i, "v" + i);
        for (int i = 0; i < 200; i++) assertEquals("v" + i, m.remove(i));
        assertEquals(0, m.size());
        for (int i = 0; i < 200; i++) assertNull(m.get(i));
    }

    @Test
    void forEachVisitsEveryLiveEntryOnce() {
        IntObjectMap<Integer> m = new IntObjectMap<>(16);
        for (int i = 0; i < 100; i++) m.put(i, i);
        Map<Integer, Integer> seen = new HashMap<>();
        m.forEach((k, v) -> seen.put(k, v));
        assertEquals(100, seen.size());
        for (int i = 0; i < 100; i++) assertEquals(i, seen.get(i));
    }

    @Test
    void forEachSumsMatchAfterPartialRemovals() {
        IntObjectMap<Integer> m = new IntObjectMap<>(16);
        for (int i = 0; i < 100; i++) m.put(i, 1);
        for (int i = 0; i < 100; i += 2) m.remove(i);
        int[] sum = {0};
        m.forEach((k, v) -> sum[0] += v);
        assertEquals(50, sum[0]);
        assertEquals(50, m.size());
    }

    @Test
    void tinyCapacityGrowsCorrectly() {
        IntObjectMap<String> m = new IntObjectMap<>(1);
        for (int i = 0; i < 100; i++) m.put(i, "v" + i);
        assertEquals(100, m.size());
        for (int i = 0; i < 100; i++) assertEquals("v" + i, m.get(i));
    }

    @Test
    void getOnEmptyMapIsNull() {
        assertNull(new IntObjectMap<>(8).get(123));
    }

    @Test
    void removeOnEmptyMapIsNull() {
        assertNull(new IntObjectMap<>(8).remove(123));
    }

    @Test
    void extremeIntKeysCoexist() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.put(Integer.MIN_VALUE, "min");
        m.put(Integer.MIN_VALUE + 1, "min1");
        m.put(Integer.MAX_VALUE - 1, "max1");
        m.put(Integer.MAX_VALUE, "max");
        assertEquals("min", m.get(Integer.MIN_VALUE));
        assertEquals("min1", m.get(Integer.MIN_VALUE + 1));
        assertEquals("max1", m.get(Integer.MAX_VALUE - 1));
        assertEquals("max", m.get(Integer.MAX_VALUE));
    }

    @Test
    void overwriteThenRemoveYieldsLastValue() {
        IntObjectMap<String> m = new IntObjectMap<>(8);
        m.put(5, "a");
        m.put(5, "b");
        assertEquals("b", m.remove(5));
        assertEquals(0, m.size());
    }

    @Test
    void sameKeyAcrossGrowBoundaryKeepsLatest() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        for (int i = 0; i < 1000; i++) m.put(i, "v" + i);
        m.put(42, "updated");
        assertEquals("updated", m.get(42));
        assertEquals(1000, m.size());
    }

    @Test
    void descendingInsertsAllReadable() {
        IntObjectMap<Integer> m = new IntObjectMap<>(32);
        for (int i = 999; i >= 0; i--) m.put(i, i);
        assertEquals(1000, m.size());
        for (int i = 0; i < 1000; i++) assertEquals(i, m.get(i));
    }

    @Test
    void powerOfTwoSpacedKeysAllReadable() {
        IntObjectMap<String> m = new IntObjectMap<>(8);
        for (int i = 0; i < 256; i++) m.put(i * 1024, "p" + i);
        assertEquals(256, m.size());
        for (int i = 0; i < 256; i++) assertEquals("p" + i, m.get(i * 1024));
    }
}
