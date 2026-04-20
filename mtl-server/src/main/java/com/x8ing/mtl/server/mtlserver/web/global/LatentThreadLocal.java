package com.x8ing.mtl.server.mtlserver.web.global;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Supplier;

@Slf4j
public class LatentThreadLocal<T> {

    private final ThreadLocal<T> threadLocal = new ThreadLocal<>();

    private final Supplier<T> supplier;

    public LatentThreadLocal(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * From smart AI:
     * Therefore, each thread should only initialize the ThreadLocal once. However, the ForkJoinPool (common pool)
     * uses a work-stealing algorithm, where any worker thread can steal tasks from another thread's work
     * queue when idle, and it reuses threads.
     */
    public T get() {
        T value = threadLocal.get();

        if (value == null) {

            synchronized (threadLocal) {

                // Re-check the value from the threadLocal inside the synchronized block
                value = threadLocal.get();

                // even though no other thread should be able to get in here, let's play safe, and double check
                // within the synced block, if we are still not initialized.
                if (value == null) {
                    value = supplier.get();
                    log.info("Init LatentThreadLocal for thread=%s with id=%s threadObj=%s this=%s to value=%s".
                            formatted(Thread.currentThread().getName(), Thread.currentThread().getId(), Thread.currentThread().hashCode(), System.identityHashCode(this), value == null ? "null" : "not_null"));
                    threadLocal.set(value);
                }
            }
        }
        return value;
    }
}
