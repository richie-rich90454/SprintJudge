package com.openquiz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    @Value("${openquiz.db.path:./openquiz.db}")
    private String dbPath;

    @Bean
    public DataSource dataSource() throws IOException {
        // SQLite creates the database file but never its parent directories —
        // a fresh prod run on any OS would fail without this.
        Path dbFile = Path.of(dbPath).toAbsolutePath().normalize();
        if (dbFile.getParent() != null) {
            Files.createDirectories(dbFile.getParent());
        }
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + dbFile + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=ON");
        SqlScriptRunner.runClasspath(ds, "db/migration/V1__init.sql");
        return ds;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
