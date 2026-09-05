package com.sprintjudge.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @Test
    void allConstantsAreDefined() {
        Role[] all = Role.values();
        assertEquals(2, all.length);
        assertSame(Role.ADMIN, Role.valueOf("ADMIN"));
        assertSame(Role.PLAYER, Role.valueOf("PLAYER"));
    }

    @Test
    void fromNormalizesCaseAndWhitespace() {
        assertSame(Role.ADMIN, Role.from("admin"));
        assertSame(Role.PLAYER, Role.from(" PLAYER "));
    }

    @Test
    void fromThrowsOnUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> Role.from("GUEST"));
    }

    @Test
    void fromNullIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> Role.from(null));
    }

    @Test
    void fromBlankIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> Role.from(""));
        assertThrows(IllegalArgumentException.class, () -> Role.from("   "));
    }

    @Test
    void everyValueRoundTrips() {
        for (Role r : Role.values()) {
            assertSame(r, Role.from(r.name()));
            assertSame(r, Role.from(r.name().toLowerCase()));
        }
    }
}
