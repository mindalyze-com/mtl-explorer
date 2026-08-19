package com.x8ing.mtl.server.mtlserver.jobs;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceIntensiveJobGuardTest {

    @Test
    void skipsOverlappingJobAndAllowsLaterRetry() throws Exception {
        ResourceIntensiveJobGuard guard = new ResourceIntensiveJobGuard();
        CountDownLatch firstJobStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstJob = new CountDownLatch(1);
        AtomicBoolean firstJobResult = new AtomicBoolean();

        Thread firstJob = new Thread(() -> firstJobResult.set(guard.runIfAvailable(
                ResourceIntensiveJobGuardTest.class,
                () -> {
                    firstJobStarted.countDown();
                    try {
                        releaseFirstJob.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                })));
        firstJob.start();

        assertThat(firstJobStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(guard.runIfAvailable(ResourceIntensiveJobGuardTest.class, () -> {
        })).isFalse();

        releaseFirstJob.countDown();
        firstJob.join(2_000);

        assertThat(firstJobResult.get()).isTrue();
        assertThat(guard.runIfAvailable(ResourceIntensiveJobGuardTest.class, () -> {
        })).isTrue();
    }
}
