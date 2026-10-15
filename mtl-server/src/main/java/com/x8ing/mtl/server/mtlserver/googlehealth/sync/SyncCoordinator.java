package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHCycleStatistics;
import com.x8ing.mtl.server.mtlserver.utils.ThreadFactories;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Slf4j
public class SyncCoordinator {

    private static final int PROGRESS_MILESTONE_PERCENT = 25;
    private static final int LAST_PROGRESS_MILESTONE_PERCENT = 75;

    private final SyncService syncService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            ThreadFactories.namedDaemon("google-health-sync"));
    private final AtomicReference<JobRuntime> active = new AtomicReference<>();
    private final AtomicReference<JobSnapshot> latest = new AtomicReference<>(JobSnapshot.idle());
    private volatile UUID lastCompletedJobId;
    private final ReentrantReadWriteLock requestLock = new ReentrantReadWriteLock();
    private final Object jobQueueLock = new Object();
    private volatile boolean shuttingDown;

    public SyncCoordinator(SyncService syncService) {
        this.syncService = syncService;
    }

    public JobRequest request(JobKind kind) {
        return request(kind, null);
    }

    public JobRequest request(JobKind kind, Instant deadline) {
        requestLock.readLock().lock();
        try {
            JobRuntime candidate;
            synchronized (jobQueueLock) {
                if (shuttingDown) throw new IllegalStateException("Google Health sync coordinator is shutting down");
                JobRuntime current = active.get();
                if (current != null) {
                    log.info("Google Health busy tick skipped: requestedKind={}, activeJobId={}", kind, current.id);
                    return new JobRequest(current.snapshot(), true);
                }
                candidate = new JobRuntime(UUID.randomUUID(), kind);
                candidate.deadline = deadline;
                active.set(candidate);
            }
            latest.set(candidate.snapshot());
            log.info("Google Health sync job queued: jobId={}, kind={}", candidate.id, candidate.kind);
            executor.submit(() -> run(candidate));
            return new JobRequest(candidate.snapshot(), false);
        } finally {
            requestLock.readLock().unlock();
        }
    }

    public JobSnapshot status() {
        JobRuntime current = active.get();
        return current == null ? latest.get() : current.snapshot();
    }

    public UUID lastCompletedJobId() {
        return lastCompletedJobId;
    }

    public boolean hasActiveJob() {
        // Includes the terminal-to-follow-up handoff. Readers must not mistake
        // that brief transition for the end of a history continuation chain.
        synchronized (jobQueueLock) {
            return active.get() != null;
        }
    }

    public boolean runExclusivelyAfterCancelling(Duration timeout, Runnable action) {
        requestLock.writeLock().lock();
        try {
            if (!cancelActiveAndAwait(timeout)) {
                return false;
            }
            action.run();
            return true;
        } finally {
            requestLock.writeLock().unlock();
        }
    }

    public void cancelActive() {
        JobRuntime current = active.get();
        if (current == null) {
            return;
        }
        if (!current.cancelled) {
            log.info("Cancelling Google Health sync job: jobId={}, kind={}, state={}",
                    current.id, current.kind, current.state.get());
        }
        current.cancelled = true;
        current.publish();
        Thread runner = current.runner;
        if (runner != null) {
            runner.interrupt();
        }
    }

    private boolean cancelActiveAndAwait(Duration timeout) {
        JobRuntime current = active.get();
        if (current == null) {
            return true;
        }
        cancelActive();
        try {
            return current.completed.await(Math.max(0L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for Google Health sync job {} to stop", current.id, exception);
            return false;
        }
    }

    private void run(JobRuntime job) {
        job.runner = Thread.currentThread();
        job.state.set(JobState.RUNNING);
        job.startedAt = Instant.now();
        latest.set(job.snapshot());
        log.info("Google Health sync job started: jobId={}, kind={}", job.id, job.kind);
        try {
            if (job.cancelled) {
                job.state.set(JobState.CANCELLED);
                return;
            }
            SyncService.SyncResult result = syncService.run(job.kind, job.progress());
            if (job.cancelled) {
                job.state.set(JobState.CANCELLED);
                job.error = null;
            } else {
                job.state.set(result.success() ? JobState.COMPLETED : JobState.COMPLETED_WITH_ERRORS);
                job.statistics = result.statistics();
                job.error = result.errors().isEmpty() ? null : String.join("; ", result.errors());
            }
        } catch (RuntimeException exception) {
            if (job.cancelled) {
                job.state.set(JobState.CANCELLED);
                job.error = null;
                log.info("Google Health sync job stopped after cancellation: jobId={}, kind={}, "
                         + "completedUnits={}, totalUnits={}, errorType={}, error={}",
                        job.id, job.kind, job.completedUnits, job.totalUnits,
                        exception.getClass().getSimpleName(), safeError(exception));
                log.debug("Google Health sync cancellation exception", exception);
            } else {
                job.state.set(JobState.FAILED);
                job.error = safeError(exception);
                log.error("Google Health sync job failed: jobId={}, kind={}, completedUnits={}, totalUnits={}, error={}",
                        job.id, job.kind, job.completedUnits, job.totalUnits, job.error, exception);
            }
        } finally {
            job.completedAt = Instant.now();
            lastCompletedJobId = job.id;
            latest.set(job.snapshot());
            long durationMillis = job.startedAt == null
                    ? 0L
                    : Duration.between(job.startedAt, job.completedAt).toMillis();
            log.info("Google Health sync job finished: jobId={}, kind={}, state={}, durationMs={}, "
                     + "completedUnits={}, totalUnits={}, successfulWindows={}, failedUnits={}, skippedUnits={}, "
                     + "imported={}, deleted={}, errorPresent={}",
                    job.id, job.kind, job.state.get(), durationMillis,
                    job.completedUnits, job.totalUnits, job.successfulWindows, job.failedUnits, job.skippedUnits,
                    job.totalImported, job.totalDeleted, job.error != null);
            job.runner = null;
            synchronized (jobQueueLock) {
                active.compareAndSet(job, null);
            }
            job.completed.countDown();
        }
    }

    @PreDestroy
    @EventListener(ContextClosedEvent.class)
    public void shutdown() {
        synchronized (jobQueueLock) {
            if (shuttingDown) return;
            shuttingDown = true;
        }
        cancelActive();
        executor.shutdownNow();
        log.info("Google Health sync coordinator stopped");
    }

    private static String safeError(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public enum JobKind {
        REGULAR,
        DAILY_RECONCILIATION,
        FULL_RECONCILIATION
    }

    public enum JobState {
        IDLE,
        QUEUED,
        RUNNING,
        COMPLETED,
        COMPLETED_WITH_ERRORS,
        CANCELLED,
        FAILED
    }

    public record JobRequest(JobSnapshot job, boolean joinedActiveJob) {
    }

    public record JobSnapshot(
            UUID id,
            @Schema(description = "Identifies one budgeted cycle across recent and historical lanes.") UUID syncId,
            JobKind kind,
            JobState state,
            Instant requestedAt,
            Instant startedAt,
            Instant completedAt,
            long totalUnits,
            long completedUnits,
            long recordsProcessed,
            long currentWindowImported,
            String currentDataType,
            String currentRepresentation,
            Instant currentWindowStart,
            Instant currentWindowEnd,
            String error,
            GHCycleStatistics statistics
    ) {
        public JobSnapshot(UUID id, UUID syncId, JobKind kind, JobState state, Instant requestedAt, Instant startedAt, Instant completedAt,
                           long totalUnits, long completedUnits, long recordsProcessed, long currentWindowImported, String currentDataType,
                           String currentRepresentation, Instant currentWindowStart, Instant currentWindowEnd, String error) {
            this(id, syncId, kind, state, requestedAt, startedAt, completedAt, totalUnits, completedUnits, recordsProcessed, currentWindowImported,
                    currentDataType, currentRepresentation, currentWindowStart, currentWindowEnd, error, null);
        }

        public static JobSnapshot idle() {
            return new JobSnapshot(null, null, null, JobState.IDLE, null, null, null,
                    0L, 0L, 0L, 0L, null, null, null, null, null);
        }
    }

    public interface Progress {
        void setTotalUnits(long units);

        void addTotalUnits(long units);

        void startUnit(String dataType, String representation, Instant start, Instant end);

        /**
         * Restore committed window progress without counting work from an earlier attempt.
         */
        default void restoreWindowProgress(long imported) {
        }

        default void observeStatistics(GHCycleStatistics statistics) {
        }

        default Instant cycleDeadline() {
            return null;
        }

        void pageCompleted(long imported);

        void completeUnit();

        default void completeUnit(long imported, long deleted) {
            completeUnit();
        }

        void failUnit(String error);

        void unitSkipped(String dataType, String reason);

        boolean isCancelled();
    }

    private final class JobRuntime {
        private final UUID id;
        private final UUID syncId;
        private final JobKind kind;
        private final Instant requestedAt = Instant.now();
        private final AtomicReference<JobState> state = new AtomicReference<>(JobState.QUEUED);
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile Thread runner;
        private volatile Instant deadline;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile long totalUnits;
        private volatile long completedUnits;
        private volatile long successfulWindows;
        private volatile long failedUnits;
        private volatile long skippedUnits;
        private volatile long totalImported;
        private volatile long totalDeleted;
        private volatile long recordsProcessed;
        private volatile long currentWindowImported;
        private volatile String currentDataType;
        private volatile String currentRepresentation;
        private volatile Instant currentWindowStart;
        private volatile Instant currentWindowEnd;
        private volatile String error;
        private volatile GHCycleStatistics statistics;
        private volatile boolean cancelled;
        private volatile int nextMilestonePercent = PROGRESS_MILESTONE_PERCENT;

        private JobRuntime(UUID id, JobKind kind) {
            this(id, kind, id);
        }

        private JobRuntime(UUID id, JobKind kind, UUID syncId) {
            this.id = id;
            this.syncId = syncId;
            this.kind = kind;
        }

        private Progress progress() {
            return new Progress() {
                @Override
                public void setTotalUnits(long units) {
                    totalUnits = Math.max(0L, units);
                    publish();
                }

                @Override
                public void addTotalUnits(long units) {
                    totalUnits += Math.max(0L, units);
                    publish();
                }

                @Override
                public void startUnit(String dataType, String representation, Instant start, Instant end) {
                    currentDataType = dataType;
                    currentRepresentation = representation;
                    currentWindowStart = start;
                    currentWindowEnd = end;
                    currentWindowImported = 0L;
                    error = null;
                    publish();
                }

                @Override
                public void restoreWindowProgress(long imported) {
                    currentWindowImported = Math.max(0L, imported);
                    publish();
                }

                @Override
                public void pageCompleted(long imported) {
                    recordsProcessed += Math.max(0L, imported - currentWindowImported);
                    currentWindowImported = imported;
                    publish();
                }

                @Override
                public void completeUnit() {
                    completeUnit(0L, 0L);
                }

                @Override
                public void completeUnit(long imported, long deleted) {
                    recordsProcessed += Math.max(0L, imported - currentWindowImported);
                    successfulWindows++;
                    totalImported += Math.max(0L, imported);
                    totalDeleted += Math.max(0L, deleted);
                    completedUnits++;
                    publish();
                    logMilestoneIfReady();
                }

                @Override
                public void failUnit(String safeError) {
                    failedUnits++;
                    completedUnits++;
                    error = safeError;
                    publish();
                    logMilestoneIfReady();
                }

                @Override
                public void unitSkipped(String dataType, String reason) {
                    currentDataType = dataType;
                    currentRepresentation = null;
                    currentWindowStart = null;
                    currentWindowEnd = null;
                    currentWindowImported = 0L;
                    error = reason;
                    skippedUnits++;
                    completedUnits++;
                    publish();
                    logMilestoneIfReady();
                }

                @Override
                public void observeStatistics(GHCycleStatistics value) {
                    statistics = value;
                    publish();
                }

                @Override
                public Instant cycleDeadline() {
                    return deadline;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled || Thread.currentThread().isInterrupted();
                }
            };
        }

        private void publish() {
            latest.set(snapshot());
        }

        private void logMilestoneIfReady() {
            if (totalUnits <= 0L || nextMilestonePercent > LAST_PROGRESS_MILESTONE_PERCENT) {
                return;
            }
            int completedPercent = (int) Math.min(100L, completedUnits * 100L / totalUnits);
            if (completedPercent < nextMilestonePercent) {
                return;
            }
            log.info("Google Health sync milestone reached: jobId={}, kind={}, progressPercent={}, "
                     + "completedUnits={}, totalUnits={}, successfulWindows={}, failedUnits={}, skippedUnits={}, "
                     + "imported={}, deleted={}, currentDataType={}, currentRepresentation={}",
                    id, kind, nextMilestonePercent, completedUnits, totalUnits,
                    successfulWindows, failedUnits, skippedUnits, totalImported, totalDeleted,
                    currentDataType, currentRepresentation);
            nextMilestonePercent += PROGRESS_MILESTONE_PERCENT;
        }

        private JobSnapshot snapshot() {
            return new JobSnapshot(id, syncId, kind, state.get(), requestedAt, startedAt, completedAt,
                    totalUnits, completedUnits, recordsProcessed, currentWindowImported, currentDataType,
                    currentRepresentation, currentWindowStart, currentWindowEnd, error, statistics);
        }
    }
}
