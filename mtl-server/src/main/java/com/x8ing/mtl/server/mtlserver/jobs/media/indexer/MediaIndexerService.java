package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

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
public class MediaIndexerService {

    private static final String INDEX_MEDIA = "MEDIA";

    @Value("${mtl.media-watch-directory}")
    private String mediaWatchDirectory;

    @Value("${mtl.indexer.change-detection-strategy:SIZE_ONLY}")
    private String changeDetectionStrategy;

    @Value("${mtl.indexer.media.live-watch-enabled:false}")
    private boolean liveWatchEnabled;

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

        FileIndexer fileIndexer = new FileIndexer(txManager);
        Path mediaWatchDirectoryPath = Paths.get(mediaWatchDirectory);

        // Exclusions: .git and .svn directories, .DS_Store files, @eaDir folders, and hidden files starting with dot
        PathMatcher gitDir = FileSystems.getDefault().getPathMatcher("regex:(?i).*\\.git(/.*)?");
        PathMatcher svnDir = FileSystems.getDefault().getPathMatcher("regex:(?i).*\\.svn(/.*)?");
        PathMatcher dsStore = FileSystems.getDefault().getPathMatcher("regex:.*\\.DS_Store$");
        PathMatcher eaDir = FileSystems.getDefault().getPathMatcher("regex:.*@eaDir(/.*)?");
        PathMatcher hiddenFiles = FileSystems.getDefault().getPathMatcher("regex:.*/\\.[^/]+$");

        // Use the worker bean; signal completion back to indexer
        FileIndexerObserver observer = new FileIndexerObserver() {
            @Override
            public void onNewFile(String index, long fileId, OnCompletion completion) {
                completion.started(fileId);
                try {
                    processingWorker.processCreateOrChange(index, fileId, false);
                    completion.success(fileId);
                } catch (Throwable t) {
                    log.error("Media processing failed for new fileId={}: {}", fileId, t.toString(), t);
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
                    log.error("Media delete failed for fileId={}: {}", fileId, t.toString(), t);
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
                    log.error("Media processing failed for changed fileId={}: {}", fileId, t.toString(), t);
                    completion.failed(fileId, safeMsg(t));
                    if (t instanceof Error) throw (Error) t;
                }
            }

            // Legacy no-arg overloads (not used by indexer now)
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

        // Start indexing (non-blocking). This constructs a new FileIndexerImpl internally.
        this.fileIndexerImpl = fileIndexer.findAndIndex(INDEX_MEDIA, mediaWatchDirectoryPath, indexerRepository, observer, false, List.of(gitDir, svnDir, dsStore, eaDir, hiddenFiles), null, strategy, liveWatchEnabled);
    }

    @Scheduled(fixedDelayString = "${mtl.indexer.media.rescan-interval:P7D}")
    public void scheduledRescan() {
        if (fileIndexerImpl == null) {
            log.debug("Scheduled rescan skipped — indexer not yet started for MEDIA");
            return;
        }
        log.info("Scheduled rescan triggered for MEDIA index");
        fileIndexerImpl.rescan();
    }

}
