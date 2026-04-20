package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.FileIndexer;
import com.x8ing.mtl.server.mtlserver.indexer.FileIndexerImpl;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.indexer.event.OnCompletion;
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
public class GPXDirectoryWatcherService {

    public static final String INDEX_GPS = "GPS"; // made public for cross-module event consumers

    private final GpxProcessingWorker processingWorker;

    private final IndexerRepository indexerRepository;

    @Value("${mtl.gpx-watch-directory}")
    private String watchDirectory;

    @Value("${mtl.indexer.change-detection-strategy:SIZE_ONLY}")
    private String changeDetectionStrategy;

    @Value("${mtl.indexer.gps.live-watch-enabled:true}")
    private boolean liveWatchEnabled;

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

        FileIndexer fileIndexer = new FileIndexer(txManager);
        final Path absoluteWatchPath = Paths.get(watchDirectory);

        // Inclusion: all supported track formats (case-insensitive).
        PathMatcher includeTrackFiles = FileSystems.getDefault().getPathMatcher("regex:" + SupportedTrackFormat.inclusionRegex());

        // Exclusions: .DS_Store files, @eaDir folders, and hidden files starting with dot
        PathMatcher dsStore = FileSystems.getDefault().getPathMatcher("regex:.*\\.DS_Store$");
        PathMatcher eaDir = FileSystems.getDefault().getPathMatcher("regex:.*@eaDir(/.*)?");
        PathMatcher hiddenFiles = FileSystems.getDefault().getPathMatcher("regex:.*/\\.[^/]+$");

        FileIndexerObserver observer = new FileIndexerObserver() {
            // Synchronous observer — processing runs on the indexer's workerPool thread,
            // providing natural backpressure. Files stay SCHEDULED until started() is called.
            @Override
            public void onNewFile(String index, long fileId, OnCompletion completion) {
                completion.started(fileId);
                try {
                    processingWorker.processCreateOrChange(index, fileId, false);
                    completion.success(fileId);
                } catch (Throwable t) {
                    log.error("GPS processing failed for new fileId={}: {}", fileId, t.toString(), t);
                    completion.failed(fileId, safeMsg(t));
                    if (t instanceof Error) throw (Error) t;
                }
            }

            @Override
            public void onDeletedFile(String index, long fileId, OnCompletion completion) {
                completion.started(fileId);
                try {
                    processingWorker.processDelete(index, fileId);
                    completion.success(fileId);
                } catch (Throwable t) {
                    log.error("GPS delete failed for fileId={}: {}", fileId, t.toString(), t);
                    completion.failed(fileId, safeMsg(t));
                    if (t instanceof Error) throw (Error) t;
                }
            }

            @Override
            public void onChangedFile(String index, long fileId, OnCompletion completion) {
                completion.started(fileId);
                try {
                    processingWorker.processCreateOrChange(index, fileId, true);
                    completion.success(fileId);
                } catch (Throwable t) {
                    log.error("GPS processing failed for changed fileId={}: {}", fileId, t.toString(), t);
                    completion.failed(fileId, safeMsg(t));
                    if (t instanceof Error) throw (Error) t;
                }
            }

            // Legacy overloads not used by the indexer now
            @Override
            public void onNewFile(String index, long fileId) {
            }

            @Override
            public void onDeletedFile(String index, long fileId) {
            }

            @Override
            public void onChangedFile(String index, long fileId) {
            }

            private String safeMsg(Throwable t) {
                String s = t.getMessage();
                return (s == null || s.isBlank()) ? t.getClass().getSimpleName() : s;
            }
        };

        FileIndexerImpl.ChangeDetectionStrategy strategy =
                FileIndexerImpl.ChangeDetectionStrategy.valueOf(changeDetectionStrategy);

        this.fileIndexerImpl = fileIndexer.findAndIndex(INDEX_GPS, absoluteWatchPath, indexerRepository, observer, false, List.of(dsStore, eaDir, hiddenFiles), List.of(includeTrackFiles), strategy, liveWatchEnabled);
    }

    @Scheduled(fixedDelayString = "${mtl.indexer.gps.rescan-interval:PT12H}")
    public void scheduledRescan() {
        if (fileIndexerImpl == null) {
            log.debug("Scheduled rescan skipped — indexer not yet started for GPS");
            return;
        }
        log.info("Scheduled rescan triggered for GPS index");
        fileIndexerImpl.rescan();
    }

}
