package com.x8ing.mtl.server.mtlserver.indexer.event;

/**
 * Completion callback invoked by domain consumers after they processed a file.
 * It is intentionally ID-only to keep boundaries clean and avoid passing detached entities/transactions.
 */
public interface OnCompletion {

    /**
     * Signal that the consumer has actually started processing the file.
     * This flips the status from SCHEDULED to PROCESSING.
     * Call this at the very beginning of the observer callback, before doing any heavy work.
     */
    default void started(long fileId) {
        // no-op default for backward compatibility
    }

    void success(long fileId);

    void failed(long fileId, String reason);
}
