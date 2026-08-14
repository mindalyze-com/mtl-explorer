package com.x8ing.mtl.server.mtlserver.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadFactories {

    private ThreadFactories() {
    }

    public static ThreadFactory named(String prefix) {
        return named(prefix, false);
    }

    public static ThreadFactory namedDaemon(String prefix) {
        return named(prefix, true);
    }

    private static ThreadFactory named(String prefix, boolean daemon) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(daemon);
            return thread;
        };
    }
}
