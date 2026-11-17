package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class PlayerTest {

	@Test
	void withScoreReplacesScoreOnly() {
		Player p = new Player("u", "Ann", 10, "s", true, "tok");
		Player next = p.withScore(99);
		assertEquals(99, next.getScore());
		assertEquals("u", next.getUuid());
		assertEquals("Ann", next.getName());
		assertEquals("s", next.getSessionId());
		assertTrue(next.isConnected());
		assertEquals("tok", next.getToken());
	}

	@Test
	void withSessionReplacesSessionAndReconnects() {
		Player p = new Player("u", "Ann", 10, "old", false, "tok");
		Player next = p.withSession("new");
		assertEquals("new", next.getSessionId());
		assertTrue(next.isConnected());
		assertEquals(10, next.getScore());
		assertEquals("tok", next.getToken());
	}

	@Test
	void disconnectedMarksOffline() {
		Player p = new Player("u", "Ann", 10, "s", true, "tok");
		Player next = p.disconnected();
		assertFalse(next.isConnected());
		assertEquals("s", next.getSessionId());
		assertEquals(10, next.getScore());
		assertEquals("tok", next.getToken());
	}

	@Test
	void convenienceCtorDefaultsTokenToNull() {
		Player p = new Player("u", "Ann", 0, "s", true);
		assertNull(p.getToken());
		assertTrue(p.isConnected());
	}
}
