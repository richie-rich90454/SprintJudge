package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntObjectMapExtraTest {

    @Test
    void zeroKeyRoundTrip() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.put(0, "zero");
        assertEquals("zero", m.get(0));
        assertEquals("zero", m.remove(0));
        assertNull(m.get(0));
        assertEquals(0, m.size());
    }

    @Test
    void negativeAndExtremeKeys() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.put(-1, "neg");
        m.put(Integer.MIN_VALUE, "min");
        m.put(Integer.MAX_VALUE, "max");
        assertEquals("neg", m.get(-1));
        assertEquals("min", m.get(Integer.MIN_VALUE));
        assertEquals("max", m.get(Integer.MAX_VALUE));
        assertEquals(3, m.size());
    }

    @Test
    void removeThenReput() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.put(5, "a");
        m.remove(5);
        assertNull(m.get(5));
        m.put(5, "b");
        assertEquals("b", m.get(5));
        assertEquals(1, m.size());
    }

    @Test
    void overwriteDoesNotGrowSize() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        for (int i = 0; i < 10; i++) m.put(42, "v" + i);
        assertEquals(1, m.size());
        assertEquals("v9", m.get(42));
    }

    @Test
    void forEachEmptyVisitsNone() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.forEach((k, v) -> { throw new AssertionError("must not visit"); });
    }

    @Test
    void forEachAfterRemovals() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.put(1, "a");
        m.put(2, "b");
        m.put(3, "c");
        m.remove(2);
        Map<Integer, String> seen = new HashMap<>();
        m.forEach(seen::put);
        assertEquals(Map.of(1, "a", 3, "c"), seen);
    }

    @Test
    void largeGrowKeepsAllEntries() {
        IntObjectMap<Integer> m = new IntObjectMap<>(2);
        for (int i = 0; i < 5000; i++) m.put(i * 31, i);
        assertEquals(5000, m.size());
        for (int i = 0; i < 5000; i++) assertEquals(i, m.get(i * 31));
    }

    @Test
    void removeMissingOnNonEmptyMap() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.put(1, "a");
        assertNull(m.remove(2));
        assertEquals(1, m.size());
        assertEquals("a", m.get(1));
    }

    @Test
    void nullValuesAllowed() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.put(7, null);
        assertEquals(1, m.size());
        assertNull(m.get(7));
    }

    @Test
    void pinLikeKeys() {
        IntObjectMap<String> m = new IntObjectMap<>(16);
        m.put(123456, "room-a");
        m.put(654321, "room-b");
        assertEquals("room-a", m.get(123456));
        assertEquals("room-b", m.get(654321));
        assertEquals("room-a", m.remove(123456));
        assertNull(m.get(123456));
        assertEquals("room-b", m.get(654321));
    }

    private static int mixKey(int k) {
        k *= 0x9E3779B9;
        k ^= k >>> 16;
        return k;
    }

    private static int keyWithHome(int home, int mask, java.util.Set<Integer> exclude) {
        for (int k = 1; k < 10_000_000; k++) {
            if (exclude.contains(k)) continue;
            if ((mixKey(k) & mask) == home) return k;
        }
        throw new IllegalStateException("no key for home " + home);
    }

    @Test
    void backwardShiftMovesChainedEntry() {
        // cap 8, mask 7: two colliding keys share a home; removing the first
        // shifts the second back into the hole (move-taken path).
        IntObjectMap<String> m = new IntObjectMap<>(2);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int a = keyWithHome(2, 7, used);
        used.add(a);
        int b = keyWithHome(2, 7, used);
        m.put(a, "a");
        m.put(b, "b");
        assertEquals("a", m.remove(a));
        assertEquals("b", m.get(b));
        assertEquals(1, m.size());
        assertEquals("b", m.remove(b));
        assertEquals(0, m.size());
    }

    @Test
    void backwardShiftWrapsAroundEnd() {
        // hole at 7, chained entry wrapped at 0 with the same home: the hole>j
        // wrap path moves it back (wrap move-taken path).
        IntObjectMap<String> m = new IntObjectMap<>(2);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int a = keyWithHome(7, 7, used);
        used.add(a);
        int b = keyWithHome(7, 7, used);
        m.put(a, "a");
        m.put(b, "b");
        assertEquals("a", m.remove(a));
        assertEquals("b", m.get(b));
        assertEquals("b", m.remove(b));
        assertEquals(0, m.size());
    }

    @Test
    void backwardShiftSkipsSettledEntries() {
        // entry whose home lies strictly between hole and cursor stays put
        // (move-skipped paths on both ternary arms).
        IntObjectMap<String> m = new IntObjectMap<>(2);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int a = keyWithHome(2, 7, used);
        used.add(a);
        int x = keyWithHome(3, 7, used);
        used.add(x);
        int c = keyWithHome(3, 7, used);
        m.put(a, "a");
        m.put(x, "x");
        m.put(c, "c");
        assertEquals("a", m.remove(a));
        assertEquals("x", m.get(x));
        assertEquals("c", m.get(c));
        assertEquals(2, m.size());
    }

    @Test
    void backwardShiftWrapSkipsFarHome() {
        // hole=5, chain 5->6->7->0: the wrapped entry at 0 with home 0 is
        // past the hole on the wrap arm, so it stays (AND second-operand false).
        IntObjectMap<String> m = new IntObjectMap<>(2);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int a = keyWithHome(5, 7, used);
        used.add(a);
        int b = keyWithHome(6, 7, used);
        used.add(b);
        int c = keyWithHome(7, 7, used);
        used.add(c);
        int d = keyWithHome(0, 7, used);
        m.put(a, "a");
        m.put(b, "b");
        m.put(c, "c");
        m.put(d, "d");
        assertEquals("a", m.remove(a));
        assertEquals("b", m.get(b));
        assertEquals("c", m.get(c));
        assertEquals("d", m.get(d));
        assertEquals(3, m.size());
    }

    @Test
    void randomizedLayoutsStayConsistent() {
        java.util.Random rnd = new java.util.Random(1234);
        for (int seed = 0; seed < 300; seed++) {
            IntObjectMap<String> m = new IntObjectMap<>(seed % 2 == 0 ? 2 : 8);
            java.util.Map<Integer, String> oracle = new java.util.HashMap<>();
            for (int i = 0; i < 40; i++) {
                int k = rnd.nextInt(500);
                String v = "v" + seed + "-" + i;
                oracle.put(k, v);
                m.put(k, v);
            }
            assertEquals(oracle.size(), m.size());
            java.util.List<Integer> keys = new java.util.ArrayList<>(oracle.keySet());
            java.util.Collections.shuffle(keys, rnd);
            for (int k : keys) {
                assertEquals(oracle.get(k), m.get(k));
                assertEquals(oracle.remove(k), m.remove(k));
            }
            assertEquals(0, m.size());
        }
    }

    @Test
    void backwardShiftWrapSkipsAheadHome() {
        // hole=5, chain 5->6->7->0: the wrapped entry at 0 with home 6 is
        // ahead of the hole on the wrap arm, so it stays (AND first-operand false).
        IntObjectMap<String> m = new IntObjectMap<>(2);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int a = keyWithHome(5, 7, used);
        used.add(a);
        int b = keyWithHome(6, 7, used);
        used.add(b);
        int c = keyWithHome(7, 7, used);
        used.add(c);
        int e = keyWithHome(6, 7, used);
        m.put(a, "a");
        m.put(b, "b");
        m.put(c, "c");
        m.put(e, "e");
        assertEquals("a", m.remove(a));
        assertEquals("b", m.get(b));
        assertEquals("c", m.get(c));
        assertEquals("e", m.get(e));
        assertEquals(3, m.size());
    }
}
