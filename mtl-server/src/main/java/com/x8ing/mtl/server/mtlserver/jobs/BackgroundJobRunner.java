package com.x8ing.mtl.server.mtlserver.jobs;

import lombok.extern.slf4j.Slf4j;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJob;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJobSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One bounded batch per scheduler turn; periodic checks recover idle or failed jobs.
 */
@Component
@Slf4j
public class BackgroundJobRunner {
    private static final Set<String> NON_RETRYABLE_SQL_STATE_CLASSES = Set.of("22", "23", "28", "3D", "42");
    private static final String CONNECTION_SQL_STATE_CLASS = "08";
    private static final String TOO_MANY_CONNECTIONS_SQL_STATE = "53300";
    private static final String DATABASE_SHUTDOWN_SQL_STATE = "57P01";
    private final TaskScheduler scheduler;
    private final ShutdownSignal shutdownSignal;
    private final Set<Class<?>> running = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Runnable> batchStart = new ThreadLocal<>();
    private final Set<String> executing = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> nextChecks = new ConcurrentHashMap<>();
    private final Map<String, Instant> continuations = new ConcurrentHashMap<>();
    private final ProcessingJobSupport reporting;

    public BackgroundJobRunner(@Lazy TaskScheduler scheduler, ShutdownSignal shutdownSignal) {
        this(scheduler, shutdownSignal, null);
    }

    @Autowired
    public BackgroundJobRunner(@Lazy TaskScheduler scheduler, ShutdownSignal shutdownSignal, ProcessingJobSupport reporting) {
        this.scheduler = scheduler;
        this.shutdownSignal = shutdownSignal;
        this.reporting = reporting;
    }

    public void registerSchedule(ProcessingJob job) {
        nextChecks.putIfAbsent(job.jobId(), Instant.now().plus(job.initialDelay()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerInitialSchedules(ApplicationReadyEvent event) {
        event.getApplicationContext().getBeansOfType(ProcessingJob.class).values().forEach(this::registerSchedule);
    }

    public boolean isExecuting(String job) { return executing.contains(job); }

    public Instant nextRunAt(String job) {
        Instant continuation = continuations.get(job);
        return continuation != null ? continuation : nextChecks.get(job);
    }

    public ProcessingDetails.BatchOutcome executing(String job, Supplier<ProcessingDetails.BatchOutcome> work) {
        Runnable previous = batchStart.get();
        batchStart.set(() -> executing.add(job));
        try { return work.get(); }
        finally {
            executing.remove(job);
            if (previous == null) batchStart.remove(); else batchStart.set(previous);
        }
    }

    /** Called by the domain worker after gates/selection, immediately before actual batch work. */
    public static void batchStarted() {
        Runnable signal = batchStart.get();
        if (signal != null) signal.run();
    }

    public void runReported(ProcessingJob job, Supplier<ProcessingDetails.BatchOutcome> work) {
        registerSchedule(job);
        try {
            run(job.getClass(), () -> {
                continuations.remove(job.jobId());
                var outcome = reporting == null
                        ? executing(job.jobId(), work)
                        : reporting.execute(job, this, work);
                if (outcome.hasMore()) continuations.put(job.jobId(), Instant.now());
                return outcome.hasMore();
            });
        } finally {
            nextChecks.put(job.jobId(), Instant.now().plus(job.checkInterval()));
        }
    }

    public void run(Class<?> jobType, BooleanSupplier batchHasMore) {
        if (shutdownSignal.isStopRequested() || !running.add(jobType)) return;
        runClaimed(jobType, batchHasMore);
    }

    /**
     * A temporary connection problem leaves durable work pending; invalid data/configuration does not.
     */
    public static boolean isTemporaryConnectionFailure(Throwable failure) {
        if (failure == null) return false;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var causes = new ArrayDeque<Throwable>();
        causes.add(failure);
        boolean temporary = false;
        while (!causes.isEmpty()) {
            Throwable cause = causes.removeFirst();
            if (!visited.add(cause)) continue;
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                // Hikari can wrap its last connection error in a transient pool timeout.
                if (cause instanceof SQLNonTransientConnectionException
                    || state != null && state.length() >= 2 && NON_RETRYABLE_SQL_STATE_CLASSES.contains(state.substring(0, 2))) return false;
                temporary |= cause instanceof SQLTransientConnectionException || cause instanceof SQLRecoverableException
                             || state != null && (state.startsWith(CONNECTION_SQL_STATE_CLASS)
                                                  || state.equals(TOO_MANY_CONNECTIONS_SQL_STATE) || state.equals(DATABASE_SHUTDOWN_SQL_STATE));
                if (sql.getNextException() != null) causes.addLast(sql.getNextException());
            }
            temporary |= cause instanceof TransientDataAccessResourceException;
            if (cause.getCause() != null) causes.addLast(cause.getCause());
        }
        return temporary;
    }

    private void runClaimed(Class<?> jobType, BooleanSupplier batchHasMore) {
        boolean queued = false;
        try {
            // The callback returns after its batch transaction has committed.
            if (!shutdownSignal.isStopRequested() && batchHasMore.getAsBoolean()
                && !shutdownSignal.isStopRequested()) {
                scheduler.schedule(() -> runClaimed(jobType, batchHasMore), Instant.now());
                queued = true;
            }
        } catch (RuntimeException failure) {
            if (org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(failure) instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Background batch stopped: job={}, type={}, error={}",
                    jobType.getSimpleName(), failure.getClass().getSimpleName(), failure.getMessage());
            log.debug("Background batch failure", failure);
        } finally {
            // Keep ownership while a continuation is queued; another periodic tick cannot add a second chain.
            if (!queued) running.remove(jobType);
        }
    }
}
