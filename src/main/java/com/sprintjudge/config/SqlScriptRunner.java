package com.sprintjudge.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;

/**
 * Executes the bundled schema DDL directly against a connection.
 *
 * <p>
 * Flyway offers no SQLite support (no official module exists), so the migration
 * path for this project is a single idempotent DDL script (every statement is
 * CREATE TABLE/INDEX IF NOT EXISTS) applied exactly once at DataSource
 * construction - guaranteed to precede any repository call.
 */
final class SqlScriptRunner {

	private SqlScriptRunner() {
	}

	static void runClasspath(javax.sql.DataSource dataSource, String classpathLocation) {
		try (java.sql.Connection conn = dataSource.getConnection()) {
			EncodedResource resource = new EncodedResource(new ClassPathResource(classpathLocation),
					StandardCharsets.UTF_8);
			ScriptUtils.executeSqlScript(conn, resource);
		} catch (Exception e) {
			throw new IllegalStateException("Schema initialization failed: " + classpathLocation, e);
		}
	}

	/**
	 * Idempotent additive migration for databases created before a column existed:
	 * probes the column and ALTERs only when absent. Callers pass internal
	 * constants only - never user input (identifiers are inlined).
	 */
	static void ensureColumn(javax.sql.DataSource dataSource, String table, String column, String columnDdl) {
		try (java.sql.Connection conn = dataSource.getConnection()) {
			// ponytail: probe Statement/RS ride on conn.close(); runs once at startup
			conn.prepareStatement("SELECT " + column + " FROM " + table + " LIMIT 0").executeQuery().next();
			return;
		} catch (Exception ignored) {
			// Absent (or unreadable): fall through to ALTER.
		}
		try (java.sql.Connection conn = dataSource.getConnection()) {
			try (java.sql.Statement alter = conn.createStatement()) {
				alter.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDdl);
			}
		} catch (Exception e) {
			throw new IllegalStateException("Schema migration failed: " + table + "." + column, e);
		}
	}
}
