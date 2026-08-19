package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageMagickProcessLimiterTest {

    private static final long TEST_TIMEOUT_SECONDS = 5L;

    @Test
    void limitsConcurrentOperationsToTheConfiguredProcessCount() throws Exception {
        ImageMagickProperties properties = new ImageMagickProperties();
        properties.setMaxConcurrentProcesses(2);
        ImageMagickProcessLimiter limiter = new ImageMagickProcessLimiter(properties);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch firstOperationsStarted = new CountDownLatch(2);
        CountDownLatch thirdOperationStarted = new CountDownLatch(1);
        CountDownLatch releaseOperations = new CountDownLatch(1);
        AtomicInteger activeOperations = new AtomicInteger();
        AtomicInteger maximumActiveOperations = new AtomicInteger();

        try {
            Future<?> first = submitBlockingOperation(
                    executor, limiter, firstOperationsStarted, releaseOperations, activeOperations, maximumActiveOperations);
            Future<?> second = submitBlockingOperation(
                    executor, limiter, firstOperationsStarted, releaseOperations, activeOperations, maximumActiveOperations);
            assertThat(firstOperationsStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> third = submitBlockingOperation(
                    executor, limiter, thirdOperationStarted, releaseOperations, activeOperations, maximumActiveOperations);
            assertThat(thirdOperationStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();

            releaseOperations.countDown();
            first.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            third.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(maximumActiveOperations).hasValue(2);
        } finally {
            releaseOperations.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void releasesTheProcessSlotWhenAnOperationFails() throws Exception {
        ImageMagickProperties properties = new ImageMagickProperties();
        properties.setMaxConcurrentProcesses(1);
        ImageMagickProcessLimiter limiter = new ImageMagickProcessLimiter(properties);

        assertThatThrownBy(() -> limiter.execute(() -> {
            throw new IOException("synthetic failure");
        })).isInstanceOf(IOException.class);

        AtomicInteger completed = new AtomicInteger();
        limiter.execute(completed::incrementAndGet);
        assertThat(completed).hasValue(1);
    }

    @Test
    void rejectsANonPositiveProcessLimit() {
        ImageMagickProperties properties = new ImageMagickProperties();
        properties.setMaxConcurrentProcesses(0);

        assertThatIllegalStateException()
                .isThrownBy(() -> new ImageMagickProcessLimiter(properties))
                .withMessageContaining("max-concurrent-processes");
    }

    private static Future<?> submitBlockingOperation(
            ExecutorService executor,
            ImageMagickProcessLimiter limiter,
            CountDownLatch started,
            CountDownLatch release,
            AtomicInteger active,
            AtomicInteger maximumActive) {
        return executor.submit(() -> {
            limiter.execute(() -> {
                int activeCount = active.incrementAndGet();
                maximumActive.accumulateAndGet(activeCount, Math::max);
                started.countDown();
                try {
                    await(release);
                } finally {
                    active.decrementAndGet();
                }
            });
            return null;
        });
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for the synthetic operation release");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting in the synthetic operation", e);
        }
    }
}
