package com.sprintjudge.util;

import java.util.Arrays;

/**
 * Open-addressing (linear probing) int->Object map for hot primitive keys such
 * as six-digit game PINs. No boxing, no treeification, cache-friendly probes.
 * Not thread-safe by itself; guard externally or wrap per-shard.
 */
public final class IntObjectMap<V> {

    private static final int[] NO_KEY = new int[0];
    private static final double LOAD_FACTOR = 0.6;

    private int[] keys = NO_KEY;
    private Object[] values = new Object[0];
    private boolean[] used = new boolean[0];
    private int size;
    private int mask = -1;
    private int threshold;

    public IntObjectMap(int expectedCapacity) {
        int cap = tableSize(Math.max(4, (int) (expectedCapacity / LOAD_FACTOR) + 1));
        allocate(cap);
    }

    private static int tableSize(int min) {
        int p = Integer.highestOneBit(Math.max(min - 1, 4));
        return p << 1;
    }

    private void allocate(int cap) {
        keys = new int[cap];
        Arrays.fill(keys, 0);
        values = new Object[cap];
        used = new boolean[cap];
        mask = cap - 1;
        threshold = (int) (cap * LOAD_FACTOR);
    }

    private int probe(int key) {
        int h = mix(key);
        int i = h & mask;
        while (used[i] && keys[i] != key) {
            i = (i + 1) & mask;
        }
        return i;
    }

    private static int mix(int k) {
        k *= 0x9E3779B9;
        k ^= k >>> 16;
        return k;
    }

    @SuppressWarnings("unchecked")
    public V get(int key) {
        if (size == 0) return null;
        int i = probe(key);
        return used[i] ? (V) values[i] : null;
    }

    @SuppressWarnings("unchecked")
    public V put(int key, V value) {
        if (size + 1 > threshold) grow();
        int i = probe(key);
        V old;
        if (used[i]) {
            old = (V) values[i];
        } else {
            used[i] = true;
            keys[i] = key;
            size++;
            old = null;
        }
        values[i] = value;
        return old;
    }

    @SuppressWarnings("unchecked")
    public V remove(int key) {
        if (size == 0) return null;
        int i = probe(key);
        if (!used[i]) return null;
        V old = (V) values[i];
        values[i] = null;
        // Backward-shift deletion keeps probe chains intact.
        int hole = i;
        int j = (i + 1) & mask;
        while (used[j]) {
            int home = mix(keys[j]) & mask;
            if ((hole < j) ? (home <= hole || home > j) : (home <= hole && home > j)) {
                keys[hole] = keys[j];
                values[hole] = values[j];
                hole = j;
            }
            j = (j + 1) & mask;
        }
        used[hole] = false;
        size--;
        return old;
    }

    private void grow() {
        int[] oldKeys = keys;
        Object[] oldVals = values;
        boolean[] oldUsed = used;
        int oldCap = oldKeys.length;
        allocate(oldCap << 1);
        for (int i = 0; i < oldCap; i++) {
            if (oldUsed[i]) {
                int ni = probe(oldKeys[i]);
                used[ni] = true;
                keys[ni] = oldKeys[i];
                values[ni] = oldVals[i];
            }
        }
    }

    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    public void forEach(IntObjectConsumer<V> consumer) {
        for (int i = 0; i < keys.length; i++) {
            if (used[i]) consumer.accept(keys[i], (V) values[i]);
        }
    }

    @FunctionalInterface
    public interface IntObjectConsumer<V> {
        void accept(int key, V value);
    }
}
