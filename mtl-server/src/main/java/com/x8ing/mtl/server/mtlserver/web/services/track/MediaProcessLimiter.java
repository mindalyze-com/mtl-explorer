package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps high-memory ImageMagick, FFprobe, and FFmpeg child processes from overlapping. */
@Component
public class MediaProcessLimiter {

    private final Semaphore processPermits;
    private final Duration acquireTimeout;

    public MediaProcessLimiter(MediaProcessProperties properties) {
        properties.validate();
        processPermits = new Semaphore(properties.getMaxConcurrentProcesses(), true);
        acquireTimeout = properties.getAcquireTimeout();
    }

    Process start(ProcessBuilder processBuilder) throws IOException {
        Permit permit = acquire();
        try {
            Process process = processBuilder.start();
            process.onExit().whenComplete((ignored, error) -> permit.close());
            return process;
        } catch (IOException | RuntimeException e) {
            permit.close();
            throw e;
        }
    }

    Permit acquire() throws IOException {
        try {
            if (!processPermits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new MediaProcessBusyException(
                        "No media process slot became available within " + acquireTimeout);
            }
            return new Permit(processPermits);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for a media process slot", e);
        }
    }

    static final class MediaProcessBusyException extends IOException {

        private MediaProcessBusyException(String message) {
            super(message);
        }
    }

    static final class Permit implements AutoCloseable {

        private final Semaphore processPermits;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(Semaphore processPermits) {
            this.processPermits = processPermits;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) processPermits.release();
        }
    }
}
