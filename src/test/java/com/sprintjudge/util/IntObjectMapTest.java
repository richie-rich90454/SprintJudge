package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IntObjectMapTest {

    private static int mixKey(int k) {
        k *= 0x9E3779B9;
        k ^= k >>> 16;
        return k;
    }

    private static int findKeyWithHome(int home, int mask, int... excludes) {
        Set<Integer> ex = new HashSet<>();
        for (int e : excludes) ex.add(e);
        for (int k = 1; k < 10_000_000; k++) {
            if (ex.contains(k)) continue;
            if ((mixKey(k) & mask) == home) return k;
        }
        throw new IllegalStateException("no key for home " + home);
    }

    @Test
    void emptyBehaviors() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        assertEquals(0, m.size());
        assertNull(m.get(1));
        assertNull(m.remove(1));
    }

    @Test
    void putGetOverwriteAndMissing() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        assertNull(m.put(10, "a"));
        assertEquals("a", m.get(10));
        assertEquals("a", m.put(10, "b"));
        assertEquals("b", m.get(10));
        assertNull(m.get(999));
        assertNull(m.remove(999));
    }

    @Test
    void growTriggered() {
        IntObjectMap<String> m = new IntObjectMap<>(1); // cap 8, threshold 4
        for (int i = 0; i < 5; i++) m.put(1000 + i * 97, "v" + i);
        for (int i = 0; i < 5; i++) assertEquals("v" + i, m.get(1000 + i * 97));
        assertEquals(5, m.size());
    }

    @Test
    void forEachVisitsAll() {
        IntObjectMap<String> m = new IntObjectMap<>(4);
        m.put(1, "a");
        m.put(2, "b");
        Set<String> seen = new HashSet<>();
        m.forEach((k, v) -> seen.add(k + v));
        assertEquals(Set.of("1a", "2b"), seen);
    }

    // cap 16, mask 15. Removing q (idx7) wraps: r(idx0, home0) NOT moved (AND false),
    // s(idx1, home1) IS moved (AND true). Exposes both wrap-shift inner outcomes.
    @Test
    void backwardShiftWrapBranches() {
        IntObjectMap<String> m = new IntObjectMap<>(5); // cap 16
        int mask = 15;
        int p = findKeyWithHome(6, mask);
        int q = findKeyWithHome(6, mask, p);
        int r = findKeyWithHome(0, mask, p, q);
        int s = findKeyWithHome(1, mask, p, q, r);
        m.put(p, "p");
        m.put(q, "q");
        m.put(r, "r");
        m.put(s, "s");
        assertEquals("q", m.remove(q));
        assertEquals("p", m.get(p));
        assertEquals("r", m.get(r));
        assertEquals("s", m.get(s));
        assertEquals("p", m.remove(p));
        assertEquals("r", m.remove(r));
        assertEquals("s", m.remove(s));
        assertEquals(0, m.size());
    }

    // cap 16, mask 15. Removing b(idx3): c(idx4, home4) NOT moved (OR false),
    // d(idx5, home5) IS moved (OR true). Exposes both non-wrap shift inner outcomes.
    @Test
    void backwardShiftNonWrapBranches() {
        IntObjectMap<String> m = new IntObjectMap<>(5);
        int mask = 15;
        int a = findKeyWithHome(2, mask);
        int b = findKeyWithHome(2, mask, a);
        int c = findKeyWithHome(4, mask, a, b);
        int d = findKeyWithHome(5, mask, a, b, c);
        m.put(a, "a");
        m.put(b, "b");
        m.put(c, "c");
        m.put(d, "d");
        assertEquals("b", m.remove(b));
        assertEquals("a", m.get(a));
        assertEquals("c", m.get(c));
        assertEquals("d", m.get(d));
        assertEquals("a", m.remove(a));
        assertEquals("c", m.remove(c));
        assertEquals("d", m.remove(d));
        assertEquals(0, m.size());
    }

    @Test
    void randomizedShiftStress() {
        IntObjectMap<String> m = new IntObjectMap<>(8);
        Random rnd = new Random(42);
        Set<Integer> keys = new HashSet<>();
        for (int i = 0; i < 3000; i++) {
            int k = rnd.nextInt(200000);
            keys.add(k);
            m.put(k, "v" + k);
        }
        List<Integer> list = new ArrayList<>(keys);
        Collections.shuffle(list, rnd);
        for (int k : list) assertEquals("v" + k, m.remove(k));
        assertEquals(0, m.size());
        assertNull(m.get(0));
    }

    // High-load small table: load factor ~0.875 forces long probe chains that wrap
    // across the index-0 boundary, exercising the hole>j (wrap) shift branch and
    // every inner (home<=hole||home>j) / (home<=hole&&home>j) outcome.
    @Test
    void highLoadWrapShiftStress() {
        for (int trial = 0; trial < 50; trial++) {
            IntObjectMap<String> m = new IntObjectMap<>(2); // cap 8, threshold 4
            List<Integer> keys = new ArrayList<>();
            for (int i = 0; i < 7; i++) keys.add(1000 + i);
            for (int k : keys) m.put(k, "v" + k);
            Collections.shuffle(keys, new Random(trial));
            for (int k : keys) assertEquals("v" + k, m.remove(k));
            assertEquals(0, m.size());
            assertNull(m.get(1000));
        }
    }
}
