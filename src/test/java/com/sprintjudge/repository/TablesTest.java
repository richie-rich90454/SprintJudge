package com.sprintjudge.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TablesTest {

    @Test
    void staticFieldsAreExposed() {
        assertNotNull(Tables.USERS);
        assertNotNull(Tables.USERS_ID);
        assertNotNull(Tables.QUIZZES);
        assertNotNull(Tables.QUESTIONS);
        assertNotNull(Tables.GAME_SESSIONS);
        assertNotNull(Tables.SUBMISSIONS);
        assertNotNull(Tables.ADMIN_SETTINGS);
        assertNotNull(Tables.USERS_ROLE);
        assertNotNull(Tables.SESS_STATUS);
        assertNotNull(Tables.SUB_CORRECT);
    }

    @Test
    void privateConstructorIsInvokable() throws Exception {
        Constructor<Tables> c = Tables.class.getDeclaredConstructor();
        c.setAccessible(true);
        c.newInstance();
    }
}
