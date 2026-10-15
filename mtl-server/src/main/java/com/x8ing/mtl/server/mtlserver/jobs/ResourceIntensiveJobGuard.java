package com.x8ing.mtl.server.mtlserver.jobs;

import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Prevents scheduled jobs with high allocation or database load from running together.
 * A skipped job retries on its normal schedule instead of occupying a scheduler thread.
 */
@Component
public class ResourceIntensiveJobGuard {

    private final ReentrantLock executionLock = new ReentrantLock();
    private volatile String owner;

    public String heldBy() { return owner; }

    public ProcessingDetails.BatchOutcome runReportedBatch(String job, Supplier<ProcessingDetails.BatchOutcome> action) {
        if (!executionLock.tryLock()) {
            return ProcessingDetails.BatchOutcome.waiting(List.of(new ProcessingDetails.Blocker(
                    ProcessingDetails.Kind.LOCK, ProcessingDetails.RESOURCE_LOCK, "RESOURCE_BUSY",
                    ProcessingDetails.Scope.WHOLE_JOB, ProcessingDetails.Resolution.AUTOMATIC, owner)));
        }
        owner = job;
        try {
            return action.get();
        } finally {
            owner = null;
            executionLock.unlock();
        }
    }

    public boolean runBatchIfAvailable(BooleanSupplier action) {
        if (!executionLock.tryLock()) return false;
        try {
            return action.getAsBoolean();
        } finally {
            executionLock.unlock();
        }
    }
}
