package com.sprintjudge.domain.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("null")
class GameStatusTest {

	private static final List<String> ALL = Arrays.asList(GameStatus.LOBBY, GameStatus.ACTIVE, GameStatus.REVIEW,
			GameStatus.ENDED);

	@Test
	void allConstantsAreDefined() {
		assertEquals(4, ALL.size());
		assertEquals(GameStatus.LOBBY, GameStatus.from("LOBBY"));
		assertEquals(GameStatus.ACTIVE, GameStatus.from("ACTIVE"));
		assertEquals(GameStatus.REVIEW, GameStatus.from("REVIEW"));
		assertEquals(GameStatus.ENDED, GameStatus.from("ENDED"));
	}

	@Test
	void fromNormalizesCaseAndWhitespace() {
		assertEquals(GameStatus.LOBBY, GameStatus.from("lobby"));
		assertEquals(GameStatus.ACTIVE, GameStatus.from(" ACTIVE "));
		assertEquals(GameStatus.REVIEW, GameStatus.from("Review"));
		assertEquals(GameStatus.ENDED, GameStatus.from("ended"));
	}

	@Test
	void fromThrowsOnUnknownValue() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				GameStatus.from("NOPE");
			}
		});
	}

	@Test
	void fromNullIsRequired() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				GameStatus.from(null);
			}
		});
	}

	@Test
	void fromBlankIsRequired() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				GameStatus.from("");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				GameStatus.from("   ");
			}
		});
	}

	@Test
	void everyValueRoundTrips() {
		for (String status : ALL) {
			assertEquals(status, GameStatus.from(status));
			assertEquals(status, GameStatus.from(status.toLowerCase()));
		}
	}
}
