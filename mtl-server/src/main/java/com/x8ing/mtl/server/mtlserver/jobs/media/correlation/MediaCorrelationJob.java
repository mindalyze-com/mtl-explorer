package com.x8ing.mtl.server.mtlserver.jobs.media.correlation;

import com.x8ing.mtl.server.mtlserver.jobs.ImportPrerequisites;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService.JobSummaryDto;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJob;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
@JsonPropertyOrder({"repository", "worker", "indexerStatusService"})
public class MediaCorrelationJob implements ProcessingJob {

    public static final int ALGORITHM_VERSION = 1;
    static final int TRACK_BATCH_SIZE = 25;
    static final int MEDIA_BATCH_SIZE = 500;
    static final int FAILED_MEDIA_RETRY_DELAY_SECONDS = 5 * 60;
    static final int MAX_FAILURE_MESSAGE_LENGTH = 1_000;

    private final MediaCorrelationRepository repository;
    private final MediaCorrelationAtomicWorker worker;
    private final IndexerStatusService indexerStatusService;
    private final ShutdownSignal shutdownSignal;
    private final BackgroundJobRunner backgroundJobs;

    public MediaCorrelationJob(MediaCorrelationRepository repository,
                               MediaCorrelationAtomicWorker worker,
                               IndexerStatusService indexerStatusService,
                               ShutdownSignal shutdownSignal, BackgroundJobRunner backgroundJobs) {
        this.repository = repository;
        this.worker = worker;
        this.indexerStatusService = indexerStatusService;
        this.shutdownSignal = shutdownSignal;
        this.backgroundJobs = backgroundJobs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void enqueueStaleMedia() {
        if (shutdownSignal.isStopRequested()) return;
        int queued = repository.enqueueStaleMedia(ALGORITHM_VERSION);
        if (queued > 0) {
            log.info("Scheduled {} media items for persisted position correlation.", queued);
        }
    }

    @Scheduled(
            fixedDelayString = "${mtl.media-correlation.run-schedule:PT5S}",
            initialDelayString = "${mtl.media-correlation.initial-delay:PT2S}")
    public void run() {
        backgroundJobs.runReported(this, this::reportedBatch);
    }

    boolean runBatch() { return reportedBatch().hasMore(); }

    public ProcessingDetails.BatchOutcome reportedBatch() {
        if (shutdownSignal.isStopRequested()) return ProcessingDetails.BatchOutcome.finished(false);
        var gates = prerequisites(ProcessingContext.indexes(indexerStatusService));
        if (!gates.isEmpty()) {
            log.debug("Media correlation is waiting for file indexing to settle");
            return ProcessingDetails.BatchOutcome.waiting(gates);
        }
        if (repository.hasReadyWork()) BackgroundJobRunner.batchStarted();
        long started = System.currentTimeMillis();
        int expanded = worker.expandTrackWork(TRACK_BATCH_SIZE);
        int rebuilt = 0;
        if (!shutdownSignal.isStopRequested()) {
            try {
                rebuilt = worker.rebuildMediaBatch(MEDIA_BATCH_SIZE, ALGORITHM_VERSION);
            } catch (MediaCorrelationBatchException batchFailure) {
                log.warn("Media correlation batch failed; retrying {} items separately.",
                        batchFailure.mediaIds().size(), batchFailure.getCause());
                rebuilt = rebuildIndividually(batchFailure.mediaIds());
            }
        }
        if (expanded > 0 || rebuilt > 0) {
            log.info("Persisted media correlation processed tracks={} media={} remaining={} stopped={} durationMs={}",
                    expanded, rebuilt, repository.countPendingWork(), shutdownSignal.isStopRequested(),
                    System.currentTimeMillis() - started);
        }
        return ProcessingDetails.BatchOutcome.finished(!shutdownSignal.isStopRequested() && (expanded > 0 || rebuilt > 0));
    }

    private int rebuildIndividually(List<Long> mediaIds) {
        int rebuilt = 0;
        for (Long mediaId : mediaIds) {
            if (shutdownSignal.isStopRequested()) break;
            try {
                if (worker.rebuildMediaId(mediaId, ALGORITHM_VERSION)) {
                    rebuilt++;
                }
            } catch (RuntimeException itemFailure) {
                String message = failureMessage(itemFailure);
                worker.deferMediaFailure(mediaId, message, FAILED_MEDIA_RETRY_DELAY_SECONDS);
                log.error(
                        "Media correlation deferred mediaId={} retryDelaySeconds={} error={}",
                        mediaId,
                        FAILED_MEDIA_RETRY_DELAY_SECONDS,
                        message);
            }
        }
        return rebuilt;
    }

    private static String failureMessage(RuntimeException failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = root.getClass().getSimpleName();
        }
        return message.length() <= MAX_FAILURE_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }
    public static final String JOB_ID = "mediaCorrelation";
    @Override public String jobId() { return JOB_ID; }
    @Override public int displayOrder() { return 50; }
    @Value("${mtl.media-correlation.run-schedule:PT5S}")
    private String runSchedule = "PT5S";
    @Value("${mtl.media-correlation.initial-delay:PT2S}")
    private String startupDelay = "PT2S";
    @Override public Duration checkInterval() { return org.springframework.boot.convert.DurationStyle.detectAndParse(runSchedule); }
    @Override public Duration initialDelay() { return org.springframework.boot.convert.DurationStyle.detectAndParse(startupDelay); }
    @Override public List<ProcessingDetails.Blocker> prerequisites(ProcessingContext context) {
        return ImportPrerequisites.check(context, true);
    }
    @Override public List<ProcessingDetails.Dependency> dependencies(ProcessingContext context) {
        return List.of(
                new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "GPS", JOB_ID,
                        ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB),
                new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "MEDIA", JOB_ID,
                        ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB),
                new ProcessingDetails.Dependency(ProcessingDetails.Kind.JOB, "duplicate", JOB_ID,
                        ProcessingDetails.Relation.RETRIGGER, ProcessingDetails.Scope.ITEMS));
    }
    @Override public JobSummaryDto processingStatus(ProcessingContext context) {
        var counts = repository.processingCounts(ALGORITHM_VERSION);
        var blockers = prerequisites(context);
        long pending = counts.pending();
        return JobSummaryDto.counts(JOB_ID, "Media location matching", pending, counts.done(), 0, 0, 0, null)
                .processing(this, context, blockers.isEmpty() ? 0 : pending,
                        blockers.isEmpty() ? counts.retrying() : 0, blockers, counts.nextRetryAt(), "work items", true);
    }

}
