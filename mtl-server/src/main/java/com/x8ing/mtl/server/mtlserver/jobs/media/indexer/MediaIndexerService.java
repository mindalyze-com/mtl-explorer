/* jscpd:ignore-start -- Spring service imports and annotations; indexing behavior is shared. */
package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

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
        "mediaWatchDirectory",
        "changeDetectionStrategy",
        "liveWatchEnabled",
        "workerThreads",
        "fileIndexerImpl",
        "indexerRepository",
        "processingWorker",
        "txManager"
})
/* jscpd:ignore-end */
public class MediaIndexerService {

    public static final String INDEX_MEDIA = "MEDIA";

    @Value("${mtl.media-watch-directory}")
    private String mediaWatchDirectory;

    @Value("${mtl.indexer.change-detection-strategy:SIZE_AND_MTIME}")
    private String changeDetectionStrategy;

    @Value("${mtl.indexer.media.live-watch-enabled:false}")
    private boolean liveWatchEnabled;

    @Value("${mtl.indexer.worker-threads:2}")
    private int workerThreads;

    private volatile FileIndexerImpl fileIndexerImpl;

    private final IndexerRepository indexerRepository;

    private final MediaProcessingWorker processingWorker;

    private final PlatformTransactionManager txManager;

    public MediaIndexerService(IndexerRepository indexerRepository,
                               MediaProcessingWorker processingWorker,
                               PlatformTransactionManager txManager) {
        this.indexerRepository = indexerRepository;
        this.processingWorker = processingWorker;
        this.txManager = txManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void findMedia() {
        log.info("Start media indexing");

        FileIndexer fileIndexer = new FileIndexer(txManager, workerThreads);
        Path mediaWatchDirectoryPath = Paths.get(mediaWatchDirectory);

        FileIndexerObserver observer = new ProcessingFileIndexerObserver(
                log, "Media", processingWorker::processCreateOrChange, processingWorker::processDelete);

        FileIndexerImpl.ChangeDetectionStrategy strategy =
                FileIndexerImpl.ChangeDetectionStrategy.valueOf(changeDetectionStrategy);
        PathMatcher includeMediaFiles = FileSystems.getDefault()
                .getPathMatcher("regex:" + SupportedMediaFormat.inclusionRegex());

        // Start indexing (non-blocking). This constructs a new FileIndexerImpl internally.
        this.fileIndexerImpl = fileIndexer.findAndIndex(
                INDEX_MEDIA,
                mediaWatchDirectoryPath,
                indexerRepository,
                observer,
                false,
                IndexerPathMatchers.mediaExclusions(),
                List.of(includeMediaFiles),
                strategy,
                liveWatchEnabled);
    }

    @Scheduled(fixedDelayString = "${mtl.indexer.media.rescan-interval:P7D}")
    public void scheduledRescan() {
        IndexerRescanSupport.scheduledRescan(fileIndexerImpl, log, INDEX_MEDIA);
    }

    public FileIndexerImpl.RescanRequestStatus requestRescan() {
        return IndexerRescanSupport.requestRescan(fileIndexerImpl, log, INDEX_MEDIA);
    }

}
