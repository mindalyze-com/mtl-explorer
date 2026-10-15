package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class SyncCoordinatorTest {

    private SyncCoordinator coordinator;

    @Test
    void contextCloseCancelsActiveSyncAndRejectsNewWorkBeforeBeanDestruction() throws Exception {
        var service = mock(SyncService.class);
        var active = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        when(service.run(any(), any())).thenAnswer(call -> {
            SyncCoordinator.Progress progress = call.getArgument(1);
            active.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                assertThat(progress.isCancelled()).isTrue();
                cancelled.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Synthetic cancellation", exception);
            }
            throw new AssertionError("Sync must be cancelled");
        });
        coordinator = new SyncCoordinator(service);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SyncCoordinator.class, () -> coordinator);
            context.refresh();
            coordinator.request(SyncCoordinator.JobKind.REGULAR);
            assertThat(active.await(5, TimeUnit.SECONDS)).isTrue();
            context.publishEvent(new ContextClosedEvent(context));
            assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThatThrownBy(() -> coordinator.request(SyncCoordinator.JobKind.REGULAR))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("shutting down");
        }
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void manualRequestJoinsActiveJobAndExclusiveActionWaitsForCancellation() throws Exception {
        SyncService syncService = mock(SyncService.class);
        CountDownLatch started = new CountDownLatch(1);
        when(syncService.run(any(), any())).thenAnswer(invocation -> {
            SyncCoordinator.Progress progress = invocation.getArgument(1);
            started.countDown();
            while (!progress.isCancelled()) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new IllegalStateException("cancelled");
        });
        coordinator = new SyncCoordinator(syncService);

        SyncCoordinator.JobRequest first = coordinator.request(
                SyncCoordinator.JobKind.REGULAR);
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        SyncCoordinator.JobRequest joined = coordinator.request(
                SyncCoordinator.JobKind.FULL_RECONCILIATION);
        AtomicBoolean actionRan = new AtomicBoolean();

        boolean completed = coordinator.runExclusivelyAfterCancelling(
                Duration.ofSeconds(2), () -> actionRan.set(true));

        assertThat(joined.joinedActiveJob()).isTrue();
        assertThat(joined.job().id()).isEqualTo(first.job().id());
        assertThat(completed).isTrue();
        assertThat(actionRan).isTrue();
        assertThat(coordinator.status().state()).isEqualTo(SyncCoordinator.JobState.CANCELLED);
    }


    @Test
    void logsBoundedProgressMilestonesAndFinalCounts(CapturedOutput output) throws Exception {
        SyncService syncService = mock(SyncService.class);
        when(syncService.run(any(), any())).thenAnswer(invocation -> {
            SyncCoordinator.Progress progress = invocation.getArgument(1);
            progress.setTotalUnits(4L);
            progress.startUnit(
                    "heart-rate", "HEART_RATE_SAMPLE", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            progress.completeUnit(10L, 2L);
            progress.unitSkipped("sleep", "No cold-history window is ready");
            progress.startUnit(
                    "exercise", "RAW", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            progress.failUnit("temporary failure");
            progress.startUnit(
                    "steps", "DAILY_ROLLUP", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            progress.completeUnit(5L, 0L);
            return new SyncService.SyncResult(false, List.of("temporary failure"));
        });
        coordinator = new SyncCoordinator(syncService);

        coordinator.request(SyncCoordinator.JobKind.REGULAR);
        awaitTerminalState();

        assertThat(output)
                .contains("progressPercent=25")
                .contains("progressPercent=50")
                .contains("progressPercent=75")
                .contains("successfulWindows=2")
                .contains("failedUnits=1")
                .contains("skippedUnits=1")
                .contains("imported=15")
                .contains("deleted=2");
    }


    @Test
    void restoredCheckpointsDoNotCountPreviouslyCommittedRecords() throws Exception {
        SyncService service = mock(SyncService.class);
        when(service.run(any(), any())).thenAnswer(call -> {
            SyncCoordinator.Progress progress = call.getArgument(1);
            progress.startUnit("sleep", "RAW", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            progress.pageCompleted(20);
            progress.failUnit("Synthetic failure");
            progress.startUnit("sleep", "RAW", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            progress.restoreWindowProgress(20);
            assertThat(coordinator.status().recordsProcessed()).isEqualTo(20);
            assertThat(coordinator.status().currentWindowImported()).isEqualTo(20);
            progress.pageCompleted(40);
            progress.completeUnit(40, 0);
            assertThat(coordinator.status().recordsProcessed()).isEqualTo(40);
            progress.startUnit("exercise", "RAW", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            progress.restoreWindowProgress(1_000);
            progress.pageCompleted(1_000);
            progress.completeUnit(1_000, 0);
            assertThat(coordinator.status().recordsProcessed()).isEqualTo(40);
            progress.startUnit("heart-rate", "HEART_RATE_SAMPLE", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            progress.restoreWindowProgress(5_000);
            progress.restoreWindowProgress(0);
            progress.pageCompleted(5);
            progress.completeUnit(5, 0);
            return new SyncService.SyncResult(true, List.of());
        });
        coordinator = new SyncCoordinator(service);
        coordinator.request(SyncCoordinator.JobKind.REGULAR);
        awaitTerminalState();
        assertThat(coordinator.status().recordsProcessed()).isEqualTo(45);
    }

    @Test
    void independentSyncStartsANewProcessedCountSeries() {
        SyncService service = mock(SyncService.class);
        when(service.run(any(), any())).thenAnswer(call -> {
            SyncCoordinator.Progress progress = call.getArgument(1);
            progress.startUnit("heart-rate", "HEART_RATE_SAMPLE", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            progress.completeUnit(1_000, 0);
            return new SyncService.SyncResult(true, List.of(), false);
        });
        coordinator = new SyncCoordinator(service);
        SyncCoordinator.JobRequest first = coordinator.request(SyncCoordinator.JobKind.REGULAR);
        await().atMost(Duration.ofSeconds(2)).until(() -> !coordinator.hasActiveJob());
        SyncCoordinator.JobRequest second = coordinator.request(SyncCoordinator.JobKind.REGULAR);
        await().atMost(Duration.ofSeconds(2)).until(() -> !coordinator.hasActiveJob());

        assertThat(second.job().syncId()).isEqualTo(second.job().id()).isNotEqualTo(first.job().syncId());
        assertThat(coordinator.status().recordsProcessed()).isEqualTo(1_000);
    }


    @Test
    void failedPassWithoutHealthyProgressWaitsForScheduledRetry() throws Exception {
        SyncService syncService = mock(SyncService.class);
        when(syncService.run(any(), any()))
                .thenReturn(new SyncService.SyncResult(false, List.of("temporary failure"), false));
        coordinator = new SyncCoordinator(syncService);

        coordinator.request(SyncCoordinator.JobKind.REGULAR);
        awaitTerminalState();

        verify(syncService, after(100).times(1)).run(any(), any());
        assertThat(coordinator.status().state()).isEqualTo(SyncCoordinator.JobState.COMPLETED_WITH_ERRORS);
    }


    @Test
    void exclusiveCancellationDoesNotRestartAutomaticHistoryWork() throws Exception {
        SyncService syncService = mock(SyncService.class);
        CountDownLatch started = new CountDownLatch(1);
        when(syncService.run(any(), any())).thenAnswer(invocation -> {
            SyncCoordinator.Progress progress = invocation.getArgument(1);
            started.countDown();
            while (!progress.isCancelled()) Thread.onSpinWait();
            return new SyncService.SyncResult(true, List.of(), true);
        });
        coordinator = new SyncCoordinator(syncService);
        coordinator.request(SyncCoordinator.JobKind.REGULAR);
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(coordinator.runExclusivelyAfterCancelling(Duration.ofSeconds(2), () -> {
        })).isTrue();

        verify(syncService, after(100).times(1)).run(any(), any());
        assertThat(coordinator.status().state()).isEqualTo(SyncCoordinator.JobState.CANCELLED);
    }

    @Test
    void busyRequestsDoNotQueueAReplay() throws Exception {
        SyncService service = mock(SyncService.class);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        when(service.run(any(), any())).thenAnswer(call -> {
            started.countDown();
            release.await();
            return new SyncService.SyncResult(true, List.of(), true);
        });
        coordinator = new SyncCoordinator(service);
        var first = coordinator.request(SyncCoordinator.JobKind.REGULAR);
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(coordinator.request(SyncCoordinator.JobKind.REGULAR).joinedActiveJob()).isTrue();
        release.countDown();
        awaitTerminalState();
        verify(service, after(100).times(1)).run(any(), any());
        assertThat(coordinator.status().syncId()).isEqualTo(first.job().syncId());
    }

    private void awaitTerminalState() throws InterruptedException {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            SyncCoordinator.JobState state = coordinator.status().state();
            if (state != SyncCoordinator.JobState.QUEUED && state != SyncCoordinator.JobState.RUNNING) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Google Health sync job did not reach a terminal state");
    }
}
