package com.sprintjudge.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    @Bean
    public DSLContext dslContext(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.SQLITE);
    }

    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("sprintjudge-vt-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
