package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTest {

    @Test
    void withScoreReplacesScoreOnly() {
        Player p = new Player("u", "Ann", 10, "s", true, "tok");
        Player next = p.withScore(99);
        assertEquals(99, next.score());
        assertEquals("u", next.uuid());
        assertEquals("Ann", next.name());
        assertEquals("s", next.sessionId());
        assertTrue(next.connected());
        assertEquals("tok", next.token());
    }

    @Test
    void withSessionReplacesSessionAndReconnects() {
        Player p = new Player("u", "Ann", 10, "old", false, "tok");
        Player next = p.withSession("new");
        assertEquals("new", next.sessionId());
        assertTrue(next.connected());
        assertEquals(10, next.score());
        assertEquals("tok", next.token());
    }

    @Test
    void disconnectedMarksOffline() {
        Player p = new Player("u", "Ann", 10, "s", true, "tok");
        Player next = p.disconnected();
        assertFalse(next.connected());
        assertEquals("s", next.sessionId());
        assertEquals(10, next.score());
        assertEquals("tok", next.token());
    }

    @Test
    void convenienceCtorDefaultsTokenToNull() {
        Player p = new Player("u", "Ann", 0, "s", true);
        assertNull(p.token());
        assertTrue(p.connected());
    }
}
