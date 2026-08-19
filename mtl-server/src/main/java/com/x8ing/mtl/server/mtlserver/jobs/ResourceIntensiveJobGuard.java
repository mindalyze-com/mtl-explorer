package com.x8ing.mtl.server.mtlserver.jobs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Prevents scheduled jobs with high allocation or database load from running together.
 * A skipped job retries on its normal schedule instead of occupying a scheduler thread.
 */
@Component
@Slf4j
public class ResourceIntensiveJobGuard {

    private final ReentrantLock executionLock = new ReentrantLock();

    public boolean runIfAvailable(Class<?> jobType, Runnable action) {
        if (!executionLock.tryLock()) {
            log.debug("Skipping {} while another resource-intensive job is running", jobType.getSimpleName());
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            executionLock.unlock();
        }
    }
}
