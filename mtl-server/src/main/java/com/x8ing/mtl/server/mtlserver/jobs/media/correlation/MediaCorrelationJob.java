package com.x8ing.mtl.server.mtlserver.jobs.media.correlation;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@JsonPropertyOrder({"repository", "worker", "indexerStatusService"})
public class MediaCorrelationJob {

    public static final int ALGORITHM_VERSION = 1;
    static final int TRACK_BATCH_SIZE = 25;
    static final int MEDIA_BATCH_SIZE = 500;
    static final int MAX_BATCHES_PER_RUN = 20;
    static final int FAILED_MEDIA_RETRY_DELAY_SECONDS = 5 * 60;
    static final int MAX_FAILURE_MESSAGE_LENGTH = 1_000;

    private final MediaCorrelationRepository repository;
    private final MediaCorrelationAtomicWorker worker;
    private final IndexerStatusService indexerStatusService;

    public MediaCorrelationJob(MediaCorrelationRepository repository,
                               MediaCorrelationAtomicWorker worker,
                               IndexerStatusService indexerStatusService) {
        this.repository = repository;
        this.worker = worker;
        this.indexerStatusService = indexerStatusService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void enqueueStaleMedia() {
        int queued = repository.enqueueStaleMedia(ALGORITHM_VERSION);
        if (queued > 0) {
            log.info("Scheduled {} media items for persisted position correlation.", queued);
        }
    }

    @Scheduled(
            fixedDelayString = "${mtl.media-correlation.run-schedule:PT5S}",
            initialDelayString = "${mtl.media-correlation.initial-delay:PT2S}")
    public void run() {
        if (indexerStatusService.hasIndexPendingWork(GPXDirectoryWatcherService.INDEX_GPS)
            || indexerStatusService.hasIndexPendingWork(MediaIndexerService.INDEX_MEDIA)) {
            log.debug("Media correlation is waiting for file indexing to settle");
            return;
        }
        long started = System.currentTimeMillis();
        int expandedTracks = 0;
        int rebuiltMedia = 0;

        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int expanded = worker.expandTrackWork(TRACK_BATCH_SIZE);
            int rebuilt;
            try {
                rebuilt = worker.rebuildMediaBatch(MEDIA_BATCH_SIZE, ALGORITHM_VERSION);
            } catch (MediaCorrelationBatchException batchFailure) {
                log.warn(
                        "Media correlation batch failed; retrying {} items separately.",
                        batchFailure.mediaIds().size(),
                        batchFailure.getCause());
                rebuilt = rebuildIndividually(batchFailure.mediaIds());
            }
            expandedTracks += expanded;
            rebuiltMedia += rebuilt;
            if (expanded == 0 && rebuilt == 0) {
                break;
            }
        }

        if (expandedTracks > 0 || rebuiltMedia > 0) {
            log.info(
                    "Persisted media correlation processed tracks={} media={} remaining={} durationMs={}",
                    expandedTracks,
                    rebuiltMedia,
                    repository.countPendingWork(),
                    System.currentTimeMillis() - started);
        }
    }

    private int rebuildIndividually(List<Long> mediaIds) {
        int rebuilt = 0;
        for (Long mediaId : mediaIds) {
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
}
