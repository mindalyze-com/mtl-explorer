package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.*;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.indexer.event.ProcessingFileIndexerObserver;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;

@Service
@Slf4j
@JsonPropertyOrder({
        "processingWorker",
        "indexerRepository",
        "trackProcessingStatusService",
        "indexerStatusService",
        "watchDirectory",
        "changeDetectionStrategy",
        "liveWatchEnabled",
        "workerThreads",
        "fileIndexerImpl",
        "shuttingDown",
        "txManager"
})
public class GPXDirectoryWatcherService {

    public static final String INDEX_GPS = "GPS"; // made public for cross-module event consumers

    private final GpxProcessingWorker processingWorker;

    private final IndexerRepository indexerRepository;
    private final TrackProcessingStatusService trackProcessingStatusService;
    private final IndexerStatusService indexerStatusService;

    @Value("${mtl.gpx-watch-directory}")
    private String watchDirectory;

    @Value("${mtl.indexer.change-detection-strategy:SIZE_AND_MTIME}")
    private String changeDetectionStrategy;

    @Value("${mtl.indexer.gps.live-watch-enabled:true}")
    private boolean liveWatchEnabled;

    @Value("${mtl.indexer.worker-threads:2}")
    private int workerThreads;

    private volatile FileIndexerImpl fileIndexerImpl;
    private boolean shuttingDown;

    private final PlatformTransactionManager txManager;

    public GPXDirectoryWatcherService(GpxProcessingWorker processingWorker,
                                      IndexerRepository indexerRepository,
                                      PlatformTransactionManager txManager,
                                      TrackProcessingStatusService trackProcessingStatusService,
                                      IndexerStatusService indexerStatusService) {
        this.processingWorker = processingWorker;
        this.indexerRepository = indexerRepository;
        this.txManager = txManager;
        this.trackProcessingStatusService = trackProcessingStatusService;
        this.indexerStatusService = indexerStatusService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void watchGPXDirectoryNonBlocking() {
        try {
            startGPXIndexer();
        } catch (RuntimeException exception) {
            indexerStatusService.registerScanState(INDEX_GPS, () -> false);
            log.error("GPS indexer startup failed on {}", watchDirectory, exception);
            throw exception;
        }
    }

    private synchronized void startGPXIndexer() {
        if (shuttingDown) return;

        log.info("GPX Directory watcher service starting on directory " + watchDirectory);

        FileIndexer fileIndexer = new FileIndexer(txManager, workerThreads, trackProcessingStatusService::markGpsProcessingStarted);
        final Path absoluteWatchPath = Paths.get(watchDirectory);

        // Inclusion: all supported track formats (case-insensitive).
        PathMatcher includeTrackFiles = FileSystems.getDefault().getPathMatcher("regex:" + SupportedTrackFormat.inclusionRegex());

        // Synchronous processing on the indexer worker provides natural backpressure.
        FileIndexerObserver observer = new ProcessingFileIndexerObserver(
                log, INDEX_GPS, processingWorker::processCreateOrChange, processingWorker::processDelete, indexerStatusService::recordUnreadableSource);

        FileIndexerImpl.ChangeDetectionStrategy strategy =
                FileIndexerImpl.ChangeDetectionStrategy.valueOf(changeDetectionStrategy);

        this.fileIndexerImpl = fileIndexer.findAndIndex(INDEX_GPS, absoluteWatchPath, indexerRepository, observer, false, IndexerPathMatchers.standardExclusions(), List.of(includeTrackFiles), strategy, liveWatchEnabled);
        trackProcessingStatusService.registerGpsScanState(fileIndexerImpl::isScanInProgress);
        indexerStatusService.registerScanState(INDEX_GPS, fileIndexerImpl::isScanInProgress);
        indexerStatusService.registerDiscoveryState(INDEX_GPS, fileIndexerImpl::isDiscoveryInProgress);
    }

    @PreDestroy
    public synchronized void shutdown() {
        stopAcceptingWork();
        if (fileIndexerImpl != null) fileIndexerImpl.shutdown();
    }

    @EventListener(ContextClosedEvent.class)
    public synchronized void stopAcceptingWork() {
        if (shuttingDown) return;
        shuttingDown = true;
        if (fileIndexerImpl != null) fileIndexerImpl.requestStop();
    }

    @Scheduled(fixedDelayString = "${mtl.indexer.gps.rescan-interval:PT12H}")
    public void scheduledRescan() {
        IndexerRescanSupport.scheduledRescan(fileIndexerImpl, log, INDEX_GPS);
    }

    public FileIndexerImpl.RescanRequestStatus requestRescan() {
        return IndexerRescanSupport.requestRescan(fileIndexerImpl, log, INDEX_GPS);
    }

}
