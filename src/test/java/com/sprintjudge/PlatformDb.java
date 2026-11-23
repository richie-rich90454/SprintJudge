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
import java.util.UUID;

/** Shared in-memory SQLite database for the platform vertical-slice tests. */
@SuppressWarnings("all")
public final class PlatformDb {

	private PlatformDb() {
	}

	public static Db open() throws Exception {
		SQLiteDataSource ds = new SQLiteDataSource();
		ds.setUrl("jdbc:sqlite:file:platform_" + UUID.randomUUID().toString().replace("-", "")
				+ "?mode=memory&cache=shared");
		Connection keepAlive = ds.getConnection();
		for (String name : new String[]{"V1__init.sql", "V2__room_events.sql", "V3__submission-millis.sql"}) {
			ScriptUtils.executeSqlScript(keepAlive,
					new EncodedResource(new ClassPathResource("db/migration/" + name), StandardCharsets.UTF_8));
		}
		DSLContext dsl = DSL.using(keepAlive, SQLDialect.SQLITE);
		dsl.execute("PRAGMA foreign_keys = OFF");
		return new Db(dsl, ds, keepAlive);
	}

	public static final class Db {
		public final DSLContext dsl;
		public final SQLiteDataSource dataSource;
		final Connection keepAlive;

		private Db(DSLContext dsl, SQLiteDataSource dataSource, Connection keepAlive) {
			this.dsl = dsl;
			this.dataSource = dataSource;
			this.keepAlive = keepAlive;
		}
	}
}
