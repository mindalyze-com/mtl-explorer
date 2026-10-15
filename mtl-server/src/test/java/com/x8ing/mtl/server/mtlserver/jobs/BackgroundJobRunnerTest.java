package com.x8ing.mtl.server.mtlserver.jobs;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BackgroundJobRunnerTest {
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ShutdownSignal signal = new ShutdownSignal();
    private final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
    private final BackgroundJobRunner runner = new BackgroundJobRunner(scheduler, signal);

    BackgroundJobRunnerTest() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(0));
            return mock(ScheduledFuture.class);
        });
    }

    @Test
    void idleBatchHasNoContinuationAndTheNextPeriodicCheckCanRun() {
        var calls = new AtomicInteger();
        BooleanSupplier batch = () -> { calls.incrementAndGet(); return false; };
        runner.run(getClass(), batch);
        runner.run(getClass(), batch);
        assertThat(calls).hasValue(2);
        verifyNoInteractions(scheduler);
    }

    @Test
    void queuedContinuationRetainsOwnershipAcrossPeriodicTicks() {
        var remaining = new AtomicInteger(3);
        BooleanSupplier batch = () -> remaining.decrementAndGet() > 0;
        runner.run(getClass(), batch);
        runner.run(getClass(), batch);
        assertThat(remaining).hasValue(2);
        assertThat(callbacks).hasSize(1);
        callbacks.remove().run();
        runner.run(getClass(), batch);
        assertThat(remaining).hasValue(1);
        assertThat(callbacks).hasSize(1);
        callbacks.remove().run();
        assertThat(remaining).hasValue(0);
        assertThat(callbacks).isEmpty();
    }

    @Test
    void manyContinuationsKeepOneCallbackQueuedAndDoNotRecurse() {
        var remaining = new AtomicInteger(10_000);
        runner.run(getClass(), () -> remaining.decrementAndGet() > 0);
        while (!callbacks.isEmpty()) {
            assertThat(callbacks).hasSize(1);
            callbacks.remove().run();
        }
        assertThat(remaining).hasValue(0);
    }

    @Test
    void failedBatchWaitsForTheExistingPeriodicRetry() {
        runner.run(getClass(), () -> { throw new IllegalStateException("synthetic database outage"); });
        assertThat(callbacks).isEmpty();
        var calls = new AtomicInteger();
        runner.run(getClass(), () -> { calls.incrementAndGet(); return false; });
        assertThat(calls).hasValue(1);
    }

    @Test
    void shutdownMakesQueuedCallbacksHarmlessWithoutInterruptingTransactions() {
        var calls = new AtomicInteger();
        runner.run(getClass(), () -> { calls.incrementAndGet(); return true; });
        signal.onContextClosed();
        callbacks.remove().run();
        runner.run(getClass(), () -> { calls.incrementAndGet(); return true; });
        assertThat(calls).hasValue(1);
        assertThat(callbacks).isEmpty();
    }

    @Test
    void fatalErrorsReleaseOwnershipThroughTheSameFinallyBlock() {
        assertThatThrownBy(() -> runner.run(getClass(), () -> { throw new AssertionError("synthetic fatal error"); }))
                .isInstanceOf(AssertionError.class);
        var calls = new AtomicInteger();
        runner.run(getClass(), () -> { calls.incrementAndGet(); return false; });
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectedContinuationDoesNotLeaveTheJobPermanentlyRunning() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenThrow(new java.util.concurrent.RejectedExecutionException("synthetic rejection"));
        runner.run(getClass(), () -> true);
        var calls = new AtomicInteger();
        runner.run(getClass(), () -> { calls.incrementAndGet(); return false; });
        assertThat(calls).hasValue(1);
    }

    @Test
    void concurrentTriggersDoNotRunTheSameBatchTogether() throws Exception {
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var calls = new AtomicInteger();
        BooleanSupplier batch = () -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
            return false;
        };
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> runner.run(getClass(), batch));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                threads.submit(() -> runner.run(getClass(), batch)).get(5, TimeUnit.SECONDS);
                assertThat(calls).hasValue(1);
            } finally {
                finish.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
        }
    }
}
