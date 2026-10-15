package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.x8ing.mtl.server.mtlserver.jobs.ImportPrerequisites;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService.JobSummaryDto;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService.UnavailableReasonsDto;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJob;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@JsonPropertyOrder({
        "gpsTrackRepository",
        "gpsTrackVariantSelector",
        "activityTypeAutoClassifier",
        "systemLogService",
        "indexerStatusService"
})
public class ActivityTypeClassifierJob implements ProcessingJob {

    static final int BATCH_SIZE = 100;

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackVariantSelector gpsTrackVariantSelector;
    private final ActivityTypeAutoClassifier activityTypeAutoClassifier;
    private final SystemLogService systemLogService;
    private final IndexerStatusService indexerStatusService;
    private final ShutdownSignal shutdownSignal;

    public ActivityTypeClassifierJob(GpsTrackRepository gpsTrackRepository,
                                     GpsTrackVariantSelector gpsTrackVariantSelector,
                                     ActivityTypeAutoClassifier activityTypeAutoClassifier,
                                     SystemLogService systemLogService,
                                     IndexerStatusService indexerStatusService,
                                     ShutdownSignal shutdownSignal) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackVariantSelector = gpsTrackVariantSelector;
        this.activityTypeAutoClassifier = activityTypeAutoClassifier;
        this.systemLogService = systemLogService;
        this.indexerStatusService = indexerStatusService;
        this.shutdownSignal = shutdownSignal;
    }

    public boolean run() { return reportedBatch().hasMore(); }

    public ProcessingDetails.BatchOutcome reportedBatch() {
        if (shutdownSignal.isStopRequested()) return ProcessingDetails.BatchOutcome.finished(false);
        var gates = prerequisites(ProcessingContext.indexes(indexerStatusService));
        if (!gates.isEmpty()) {
            log.debug("Activity type classifier is waiting for GPS indexing to settle");
            return ProcessingDetails.BatchOutcome.waiting(gates);
        }
        long t0 = System.currentTimeMillis();
        log.debug("Start find activity classifier job");


        List<Long> trackIds = gpsTrackRepository.findIdsPendingActivityClassification(PageRequest.of(0, BATCH_SIZE));
        if (!trackIds.isEmpty()) {
            BackgroundJobRunner.batchStarted();
            log.info("Activity classification batch started. tracksToBeChecked={}", trackIds.size());

            int completed = 0;
            int failed = 0;
            int progressed = 0;
            int persistenceFailures = 0;
            for (Long trackId : trackIds) {
                if (shutdownSignal.isStopRequested()) {
                    log.info("Activity classification batch stopped: completed={} failed={} remaining={} durationMs={}",
                            completed, failed, trackIds.size() - completed - failed, System.currentTimeMillis() - t0);
                    return ProcessingDetails.BatchOutcome.finished(false);
                }
                try {
                    // Classify on the full-density cleaned variant (was: SIMPLIFIED@1m)
                    // so dense 1-Hz sections aren't downsampled before activity detection.
                    List<GpsTrackDataPoint> trackPoints = gpsTrackVariantSelector.pointsForMetrics(trackId);

                    var result = activityTypeAutoClassifier.classifyActivity(trackId, trackPoints);
                    if (result.updated() && result.determinedType() == null) {
                        failed++;
                    } else {
                        completed++;
                    }
                    if (result.updated()) progressed++;
                    // Classification commits its durable energy request in the same transaction.
                } catch (Exception e) {
                    boolean interrupted = Thread.currentThread().isInterrupted()
                                          || org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(e) instanceof InterruptedException;
                    if (interrupted) Thread.currentThread().interrupt();
                    if (shutdownSignal.isStopRequested() || interrupted) {
                        log.info("Activity classification stopped and leaves track pending: trackId={} type={}", trackId, e.getClass().getSimpleName());
                        log.debug("Stopped background track calculation", e);
                        return ProcessingDetails.BatchOutcome.finished(false);
                    }
                    if (BackgroundJobRunner.isTemporaryConnectionFailure(e)) {
                        log.warn("Activity classification waits for a database connection: trackId={}, type={}, completed={}, remaining={}; work stays pending for the next scheduled turn",
                                trackId, e.getClass().getSimpleName(), completed, trackIds.size() - completed - failed);
                        log.debug("Temporary activity classification connection failure", e);
                        return ProcessingDetails.BatchOutcome.finished(false);
                    }
                    failed++;
                    String errMsg = "Activity classification failed for trackId=%s exceptionType=%s; marking task FAILED"
                            .formatted(trackId, e.getClass().getSimpleName());
                    log.error(errMsg, e);
                    try {
                        activityTypeAutoClassifier.markFailed(trackId,
                                "Activity classification failed (%s). See server logs.".formatted(e.getClass().getSimpleName()));
                        progressed++;
                    } catch (RuntimeException persistenceFailure) {
                        persistenceFailures++;
                        log.error("Could not persist activity failure for trackId={} exceptionType={}; continuing batch",
                                trackId, persistenceFailure.getClass().getSimpleName(), persistenceFailure);
                    }
                    try {
                        systemLogService.saveLogForException(this.getClass(), "ClassifierException", errMsg, e);
                    } catch (RuntimeException logFailure) {
                        log.error("Could not persist activity error log for trackId={} exceptionType={}; continuing batch",
                                trackId, logFailure.getClass().getSimpleName(), logFailure);
                    }
                }

            }
            log.info("Activity classification batch completed. completed={} failed={} durationMs={}",
                    completed, failed, System.currentTimeMillis() - t0);
            if (persistenceFailures > 0) return ProcessingDetails.BatchOutcome.finished(false);
            return ProcessingDetails.BatchOutcome.finished(trackIds.size() == BATCH_SIZE && progressed > 0);
        }
        return ProcessingDetails.BatchOutcome.finished(false);
    }


    public static final String JOB_ID = "activityType";
    @Override public String jobId() { return JOB_ID; }
    @Override public int displayOrder() { return 20; }
    @Override public boolean resourceIntensive() { return true; }
    @Override public List<ProcessingDetails.Blocker> prerequisites(ProcessingContext context) {
        return ImportPrerequisites.check(context, context.demoMode());
    }
    @Override public List<ProcessingDetails.Dependency> dependencies(ProcessingContext context) {
        var edges = new ArrayList<ProcessingDetails.Dependency>();
        edges.add(new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "GPS", JOB_ID,
                ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB));
        edges.add(new ProcessingDetails.Dependency(ProcessingDetails.Kind.JOB, "duplicate", JOB_ID,
                ProcessingDetails.Relation.INPUT, ProcessingDetails.Scope.ITEMS));
        if (context.demoMode()) edges.add(new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "MEDIA", JOB_ID,
                ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB));
        return List.copyOf(edges);
    }
    @Override public JobSummaryDto processingStatus(ProcessingContext context) {
        var counts = gpsTrackRepository.countActivityTypeSummary(ActivityTypeAutoClassifier.NO_DATA_POINTS_MESSAGE + "%");
        long failed = Math.max(0, counts.getFailed() - counts.getUnavailable());
        var blockers = new ArrayList<>(prerequisites(context));
        long waiting = blockers.isEmpty() ? counts.getWaiting() : counts.getPending();
        if (counts.getWaiting() > 0) blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.JOB,
                "duplicate", "DUPLICATE_CHECKS_PENDING", ProcessingDetails.Scope.ITEMS));
        return JobSummaryDto.counts(JOB_ID, "Activity classification", counts.getPending(), counts.getDone(), failed,
                counts.getUnavailable(), failed, new UnavailableReasonsDto(counts.getUnavailable(), 0, 0, 0))
                .processing(this, context, waiting, 0, blockers, null, "tracks", true);
    }

}
