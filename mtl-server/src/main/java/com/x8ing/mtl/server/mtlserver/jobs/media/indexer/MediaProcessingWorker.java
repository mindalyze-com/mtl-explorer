package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
public class MediaProcessingWorker {

    private final IndexerRepository indexerRepository;
    private final MediaIndexer mediaIndexer; // Already a @Service, injected automatically

    /**
     * Process a new or changed media file.
     *
     * @param index   the index name (e.g., "MEDIA")
     * @param fileId  the indexed file ID
     * @param changed true if file was changed, false if new
     */
    @Transactional
    public void processCreateOrChange(String index, long fileId, boolean changed) {
        IndexedFile f = indexerRepository.findById(fileId).orElse(null);
        if (f == null) {
            log.warn("Media process: fileId={} disappeared", fileId);
            return;
        }
        // Domain-only: let exceptions bubble so observer completion can mark success/failure
        mediaIndexer.indexFile(f);
    }

    /**
     * Process a deleted media file.
     *
     * @param index  the index name (e.g., "MEDIA")
     * @param fileId the indexed file ID
     */
    @Transactional
    public void processDelete(String index, long fileId) {
        IndexedFile f = indexerRepository.findById(fileId).orElse(null);
        if (f == null) {
            log.warn("Media delete: fileId={} disappeared", fileId);
            return;
        }
        // Domain-only: no indexer state writes here; throw to signal failure to observer
        // TODO: Implement media deletion logic when needed (e.g., remove derived entities)
    }
}
