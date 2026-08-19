package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.FileIndexer;
import com.x8ing.mtl.server.mtlserver.indexer.FileIndexerImpl;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerPathMatchers;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerRescanSupport;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.indexer.event.ProcessingFileIndexerObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
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
        "watchDirectory",
        "changeDetectionStrategy",
        "liveWatchEnabled",
        "workerThreads",
        "fileIndexerImpl",
        "txManager"
})
public class GPXDirectoryWatcherService {

    public static final String INDEX_GPS = "GPS"; // made public for cross-module event consumers

    private final GpxProcessingWorker processingWorker;

    private final IndexerRepository indexerRepository;

    @Value("${mtl.gpx-watch-directory}")
    private String watchDirectory;

    @Value("${mtl.indexer.change-detection-strategy:SIZE_AND_MTIME}")
    private String changeDetectionStrategy;

    @Value("${mtl.indexer.gps.live-watch-enabled:true}")
    private boolean liveWatchEnabled;

    @Value("${mtl.indexer.worker-threads:2}")
    private int workerThreads;

    private volatile FileIndexerImpl fileIndexerImpl;

    private final PlatformTransactionManager txManager;

    public GPXDirectoryWatcherService(GpxProcessingWorker processingWorker,
                                      IndexerRepository indexerRepository,
                                      PlatformTransactionManager txManager) {
        this.processingWorker = processingWorker;
        this.indexerRepository = indexerRepository;
        this.txManager = txManager;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void watchGPXDirectoryNonBlocking() {

        log.info("GPX Directory watcher service starting on directory " + watchDirectory);

        FileIndexer fileIndexer = new FileIndexer(txManager, workerThreads);
        final Path absoluteWatchPath = Paths.get(watchDirectory);

        // Inclusion: all supported track formats (case-insensitive).
        PathMatcher includeTrackFiles = FileSystems.getDefault().getPathMatcher("regex:" + SupportedTrackFormat.inclusionRegex());

        // Synchronous processing on the indexer worker provides natural backpressure.
        FileIndexerObserver observer = new ProcessingFileIndexerObserver(
                log, INDEX_GPS, processingWorker::processCreateOrChange, processingWorker::processDelete);

        FileIndexerImpl.ChangeDetectionStrategy strategy =
                FileIndexerImpl.ChangeDetectionStrategy.valueOf(changeDetectionStrategy);

        this.fileIndexerImpl = fileIndexer.findAndIndex(INDEX_GPS, absoluteWatchPath, indexerRepository, observer, false, IndexerPathMatchers.standardExclusions(), List.of(includeTrackFiles), strategy, liveWatchEnabled);
    }

    @Scheduled(fixedDelayString = "${mtl.indexer.gps.rescan-interval:PT12H}")
    public void scheduledRescan() {
        IndexerRescanSupport.scheduledRescan(fileIndexerImpl, log, INDEX_GPS);
    }

    public FileIndexerImpl.RescanRequestStatus requestRescan() {
        return IndexerRescanSupport.requestRescan(fileIndexerImpl, log, INDEX_GPS);
    }

}
