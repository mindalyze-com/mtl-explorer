package com.x8ing.mtl.server.mtlserver.web.services.status;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ApplicationStatusExecutorConfig {

    public static final String APPLICATION_STATUS_EXECUTOR = "applicationStatusExecutor";
    private static final String THREAD_NAME_PREFIX = "application-status-";

    @Bean(name = APPLICATION_STATUS_EXECUTOR, destroyMethod = "shutdown")
    public ExecutorService applicationStatusExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(THREAD_NAME_PREFIX, 0).factory()
        );
    }
}
