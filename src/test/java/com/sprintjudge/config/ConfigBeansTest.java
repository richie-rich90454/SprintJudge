package com.sprintjudge.config;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigBeansTest {

    @Test
    void appConfigDslUsesSqliteDialect() {
        DataSource ds = mock(DataSource.class);
        var dsl = new AppConfig().dslContext(ds);
        assertNotNull(dsl);
        assertEquals(SQLDialect.SQLITE, dsl.dialect());
        DSL.using(ds, SQLDialect.SQLITE);
    }

    @Test
    void appConfigVirtualThreadsExecute() throws Exception {
        Executor executor = new AppConfig().virtualThreadExecutor();
        assertNotNull(executor);
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void webSocketExporterBean() {
        assertNotNull(new WebSocketConfig().serverEndpointExporter());
    }

    @Test
    void webSocketBufferLimits() {
        var container = new WebSocketConfig().servletServerContainer();
        assertNotNull(container);
        assertEquals(256 * 1024, container.getMaxTextMessageBufferSize());
        assertEquals(256 * 1024, container.getMaxBinaryMessageBufferSize());
    }

    @Test
    void spaRegistersTwoForwardControllers() {
        ViewControllerRegistry registry = mock(ViewControllerRegistry.class);
        var registration = mock(org.springframework.web.servlet.config.annotation.ViewControllerRegistration.class);
        when(registry.addViewController(anyString())).thenReturn(registration);
        new SpaWebConfig().addViewControllers(registry);
        verify(registry).addViewController("/{spring:[^.]*}");
        verify(registry).addViewController("/**/{spring:[^.]*}");
        verify(registration, org.mockito.Mockito.times(2)).setViewName("forward:/index.html");
    }

    @Test
    void sqlScriptRunnerFailsOnBadLocation(@TempDir Path tmp) throws Exception {
        org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite::memory:");
        assertThrows(IllegalStateException.class,
                () -> SqlScriptRunner.runClasspath(ds, "db/migration/does-not-exist.sql"));
    }

    @Test
    void sqlScriptRunnerFailsOnBrokenDataSource() {
        DataSource ds = mock(DataSource.class);
        try {
            when(ds.getConnection()).thenThrow(new java.sql.SQLException("down"));
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        assertThrows(IllegalStateException.class,
                () -> SqlScriptRunner.runClasspath(ds, "db/migration/V1__init.sql"));
    }

    @Test
    void sqlScriptRunnerAppliesSchema(@TempDir Path tmp) throws Exception {
        Path db = tmp.resolve("schema.db");
        org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + db);
        SqlScriptRunner.runClasspath(ds, "db/migration/V1__init.sql");
        try (var conn = ds.getConnection();
             var rs = conn.createStatement().executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table'")) {
            boolean quizzes = false;
            while (rs.next()) {
                if ("quizzes".equals(rs.getString(1))) quizzes = true;
            }
            assertTrue(quizzes);
        }
    }

    @Test
    void databaseFilePathKept(@TempDir Path tmp) throws Exception {
        DatabaseConfig cfg = new DatabaseConfig();
        Path db = tmp.resolve("custom.db");
        ReflectionTestUtils.setField(cfg, "dbPath", db.toString());
        DataSource ds = cfg.dataSource();
        assertNotNull(ds);
        assertTrue(Files.exists(db));
    }

    @Test
    void databaseDirectoryAppendsFileName(@TempDir Path tmp) throws Exception {
        DatabaseConfig cfg = new DatabaseConfig();
        ReflectionTestUtils.setField(cfg, "dbPath", tmp.toString());
        cfg.dataSource();
        assertTrue(Files.exists(tmp.resolve("sprintjudge.db")));
    }

    @Test
    void databaseUppercaseDbSuffixKept(@TempDir Path tmp) throws Exception {
        DatabaseConfig cfg = new DatabaseConfig();
        Path db = tmp.resolve("UPPER.DB");
        ReflectionTestUtils.setField(cfg, "dbPath", db.toString());
        cfg.dataSource();
        assertTrue(Files.exists(db));
    }

    @Test
    void databaseUrlHasPragmas(@TempDir Path tmp) throws Exception {
        DatabaseConfig cfg = new DatabaseConfig();
        ReflectionTestUtils.setField(cfg, "dbPath", tmp.resolve("p.db").toString());
        DriverManagerDataSource ds = (DriverManagerDataSource) cfg.dataSource();
        assertTrue(ds.getUrl().contains("journal_mode=WAL"));
        assertTrue(ds.getUrl().contains("foreign_keys=ON"));
    }

    @Test
    void databaseTransactionManager() {
        DataSource ds = mock(DataSource.class);
        var tm = new DatabaseConfig().transactionManager(ds);
        assertInstanceOf(org.springframework.jdbc.datasource.DataSourceTransactionManager.class, tm);
    }
}
