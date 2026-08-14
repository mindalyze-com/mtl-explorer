package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import org.slf4j.Logger;

public final class IndexedFileLookup {

    private IndexedFileLookup() {
    }

    public static IndexedFile findOrLog(IndexerRepository repository,
                                        long fileId,
                                        Logger log,
                                        String operation) {
        IndexedFile file = repository.findById(fileId).orElse(null);
        if (file == null) {
            log.warn("{}: fileId={} disappeared", operation, fileId);
        }
        return file;
    }
}
