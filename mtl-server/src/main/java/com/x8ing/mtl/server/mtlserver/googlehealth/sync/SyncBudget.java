package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import java.time.Duration;
import java.util.concurrent.CancellationException;

/**
 * Monotonic scheduling deadline for admitting requests, retries and other work on this worker.
 * A completed batch commits under its separate database timeout.
 */
public final class SyncBudget implements AutoCloseable {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private final Long previous;

    public SyncBudget(long deadline) {
        previous = DEADLINE.get();
        DEADLINE.set(previous == null ? deadline : Math.min(previous, deadline));
    }

    public static long remainingNanos() {
        Long deadline = DEADLINE.get();
        return deadline == null ? Long.MAX_VALUE : Math.max(0, deadline - System.nanoTime());
    }

    public static boolean has(Duration required) {
        return remainingNanos() > required.toNanos();
    }

    public static void require(Duration required) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Sync cancelled");
        if (!has(required)) throw new Yield();
    }

    public static final class Yield extends RuntimeException {
    }

    @Override
    public void close() {
        if (previous == null) DEADLINE.remove();
        else DEADLINE.set(previous);
    }
}
