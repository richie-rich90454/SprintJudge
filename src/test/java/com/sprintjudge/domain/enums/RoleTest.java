package com.sprintjudge.domain.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("all")
class RoleTest {

	private static final List<String> ALL = Arrays.asList(Role.ADMIN, Role.PLAYER);

	@Test
	void allConstantsAreDefined() {
		assertEquals(2, ALL.size());
		assertEquals(Role.ADMIN, Role.from("ADMIN"));
		assertEquals(Role.PLAYER, Role.from("PLAYER"));
	}

	@Test
	void fromNormalizesCaseAndWhitespace() {
		assertEquals(Role.ADMIN, Role.from("admin"));
		assertEquals(Role.PLAYER, Role.from(" PLAYER "));
	}

	@Test
	void fromThrowsOnUnknownValue() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				Role.from("GUEST");
			}
		});
	}

	@Test
	void fromNullIsRequired() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				Role.from(null);
			}
		});
	}

	@Test
	void fromBlankIsRequired() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				Role.from("");
			}
		});
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				Role.from("   ");
			}
		});
	}

	@Test
	void everyValueRoundTrips() {
		for (String role : ALL) {
			assertEquals(role, Role.from(role));
			assertEquals(role, Role.from(role.toLowerCase()));
		}
	}
}
