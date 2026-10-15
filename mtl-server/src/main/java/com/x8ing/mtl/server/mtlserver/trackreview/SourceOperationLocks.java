package com.x8ing.mtl.server.mtlserver.trackreview;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes watcher imports, source assessments, and review decisions.
 */
public final class SourceOperationLocks {
    private static final int LOCK_COUNT = 128;
    private static final ReentrantLock[] LOCKS = new ReentrantLock[LOCK_COUNT];

    static {
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new ReentrantLock();
    }

    private SourceOperationLocks() {
    }

    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    private static Lease acquire(long sourceId) {
        ReentrantLock lock = LOCKS[Math.floorMod(Long.hashCode(sourceId), LOCK_COUNT)];
        lock.lock();
        return lock::unlock;
    }

    /**
     * Holds the source lock across preparation and subsequent database transactions.
     */
    public static Lease forOperation(long sourceId) {
        return acquire(sourceId);
    }

    public static Lease forTransaction(long sourceId) {
        Lease lease = acquire(sourceId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return lease;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                lease.close();
            }
        });
        return () -> {
        }; // The transaction completion callback owns this lease.
    }
}
