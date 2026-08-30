package com.sprintjudge.service.room;

import com.sprintjudge.service.GameRoom;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoomRegistryTest {

    private GameRoom room(String pin) {
        return new GameRoom("s", "q", pin, "lobby");
    }

    @Test
    void getAbsentAndPresent() {
        RoomRegistry r = new RoomRegistry();
        assertNull(r.get(123456));
        GameRoom g = room("123456");
        r.put(123456, g);
        assertSame(g, r.get(123456));
        assertEquals(1, r.size());
    }

    @Test
    void computeIfAbsentCallsFactoryOnlyWhenAbsent() {
        RoomRegistry r = new RoomRegistry();
        AtomicInteger calls = new AtomicInteger();
        GameRoom g1 = r.computeIfAbsent(555000, p -> {
            calls.incrementAndGet();
            return room("555000");
        });
        GameRoom g2 = r.computeIfAbsent(555000, p -> {
            calls.incrementAndGet();
            return room("555000");
        });
        assertSame(g1, g2);
        assertEquals(1, calls.get());
    }

    @Test
    void putIfAbsentReturnsExistingWhenPresent() {
        RoomRegistry r = new RoomRegistry();
        GameRoom g1 = room("654321");
        r.put(654321, g1);
        GameRoom g2 = room("654321");
        assertSame(g1, r.putIfAbsent(654321, g2));
        assertSame(g1, r.get(654321));
    }

    @Test
    void putIfAbsentInsertsWhenAbsent() {
        RoomRegistry r = new RoomRegistry();
        GameRoom g = room("222222");
        assertSame(g, r.putIfAbsent(222222, g));
        assertSame(g, r.get(222222));
    }

    @Test
    void removeReturnsRoomOrNull() {
        RoomRegistry r = new RoomRegistry();
        assertNull(r.remove(999999));
        GameRoom g = room("999999");
        r.put(999999, g);
        assertSame(g, r.remove(999999));
        assertNull(r.get(999999));
    }

    @Test
    void snapshotEmptyAndPopulated() {
        RoomRegistry r = new RoomRegistry();
        assertTrue(r.snapshot().isEmpty());
        GameRoom g = room("111111");
        r.put(111111, g);
        assertEquals(1, r.snapshot().size());
        assertSame(g, r.snapshot().get(0));
    }
}
