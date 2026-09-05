package com.sprintjudge.service.room;

import com.sprintjudge.service.GameRoom;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomRegistryBreadthTest {

    private GameRoom room(String pin) {
        return new GameRoom("s", "q", pin, "LOBBY");
    }

    @Test
    void putOverwritesExistingRoom() {
        RoomRegistry r = new RoomRegistry();
        GameRoom first = room("111111");
        GameRoom second = room("111111");
        r.put(111111, first);
        r.put(111111, second);
        assertSame(second, r.get(111111));
        assertEquals(1, r.size());
    }

    @Test
    void removeTwiceSecondReturnsNull() {
        RoomRegistry r = new RoomRegistry();
        r.put(222222, room("222222"));
        assertNotNull(r.remove(222222));
        assertNull(r.remove(222222));
        assertEquals(0, r.size());
    }

    @Test
    void snapshotHoldsEveryRoom() {
        RoomRegistry r = new RoomRegistry();
        r.put(1, room("1"));
        r.put(2, room("2"));
        r.put(3, room("3"));
        assertEquals(3, r.snapshot().size());
        assertEquals(3, r.size());
    }

    @Test
    void snapshotIsADetachedCopy() {
        RoomRegistry r = new RoomRegistry();
        r.put(1, room("1"));
        r.snapshot().clear();
        assertEquals(1, r.size());
        assertNotNull(r.get(1));
    }

    @Test
    void sizeTracksPutsAndRemoves() {
        RoomRegistry r = new RoomRegistry();
        assertEquals(0, r.size());
        r.put(10, room("10"));
        r.put(20, room("20"));
        assertEquals(2, r.size());
        r.remove(10);
        assertEquals(1, r.size());
        r.remove(20);
        assertEquals(0, r.size());
    }

    @Test
    void computeIfAbsentFactoryReceivesPinKey() {
        RoomRegistry r = new RoomRegistry();
        int[] seen = {-1};
        r.computeIfAbsent(777001, p -> {
            seen[0] = p;
            return room("777001");
        });
        assertEquals(777001, seen[0]);
    }

    @Test
    void putIfAbsentNeverOverwrites() {
        RoomRegistry r = new RoomRegistry();
        GameRoom first = room("333333");
        GameRoom second = room("333333");
        r.putIfAbsent(333333, first);
        assertSame(first, r.putIfAbsent(333333, second));
        assertSame(first, r.get(333333));
    }

    @Test
    void distinctPinsAreIndependent() {
        RoomRegistry r = new RoomRegistry();
        GameRoom a = room("444441");
        GameRoom b = room("444442");
        r.put(444441, a);
        r.put(444442, b);
        assertSame(a, r.get(444441));
        assertSame(b, r.get(444442));
        r.remove(444441);
        assertNull(r.get(444441));
        assertSame(b, r.get(444442));
    }

    @Test
    void overwriteViaPutThenRemoveClears() {
        RoomRegistry r = new RoomRegistry();
        r.put(555555, room("555555"));
        r.put(555555, room("555555"));
        assertNotNull(r.remove(555555));
        assertNull(r.get(555555));
        assertTrue(r.snapshot().isEmpty());
    }

    @Test
    void getMissingPinsReturnsNull() {
        RoomRegistry r = new RoomRegistry();
        assertNull(r.get(0));
        assertNull(r.get(Integer.MAX_VALUE));
        assertNull(r.get(-5));
    }

    @Test
    void removeMissingKeepsSize() {
        RoomRegistry r = new RoomRegistry();
        r.put(666666, room("666666"));
        assertNull(r.remove(666667));
        assertEquals(1, r.size());
    }

    @Test
    void concurrencySmokeKeepsEveryKey() throws Exception {
        RoomRegistry r = new RoomRegistry();
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<Integer> keys = new HashSet<>();
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) keys.add(700000 + t * 1000 + i);
            final int base = 700000 + t * 1000;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        int pin = base + i;
                        r.put(pin, room(String.valueOf(pin)));
                        assertNotNull(r.get(pin));
                        r.computeIfAbsent(pin, p -> room(String.valueOf(p)));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(keys.size(), r.size());
        assertEquals(keys.size(), r.snapshot().size());
    }
}
