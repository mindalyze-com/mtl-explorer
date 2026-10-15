package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.IndexedFileLookup;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileRevision;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileProblemException;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileSkippedException;
import com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueIngestService;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceOperationLocks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * Dedicated worker bean for media file processing.
 * <p>
 * This bean is separate from MediaIndexerService to ensure that Spring's
 * transaction proxy is properly invoked when methods are called from executor threads.
 * Direct calls on 'this' bypass the proxy and break transaction management.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "MediaProcessing")
@JsonPropertyOrder({
        "indexerRepository",
        "mediaIndexer"
})
public class MediaProcessingWorker {

    private final IndexerRepository indexerRepository;
    private final TransactionTemplate transactionTemplate;
    private final SourceIssueIngestService sourceIssues;
    private final MediaIndexer mediaIndexer; // Already a @Service, injected automatically

    /**
     * Process a new or changed media file.
     *
     * @param index   the index name (e.g., "MEDIA")
     * @param fileId  the indexed file ID
     * @param changed true if file was changed, false if new
     */
    @Transactional(propagation = Propagation.NEVER)
    public void processCreateOrChange(String index, long fileId, boolean changed) {
        try (var lease = SourceOperationLocks.forOperation(fileId)) {
            IndexedFile file = IndexedFileLookup.findOrLog(indexerRepository, fileId, log, "Media process");
            if (file == null) return;
            SourceFileRevision revision = SourceFileRevision.read(file);
            String contents = sourceIssues.contentRevision(file);
            revision.requireUnchanged();
            if (revision.size() == 0) {
                sourceIssues.empty(fileId, contents);
                throw new SourceFileSkippedException();
            }
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    IndexedFile current = IndexedFileLookup.findOrLog(indexerRepository, fileId, log, "Media import");
                    if (changed) mediaIndexer.refreshFile(current);
                    else mediaIndexer.indexFile(current);
                    revision.requireUnchanged();
                    sourceIssues.mediaCompleted(fileId, contents);
                });
            } catch (RuntimeException failure) {
                revision.requireUnchanged();
                log.warn("Media import failed: sourceId={} type={} message={}", fileId, failure.getClass().getSimpleName(), failure.getMessage());
                if (failure instanceof SourceFileProblemException)
                    sourceIssues.mediaFailed(fileId, contents, failure.getMessage());
                throw failure;
            }
        }
    }

    /**
     * Process a deleted media file.
     *
     * @param index  the index name (e.g., "MEDIA")
     * @param fileId the indexed file ID
     */
    @Transactional
    public void processDelete(String index, long fileId) {
        try (var lease = SourceOperationLocks.forTransaction(fileId)) {
            IndexedFile f = IndexedFileLookup.findOrLog(indexerRepository, fileId, log, "Media delete");
            if (f == null) return;
            SourceFileRevision.requireAbsent(f);
            mediaIndexer.deleteFilesForIndexedFile(f);
            SourceFileRevision.requireAbsent(f);
            sourceIssues.removed(fileId);
        }
    }
}
