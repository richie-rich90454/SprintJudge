package com.openquiz.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;

/**
 * Executes the bundled schema DDL directly against a connection.
 *
 * <p>Flyway offers no SQLite support (no official module exists), so the
 * migration path for this project is a single idempotent DDL script
 * (every statement is CREATE TABLE/INDEX IF NOT EXISTS) applied exactly once
 * at DataSource construction — guaranteed to precede any repository call.
 */
final class SqlScriptRunner {

    private SqlScriptRunner() {}

    static void runClasspath(javax.sql.DataSource dataSource, String classpathLocation) {
        try (var conn = dataSource.getConnection()) {
            var resource = new EncodedResource(
                    new ClassPathResource(classpathLocation), StandardCharsets.UTF_8);
            ScriptUtils.executeSqlScript(conn, resource);
        } catch (Exception e) {
            throw new IllegalStateException("Schema initialization failed: " + classpathLocation, e);
        }
    }
}
