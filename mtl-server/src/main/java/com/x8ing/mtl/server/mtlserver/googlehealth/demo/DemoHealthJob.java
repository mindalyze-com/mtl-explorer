package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthMirrorCache;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.ImportPrerequisites;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeClassifierJob;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService.JobSummaryDto;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Completes missing ordinary Health records once per startup, in asynchronous batches.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoHealthJob implements ProcessingJob {
    private static final double NANOS_PER_MILLISECOND = 1_000_000;
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);
    private final DemoHealthAccess access;
    private final DemoHealthProperties properties;
    private final IndexerStatusService indexers;
    private final ResourceIntensiveJobGuard guard;
    private final DemoHealthRepository repository;
    private final DemoHealthWorker worker;
    private final GoogleHealthMirrorCache mirrorCache;
    private final DemoHealthShowcase showcase;
    private final GpsTrackRepository tracks;
    private final ShutdownSignal shutdownSignal;
    private final BackgroundJobRunner backgroundJobs;
    private volatile JobSummaryDto summary = new JobSummaryDto("demoHealth", "Synthetic demo health", 1, 1, 0, 0, 0);
    private boolean waiting;
    private boolean started;
    private volatile boolean checked;
    private volatile boolean finished;
    private long startedAt;
    private long completedDays;
    private volatile Instant nextCheck = Instant.MIN;

    public boolean enabled() {
        return access.enabled();
    }

    public JobSummaryDto summary() {
        return summary;
    }

    @Scheduled(fixedDelayString = "${mtl.demo-health.run-schedule:PT5S}", initialDelayString = "PT15S")
    public void scheduled() {
        backgroundJobs.runReported(this, this::reportedBatch);
    }

    public ProcessingDetails.BatchOutcome reportedBatch() {
        if (Instant.now().isBefore(nextCheck)) return new ProcessingDetails.BatchOutcome(false, List.of(), nextCheck);
        return ProcessingDetails.BatchOutcome.finished(runBatch());
    }

    boolean runBatch() {
        if (shutdownSignal.isStopRequested()) return false;
        if (!access.enabled() || finished || Instant.now().isBefore(nextCheck)) return false;
        if (!started) {
            started = true;
            startedAt = System.nanoTime();
            log.info("Synthetic demo health startup completion starting: selectedPercent={}, intervalSeconds={}, recentDays={}",
                    properties.getTrackPercentage(), properties.getSampleIntervalSeconds(), properties.getRecentDays());
        }
        if (indexers.isDemoIndexingBusy()) {
            waitForTracks("GPS and media indexing");
            return false;
        }
        return guard.runBatchIfAvailable(this::batch);
    }

    private void waitForTracks(String phase) {
        if (!waiting) log.info("Synthetic demo health waiting for {} to finish before checking stored Health records", phase);
        waiting = true;
        summary = new JobSummaryDto("demoHealth", "Synthetic demo health (waiting for track processing)", 1, 1, 0, 0, 0);
    }

    private boolean batch() {
        if (shutdownSignal.isStopRequested()) return false;
        BackgroundJobRunner.batchStarted();
        long batchStarted = System.nanoTime();
        try {
            if (showcase.prepare()) return false;
            if (shutdownSignal.isStopRequested()) return false;
            showcase.classify();
            if (shutdownSignal.isStopRequested()) return false;
            if (!trackPrerequisites().isEmpty()) {
                waitForTracks("track calculations");
                return false;
            }
            if (waiting) log.info("Synthetic demo health track processing finished; checking stored Health records");
            waiting = false;
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            LocalDate first = repository.firstDay(today.minusDays(properties.getRecentDays()));
            LocalDate last = today.minusDays(1);
            long total = ChronoUnit.DAYS.between(first, today);
            if (!checked) {
                long missing = repository.missingDayCount(first, last);
                log.info("Synthetic demo health startup check completed: from={}, through={}, totalDays={}, completeDays={}, incompleteDays={}",
                        first, last, total, total - missing, missing);
                summary = new JobSummaryDto(JOB_ID, "Google Health (synthetic)", total, missing, total - missing, 0,
                        total == 0 ? 100 : (int) ((total - missing) * 100 / total));
                checked = true;
            }
            var days = repository.missingDays(first, last);
            int completed = 0;
            if (!days.isEmpty()) {
                log.info("Synthetic demo health generation batch starting: incompleteDays={}", days.size());
                for (var date : days) {
                    if (shutdownSignal.isStopRequested()) break;
                    if (worker.day(date, today)) {
                        completed++;
                        completedDays++;
                        var current = summary;
                        long remaining = Math.max(0, current.pending() - 1);
                        summary = new JobSummaryDto(JOB_ID, "Google Health (synthetic)", total, remaining,
                                total - remaining, 0, total == 0 ? 100 : (int) ((total - remaining) * 100 / total));
                    }
                }
                if (completed > 0) mirrorCache.clear();
                log.info("Synthetic demo health generation batch completed: completedDays={}, stopped={}, durationMs={}", completed,
                        shutdownSignal.isStopRequested(),
                        (System.nanoTime() - batchStarted) / NANOS_PER_MILLISECOND);
            }
            if (shutdownSignal.isStopRequested()) return false;
            long pending = repository.missingDayCount(first, last);
            summary = new JobSummaryDto("demoHealth", "Synthetic demo health", total, pending, total - pending, 0,
                    total == 0 ? 100 : (int) ((total - pending) * 100 / total));
            if (pending == 0) {
                finished = true;
                log.info("Synthetic demo health startup completion finished: completedDays={}, alreadyCompleteDays={}, totalDays={}, durationMs={}",
                        completedDays, total - completedDays, total, (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND);
            }
            return pending > 0 && completed > 0;
        } catch (RuntimeException exception) {
            mirrorCache.clear();
            nextCheck = Instant.now().plus(RETRY_INTERVAL);
            long total = Math.max(summary.done() + 1, summary.total());
            summary = new JobSummaryDto("demoHealth", "Synthetic demo health (retrying)", total,
                    total - summary.done(), summary.done(), 0, Math.min(99, summary.progressPercent()));
            log.error("Synthetic demo health generation failed; missing ordinary records will be checked again in {}: type={}, message={}",
                    RETRY_INTERVAL, exception.getClass().getSimpleName(), exception.getMessage(), exception);
            return false;
        }
    }
    public static final String JOB_ID = "demoHealth";
    @Override public String jobId() { return JOB_ID; }
    @Override public int displayOrder() { return 60; }
    @Override public boolean reportingEnabled() { return enabled(); }
    @Override public boolean resourceIntensive() { return true; }
    @Value("${mtl.demo-health.run-schedule:PT5S}")
    private String scheduleInterval = "PT5S";
    @Override public Duration checkInterval() { return org.springframework.boot.convert.DurationStyle.detectAndParse(scheduleInterval); }
    @Override public Duration initialDelay() { return Duration.ofSeconds(15); }
    @Override public List<ProcessingDetails.Blocker> prerequisites(ProcessingContext context) {
        var blockers = new ArrayList<>(ImportPrerequisites.check(context, true));
        if (context.demoIndexingBusy() && blockers.isEmpty()) blockers.add(ProcessingDetails.Blocker.automatic(
                ProcessingDetails.Kind.INDEX, "GPS", "IMPORT_STARTING", ProcessingDetails.Scope.WHOLE_JOB));
        blockers.addAll(trackPrerequisites());
        return List.copyOf(blockers);
    }
    private List<ProcessingDetails.Blocker> trackPrerequisites() {
        var blockers = new ArrayList<ProcessingDetails.Blocker>();
        if (tracks.existsByTrackSourceAndDuplicateStatus(GpsTrack.TRACK_SOURCE.IMPORTED,
                GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)) {
            blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.JOB, DuplicateDetectorJob.JOB_ID,
                    "DUPLICATE_CHECKS_PENDING", ProcessingDetails.Scope.WHOLE_JOB));
        }
        if (tracks.countActivityTypePending() > 0) {
            blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.JOB, ActivityTypeClassifierJob.JOB_ID,
                    "ACTIVITY_PENDING", ProcessingDetails.Scope.WHOLE_JOB));
        }
        if (repository.trackCalculationPending()) {
            blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.JOB, EnergyRecalculationQueue.JOB_ID,
                    "ENERGY_PENDING", ProcessingDetails.Scope.WHOLE_JOB));
        }
        return List.copyOf(blockers);
    }
    @Override public List<ProcessingDetails.Dependency> dependencies(ProcessingContext context) {
        return List.of(
                new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "GPS", JOB_ID,
                        ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB),
                new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "MEDIA", JOB_ID,
                        ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB),
                new ProcessingDetails.Dependency(ProcessingDetails.Kind.JOB, DuplicateDetectorJob.JOB_ID, JOB_ID,
                        ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB),
                new ProcessingDetails.Dependency(ProcessingDetails.Kind.JOB, ActivityTypeClassifierJob.JOB_ID, JOB_ID,
                        ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB),
                new ProcessingDetails.Dependency(ProcessingDetails.Kind.JOB, EnergyRecalculationQueue.JOB_ID, JOB_ID,
                        ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB));
    }
    @Override public JobSummaryDto processingStatus(ProcessingContext context) {
        var current = summary;
        var blockers = finished ? List.<ProcessingDetails.Blocker>of() : prerequisites(context);
        boolean retrying = Instant.now().isBefore(nextCheck);
        return JobSummaryDto.counts(JOB_ID, "Google Health (synthetic)", current.pending(), current.done(), 0, 0, 0, null)
                .processing(this, context, blockers.isEmpty() ? 0 : current.pending(),
                        blockers.isEmpty() && retrying ? current.pending() : 0, blockers,
                        retrying ? nextCheck : null, "days", checked);
    }

}
