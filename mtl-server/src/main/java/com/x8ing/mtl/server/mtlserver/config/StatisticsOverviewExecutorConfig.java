package com.x8ing.mtl.server.mtlserver.config;

import com.x8ing.mtl.server.mtlserver.utils.ThreadFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class StatisticsOverviewExecutorConfig {

    private static final int STATISTICS_OVERVIEW_QUERY_PARALLELISM = 3;
    private static final String STATISTICS_OVERVIEW_THREAD_PREFIX = "stats-overview";

    @Bean(name = "statisticsOverviewExecutor", destroyMethod = "shutdown")
    public ExecutorService statisticsOverviewExecutor() {
        return Executors.newFixedThreadPool(
                STATISTICS_OVERVIEW_QUERY_PARALLELISM,
                ThreadFactories.namedDaemon(STATISTICS_OVERVIEW_THREAD_PREFIX)
        );
    }
}
