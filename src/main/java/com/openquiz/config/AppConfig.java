package com.openquiz.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Configuration
public class AppConfig {

    @Bean
    public DSLContext dslContext(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.SQLITE);
    }

    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("openquiz-vt-");
        executor.setVirtualThreads(true);
        return executor;
    }

    @Bean
    public Semaphore executionSlots(
            @org.springframework.beans.factory.annotation.Value("${openquiz.executor.max-concurrent:100}") int max) {
        return new Semaphore(Math.max(1, max));
    }
}
