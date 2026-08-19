package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Keeps concurrent HTTP requests from starting an unbounded number of ImageMagick processes. */
@Component
public class ImageMagickProcessLimiter {

    private final Semaphore processPermits;

    public ImageMagickProcessLimiter(ImageMagickProperties properties) {
        properties.validate();
        processPermits = new Semaphore(properties.getMaxConcurrentProcesses(), true);
    }

    void execute(IoOperation operation) throws IOException {
        boolean acquired = false;
        try {
            processPermits.acquire();
            acquired = true;
            operation.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for an ImageMagick process slot", e);
        } finally {
            if (acquired) processPermits.release();
        }
    }

    @FunctionalInterface
    interface IoOperation {
        void run() throws IOException;
    }
}
