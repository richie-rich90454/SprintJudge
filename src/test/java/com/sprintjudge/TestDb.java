package com.sprintjudge;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * Shared test helper: builds a real in-memory SQLite {@link DSLContext} with the
 * production schema applied. Used by repository unit tests so they exercise
 * real jOOQ/SQLite behavior instead of mocks.
 */
public final class TestDb {

    private TestDb() {}

    public static DSLContext inMemory() throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite::memory:");
        Connection conn = ds.getConnection();
        ScriptUtils.executeSqlScript(conn,
                new EncodedResource(new ClassPathResource("db/migration/V1__init.sql"), StandardCharsets.UTF_8));
        DSLContext dsl = DSL.using(conn, SQLDialect.SQLITE);
        // Single shared connection: relax FK enforcement so each repository can be
        // exercised in isolation without manufacturing unrelated parent rows.
        dsl.execute("PRAGMA foreign_keys = OFF");
        return dsl;
    }
}
