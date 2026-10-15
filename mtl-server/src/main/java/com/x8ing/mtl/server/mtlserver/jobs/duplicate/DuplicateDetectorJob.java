package com.x8ing.mtl.server.mtlserver.jobs.duplicate;

import com.x8ing.mtl.server.mtlserver.jobs.ImportPrerequisites;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService.JobSummaryDto;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJob;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@Slf4j
@Service
@JsonPropertyOrder({
        "gpsTrackRepository",
        "duplicateDetectorAtomicWorker",
        "indexerStatusService",
        "timeTolerance",
        "distanceTolerance"
})
public class DuplicateDetectorJob implements ProcessingJob {

    static final int BATCH_SIZE = 100;

    private final GpsTrackRepository gpsTrackRepository;

    private final DuplicateDetectorAtomicWorker duplicateDetectorAtomicWorker;
    private final IndexerStatusService indexerStatusService;
    private final ShutdownSignal shutdownSignal;

    @Value("${mtl.duplicate.time-tolerance}")
    private Duration timeTolerance;

    @Value("${mtl.duplicate.distance-tolerance}")
    private Double distanceTolerance;

    public DuplicateDetectorJob(GpsTrackRepository gpsTrackRepository,
                                DuplicateDetectorAtomicWorker duplicateDetectorAtomicWorker,
                                IndexerStatusService indexerStatusService,
                                ShutdownSignal shutdownSignal) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.duplicateDetectorAtomicWorker = duplicateDetectorAtomicWorker;
        this.indexerStatusService = indexerStatusService;
        this.shutdownSignal = shutdownSignal;
    }

    public boolean run() { return reportedBatch().hasMore(); }

    public ProcessingDetails.BatchOutcome reportedBatch() {
        if (shutdownSignal.isStopRequested()) return ProcessingDetails.BatchOutcome.finished(false);
        var gates = prerequisites(ProcessingContext.indexes(indexerStatusService));
        if (!gates.isEmpty()) {
            log.debug("Duplicate detection is waiting for GPS indexing to settle");
            return ProcessingDetails.BatchOutcome.waiting(gates);
        }
        long t0 = System.currentTimeMillis();
        log.debug("Start find duplicate job");

        if (timeTolerance == null) {
            String configError = "TimeTolerance not configured for DuplicateDetector (mtl.duplicate.time-tolerance).";
            throw new RuntimeException(configError);
        }

        if (distanceTolerance == null || distanceTolerance <= 0) {
            String configError = "distanceTolerance not configured for DuplicateDetector (mtl.duplicate.distance-tolerance).";
            throw new RuntimeException(configError);
        }

        List<Long> tracks = gpsTrackRepository.findIdsByDuplicateStatusExcludingPlanned(
                GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET, PageRequest.of(0, BATCH_SIZE));
        if (!tracks.isEmpty()) {
            BackgroundJobRunner.batchStarted();
            log.info("Starting duplicate detection. tracksToBeChecked={} timeTolerance={}", tracks.size(), timeTolerance);

            int completed = 0;
            int failed = 0;
            int progressed = 0;
            int persistenceFailures = 0;
            for (Long trackId : tracks) {
                if (shutdownSignal.isStopRequested()) {
                    log.info("Duplicate detection batch stopped: completed={} failed={} remaining={} durationMs={}",
                            completed, failed, tracks.size() - completed - failed, System.currentTimeMillis() - t0);
                    return ProcessingDetails.BatchOutcome.finished(false);
                }
                try {
                    duplicateDetectorAtomicWorker.processOne(trackId, timeTolerance, distanceTolerance);
                    completed++;
                    progressed++;
                } catch (RuntimeException e) {
                    boolean interrupted = Thread.currentThread().isInterrupted()
                                          || org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(e) instanceof InterruptedException;
                    if (interrupted) Thread.currentThread().interrupt();
                    if (shutdownSignal.isStopRequested() || interrupted) {
                        log.info("Duplicate detection stopped and leaves track pending: trackId={} type={}", trackId, e.getClass().getSimpleName());
                        log.debug("Stopped background track calculation", e);
                        return ProcessingDetails.BatchOutcome.finished(false);
                    }
                    if (BackgroundJobRunner.isTemporaryConnectionFailure(e)) {
                        log.warn("Duplicate detection waits for a database connection: trackId={}, type={}, completed={}, remaining={}; work stays pending for the next scheduled turn",
                                trackId, e.getClass().getSimpleName(), completed, tracks.size() - completed - failed);
                        log.debug("Temporary duplicate detection connection failure", e);
                        return ProcessingDetails.BatchOutcome.finished(false);
                    }
                    // The worker rolls back this track's transaction; other tracks can still finish.
                    failed++;
                    log.error("Duplicate detection failed for trackId={} exceptionType={}; marking task FAILED",
                            trackId, e.getClass().getSimpleName(), e);
                    try {
                        duplicateDetectorAtomicWorker.markFailed(trackId,
                                "Duplicate detection failed (%s). See server logs.".formatted(e.getClass().getSimpleName()));
                        progressed++;
                    } catch (RuntimeException persistenceFailure) {
                        persistenceFailures++;
                        log.error("Could not persist duplicate failure for trackId={} exceptionType={}; continuing batch",
                                trackId, persistenceFailure.getClass().getSimpleName(), persistenceFailure);
                    }
                }
            }

            log.info("Duplicate detection batch completed. completed={} failed={} durationMs={}",
                    completed, failed, System.currentTimeMillis() - t0);
            if (persistenceFailures > 0) return ProcessingDetails.BatchOutcome.finished(false);
            return ProcessingDetails.BatchOutcome.finished(tracks.size() == BATCH_SIZE && progressed > 0);
        }
        return ProcessingDetails.BatchOutcome.finished(false);
    }


    public static final String JOB_ID = "duplicate";
    @Override public String jobId() { return JOB_ID; }
    @Override public int displayOrder() { return 10; }
    @Override public boolean resourceIntensive() { return true; }
    @Override public List<ProcessingDetails.Blocker> prerequisites(ProcessingContext context) {
        return ImportPrerequisites.check(context, context.demoMode());
    }
    @Override public List<ProcessingDetails.Dependency> dependencies(ProcessingContext context) {
        var edges = new ArrayList<ProcessingDetails.Dependency>();
        edges.add(new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "GPS", JOB_ID,
                ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB));
        if (context.demoMode()) edges.add(new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "MEDIA", JOB_ID,
                ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB));
        return List.copyOf(edges);
    }
    @Override public JobSummaryDto processingStatus(ProcessingContext context) {
        var counts = new EnumMap<GpsTrack.DUPLICATE_CHECK_STATUS, Long>(GpsTrack.DUPLICATE_CHECK_STATUS.class);
        gpsTrackRepository.countGroupedByDuplicateStatus().forEach(row ->
                counts.put((GpsTrack.DUPLICATE_CHECK_STATUS) row[0], (Long) row[1]));
        long pending = counts.getOrDefault(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET, 0L);
        long done = counts.getOrDefault(GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE, 0L)
                + counts.getOrDefault(GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE, 0L);
        long failed = counts.getOrDefault(GpsTrack.DUPLICATE_CHECK_STATUS.FAILED, 0L);
        var blockers = prerequisites(context);
        return JobSummaryDto.counts(JOB_ID, "Duplicate detection", pending, done, failed, 0, failed, null)
                .processing(this, context, blockers.isEmpty() ? 0 : pending, 0, blockers, null, "tracks", true);
    }

}
