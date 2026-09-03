package com.sprintjudge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableTransactionManagement
public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${sprintjudge.db.path:./sprintjudge.db}")
    private String dbPath;

    @Bean
    public DataSource dataSource() throws IOException {
        Path dbFile;
        try {
            dbFile = resolveDbFile();
            Files.createDirectories(dbFile.getParent());
        } catch (IOException e) {
            // Configured path unreachable (missing drive, permissions) — fall
            // back to ./sprintjudge.db so the app always boots.
            log.error("Cannot create DB dir for '{}', falling back to ./sprintjudge.db", dbPath, e);
            dbFile = Path.of("./sprintjudge.db").toAbsolutePath().normalize();
            Files.createDirectories(dbFile.getParent());
        }
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + dbFile + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=ON");
        SqlScriptRunner.runClasspath(ds, "db/migration/V1__init.sql");
        return ds;
    }

    /**
     * Accepts either a file path ending in .db or a bare directory; when the
     * configured value lacks a .db suffix it is treated as a directory and
     * sprintjudge.db is appended automatically.
     */
    private Path resolveDbFile() {
        Path p = Path.of(dbPath).toAbsolutePath().normalize();
        if (!p.toString().toLowerCase().endsWith(".db")) {
            p = p.resolve("sprintjudge.db");
        }
        return p;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
