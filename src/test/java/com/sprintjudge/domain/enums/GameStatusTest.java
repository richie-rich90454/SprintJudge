package com.sprintjudge.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameStatusTest {

    @Test
    void allConstantsAreDefined() {
        GameStatus[] all = GameStatus.values();
        assertEquals(4, all.length);
        assertSame(GameStatus.LOBBY, GameStatus.valueOf("LOBBY"));
        assertSame(GameStatus.ACTIVE, GameStatus.valueOf("ACTIVE"));
        assertSame(GameStatus.REVIEW, GameStatus.valueOf("REVIEW"));
        assertSame(GameStatus.ENDED, GameStatus.valueOf("ENDED"));
    }

    @Test
    void fromNormalizesCaseAndWhitespace() {
        assertSame(GameStatus.LOBBY, GameStatus.from("lobby"));
        assertSame(GameStatus.ACTIVE, GameStatus.from(" ACTIVE "));
        assertSame(GameStatus.REVIEW, GameStatus.from("Review"));
        assertSame(GameStatus.ENDED, GameStatus.from("ended"));
    }

    @Test
    void fromThrowsOnUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> GameStatus.from("NOPE"));
    }
}
