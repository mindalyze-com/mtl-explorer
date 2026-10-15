package com.x8ing.mtl.server.mtlserver.energy;

import lombok.extern.slf4j.Slf4j;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeAutoClassifier;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService.JobSummaryDto;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService.UnavailableReasonsDto;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJob;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


/**
 * Durable post-commit handoff from classification and track replacement to the energy calculation.
 */
@Service
@Slf4j
public class EnergyRecalculationQueue implements ProcessingJob {
    // Keep each transaction independent while avoiding long pauses during bulk imports.
    private static final int BATCH_SIZE = 100;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);
    private final JdbcTemplate jdbc;
    private final EnergyService energyService;
    private final ShutdownSignal shutdownSignal;
    private final BackgroundJobRunner backgroundJobs;

    public EnergyRecalculationQueue(JdbcTemplate jdbc, EnergyService energyService, ShutdownSignal shutdownSignal,
                                    BackgroundJobRunner backgroundJobs) {
        this.jdbc = jdbc;
        this.energyService = energyService;
        this.shutdownSignal = shutdownSignal;
        this.backgroundJobs = backgroundJobs;
    }

    public record SummaryCounts(long pending, long done, long noDataPoints, long unsupportedTiming,
                                long missingActivity, long missingCalculation, long waiting,
                                long retrying, Instant nextRetryAt) {
        public SummaryCounts(long pending, long done, long noDataPoints, long unsupportedTiming,
                             long missingActivity, long missingCalculation) {
            this(pending, done, noDataPoints, unsupportedTiming, missingActivity, missingCalculation, 0, 0, null);
        }
        public long unavailable() {
            return noDataPoints + unsupportedTiming + missingActivity + missingCalculation;
        }

        public long attentionRequired() {
            return missingActivity + missingCalculation;
        }
    }

    /**
     * Imported-track generations include running work and delayed retries; old results cannot count them as done.
     */
    @Transactional(readOnly = true)
    public SummaryCounts summaryCounts() {
        return jdbc.queryForObject("""
                WITH outcomes AS (
                    SELECT CASE
                               WHEN request.track_id IS NOT NULL AND request.next_attempt_at > now() THEN 'retrying'
                               WHEN request.track_id IS NOT NULL THEN 'pending'
                               WHEN track.timing_unreliable THEN 'unsupported_timing'
                               WHEN track.energy_net_total_wh IS NOT NULL THEN 'done'
                               WHEN track.activity_type_source = 'FAILED'
                                    AND track.activity_type_source_details LIKE ? THEN 'no_data_points'
                               WHEN track.activity_type_source IS NULL THEN 'waiting'
                               WHEN track.activity_type IS NULL THEN 'missing_activity'
                               ELSE 'missing_calculation'
                           END AS outcome, request.next_attempt_at
                      FROM public.gps_track track
                      LEFT JOIN public.track_energy_recalculation request ON request.track_id = track.id
                     WHERE track.load_status = 'SUCCESS'
                       AND track.track_source = ?
                )
                SELECT count(*) FILTER (WHERE outcome IN ('pending', 'retrying')) AS pending,
                       count(*) FILTER (WHERE outcome = 'done') AS done,
                       count(*) FILTER (WHERE outcome = 'no_data_points') AS no_data_points,
                       count(*) FILTER (WHERE outcome = 'unsupported_timing') AS unsupported_timing,
                       count(*) FILTER (WHERE outcome = 'missing_activity') AS missing_activity,
                       count(*) FILTER (WHERE outcome = 'missing_calculation') AS missing_calculation,
                       count(*) FILTER (WHERE outcome = 'waiting') AS waiting,
                       count(*) FILTER (WHERE outcome = 'retrying') AS retrying,
                       min(next_attempt_at) FILTER (WHERE outcome = 'retrying') AS next_retry
                  FROM outcomes
                """, (rs, row) -> new SummaryCounts(rs.getLong("pending"), rs.getLong("done"),
                rs.getLong("no_data_points"), rs.getLong("unsupported_timing"), rs.getLong("missing_activity"),
                rs.getLong("missing_calculation"), rs.getLong("waiting"), rs.getLong("retrying"),
                rs.getTimestamp("next_retry") == null ? null : rs.getTimestamp("next_retry").toInstant()), ActivityTypeAutoClassifier.NO_DATA_POINTS_MESSAGE + "%",
                GpsTrack.TRACK_SOURCE.IMPORTED.name());
    }

    /**
     * The request must commit atomically with its classification or imported replacement.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void request(long trackId) {
        jdbc.update("""
                INSERT INTO public.track_energy_recalculation (track_id, generation, requested_at, next_attempt_at)
                VALUES (?, 1, now(), now())
                ON CONFLICT (track_id) DO UPDATE SET
                    generation = track_energy_recalculation.generation + 1,
                    requested_at = now(), next_attempt_at = now(), last_error = NULL
                """, trackId);
        log.debug("Track energy calculation queued: trackId={}", trackId);
    }

    @Transactional(readOnly = true)
    public EnergyCalculationStatus statusForTrack(GpsTrack track) {
        if (track.isTimingUnreliable()) return new EnergyCalculationStatus(EnergyCalculationStatus.State.UNAVAILABLE,
                "Source timing is unreliable or estimated. See Track review.", null);
        var pending = jdbc.query("""
                SELECT last_error, next_attempt_at FROM public.track_energy_recalculation WHERE track_id = ?
                """, (rs, row) -> {
            boolean failed = rs.getString("last_error") != null;
            return new EnergyCalculationStatus(failed ? EnergyCalculationStatus.State.RETRYING : EnergyCalculationStatus.State.PENDING,
                    failed ? "Energy calculation failed. A retry is scheduled." : "Energy calculation is queued or running.",
                    rs.getTimestamp("next_attempt_at").toInstant());
        }, track.getId());
        if (!pending.isEmpty()) return pending.getFirst();
        if (track.getEnergyNetTotalWh() != null) return new EnergyCalculationStatus(EnergyCalculationStatus.State.CALCULATED, null, null);
        // Classification has not run yet; it queues the calculation once it assigns an activity.
        if (track.getActivityTypeSource() == null) {
            return new EnergyCalculationStatus(EnergyCalculationStatus.State.PENDING,
                    "Waiting for activity classification.", null);
        }
        return new EnergyCalculationStatus(EnergyCalculationStatus.State.UNAVAILABLE,
                track.getActivityType() == null ? "Select an activity to calculate energy." : "Energy calculation is unavailable for this track.", null);
    }

    @Scheduled(fixedDelayString = "${mtl.energy.recalculation-interval:PT5S}")
    public void processPending() {
        backgroundJobs.runReported(this, this::reportedBatch);
    }

    boolean processBatch() { return reportedBatch().hasMore(); }

    public ProcessingDetails.BatchOutcome reportedBatch() {
        if (shutdownSignal.isStopRequested()) return ProcessingDetails.BatchOutcome.finished(false);
        var requests = jdbc.query("""
                SELECT request.track_id, request.generation, track.timing_unreliable
                  FROM public.track_energy_recalculation request
                  JOIN public.gps_track track ON track.id = request.track_id
                 WHERE request.next_attempt_at <= now() ORDER BY request.requested_at, request.track_id LIMIT ?
                """, (rs, row) -> new Request(rs.getLong(1), rs.getLong(2), rs.getBoolean(3)), BATCH_SIZE);
        if (requests.isEmpty()) return ProcessingDetails.BatchOutcome.finished(false);
        BackgroundJobRunner.batchStarted();
        long started = System.nanoTime();
        int completed = 0;
        int failed = 0;
        int unsupported = 0;
        int progressed = 0;
        log.info("Track energy calculation batch started: count={}", requests.size());
        for (Request request : requests) {
            if (shutdownSignal.isStopRequested()) {
                log.info("Track energy calculation batch stopped: completed={}, failed={}, remaining={}, durationMs={}",
                        completed, failed, requests.size() - completed - failed, (System.nanoTime() - started) / 1_000_000);
                return ProcessingDetails.BatchOutcome.finished(false);
            }
            try {
                if (request.timingUnreliable()) {
                    // Unsupported source timing is a terminal outcome for this generation, not a retryable error.
                    unsupported++;
                    log.debug("Track energy calculation omitted: trackId={} source timing is unsupported", request.trackId());
                } else {
                    long transactionStarted = System.nanoTime();
                    if (!energyService.recalculateEnergyForTrack(request.trackId(), null)) {
                        throw new IllegalStateException("Track is not ready for energy calculation");
                    }
                    // The proxied REQUIRES_NEW call returns only after flush and successful commit.
                    log.debug("Track energy transaction committed: trackId={}, durationMs={}",
                            request.trackId(), (System.nanoTime() - transactionStarted) / 1_000_000);
                }
                // A concurrent classification queues a new generation; never acknowledge it here.
                progressed += jdbc.update("DELETE FROM public.track_energy_recalculation WHERE track_id = ? AND generation = ?",
                        request.trackId(), request.generation());
                completed++;
            } catch (Exception exception) {
                if (Thread.currentThread().isInterrupted()
                    || org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(exception) instanceof InterruptedException
                    || exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    log.info("Track energy calculation interrupted and remains queued: trackId={}, type={}, error={}",
                            request.trackId(), exception.getClass().getSimpleName(), exception.getMessage());
                    log.debug("Interrupted track energy calculation", exception);
                    return ProcessingDetails.BatchOutcome.finished(false);
                }
                failed++;
                log.warn("Track energy calculation failed and remains queued: trackId={}, type={}, error={}",
                        request.trackId(), exception.getClass().getSimpleName(), exception.getMessage());
                log.debug("Recoverable track energy calculation failure", exception);
                progressed += jdbc.update("""
                                UPDATE public.track_energy_recalculation
                                   SET next_attempt_at = now() + (? * interval '1 second'), last_error = ?
                                 WHERE track_id = ? AND generation = ?
                                """, RETRY_DELAY.toSeconds(), exception.getClass().getSimpleName(),
                        request.trackId(), request.generation());
            }
        }
        log.info("Track energy calculation batch completed: completed={}, unsupported={}, failed={}, durationMs={}",
                completed, unsupported, failed, (System.nanoTime() - started) / 1_000_000);
        return ProcessingDetails.BatchOutcome.finished(requests.size() == BATCH_SIZE && progressed > 0);
    }

    private record Request(long trackId, long generation, boolean timingUnreliable) {
    }
    public static final String JOB_ID = "energy";
    @Override public String jobId() { return JOB_ID; }
    @Override public int displayOrder() { return 30; }
    @Value("${mtl.energy.recalculation-interval:PT5S}")
    private String runSchedule = "PT5S";
    @Override public Duration checkInterval() { return org.springframework.boot.convert.DurationStyle.detectAndParse(runSchedule); }
    @Override public Duration initialDelay() { return Duration.ZERO; }
    @Override public List<ProcessingDetails.Blocker> prerequisites(ProcessingContext context) {
        // Eligibility is per request; other tracks can proceed while classification is pending.
        return List.of();
    }
    @Override public List<ProcessingDetails.Dependency> dependencies(ProcessingContext context) {
        return List.of(new ProcessingDetails.Dependency(ProcessingDetails.Kind.JOB, "activityType", JOB_ID,
                ProcessingDetails.Relation.INPUT, ProcessingDetails.Scope.ITEMS));
    }
    @Override public JobSummaryDto processingStatus(ProcessingContext context) {
        var counts = summaryCounts();
        var blockers = new ArrayList<ProcessingDetails.Blocker>();
        if (counts.waiting() > 0) blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.JOB,
                "activityType", "ACTIVITY_PENDING", ProcessingDetails.Scope.ITEMS));
        if (counts.missingActivity() > 0) blockers.add(ProcessingDetails.Blocker.user("MISSING_ACTIVITY"));
        if (counts.missingCalculation() > 0) blockers.add(ProcessingDetails.Blocker.user("MISSING_CALCULATION"));
        // Tracks that need a user-selected activity are finished work that needs attention, not waiting work:
        // counting them as pending would keep the job below 100% until the user acts.
        return JobSummaryDto.counts(JOB_ID, "Energy calculation", counts.pending() + counts.waiting(), counts.done(), 0,
                counts.unavailable(), counts.attentionRequired(),
                new UnavailableReasonsDto(counts.noDataPoints(), counts.unsupportedTiming(),
                        counts.missingActivity(), counts.missingCalculation()))
                .processing(this, context, counts.waiting(), counts.retrying(), blockers, counts.nextRetryAt(), "tracks", true);
    }

}
