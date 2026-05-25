package com.x8ing.mtl.server.mtlserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class StatisticsOverviewExecutorConfig {

    private static final int STATISTICS_OVERVIEW_QUERY_PARALLELISM = 3;
    private static final String STATISTICS_OVERVIEW_THREAD_PREFIX = "stats-overview";

    @Bean(name = "statisticsOverviewExecutor", destroyMethod = "shutdown")
    public ExecutorService statisticsOverviewExecutor() {
        return Executors.newFixedThreadPool(
                STATISTICS_OVERVIEW_QUERY_PARALLELISM,
                namedFactory(STATISTICS_OVERVIEW_THREAD_PREFIX)
        );
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
