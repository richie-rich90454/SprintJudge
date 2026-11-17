package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class SqlScriptRunnerTest {

	@TempDir
	Path tmp;

	private SQLiteDataSource ds() {
		SQLiteDataSource ds = new SQLiteDataSource();
		ds.setUrl("jdbc:sqlite:" + tmp.resolve("t.db"));
		return ds;
	}

	private void createBare(SQLiteDataSource ds) throws Exception {
		try (java.sql.Connection conn = ds.getConnection(); java.sql.Statement st = conn.createStatement()) {
			st.execute("CREATE TABLE game_sessions (id TEXT PRIMARY KEY)");
		}
	}

	private boolean hasColumn(SQLiteDataSource ds, String column) throws Exception {
		try (java.sql.Connection conn = ds.getConnection();
				java.sql.ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(game_sessions)")) {
			while (rs.next()) {
				if (column.equals(rs.getString("name")))
					return true;
			}
			return false;
		}
	}

	@Test
	void addsMissingColumn() throws Exception {
		SQLiteDataSource ds = ds();
		createBare(ds);
		SqlScriptRunner.ensureColumn(ds, "game_sessions", "game_mode", "TEXT DEFAULT 'STANDARD'");
		assertTrue(hasColumn(ds, "game_mode"));
	}

	@Test
	void skipsPresentColumn() throws Exception {
		SQLiteDataSource ds = ds();
		createBare(ds);
		SqlScriptRunner.ensureColumn(ds, "game_sessions", "game_mode", "TEXT DEFAULT 'STANDARD'");
		// Second run is a no-op, never a duplicate-column error.
		SqlScriptRunner.ensureColumn(ds, "game_sessions", "game_mode", "TEXT DEFAULT 'STANDARD'");
		assertTrue(hasColumn(ds, "game_mode"));
	}

	@Test
	void missingTableFailsCleanly() {
		SQLiteDataSource ds = ds();
		IllegalStateException e = assertThrows(IllegalStateException.class,
				new org.junit.jupiter.api.function.Executable() {
					@Override
					public void execute() {
						SqlScriptRunner.ensureColumn(ds, "no_such_table", "c", "TEXT");
					}
				});
		assertEquals("Schema migration failed: no_such_table.c", e.getMessage());
	}

	@Test
	void missingScriptFailsCleanly() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
				new org.junit.jupiter.api.function.Executable() {
					@Override
					public void execute() {
						SqlScriptRunner.runClasspath(ds(), "db/migration/does-not-exist.sql");
					}
				});
		assertTrue(e.getMessage().startsWith("Schema initialization failed"));
	}
}
