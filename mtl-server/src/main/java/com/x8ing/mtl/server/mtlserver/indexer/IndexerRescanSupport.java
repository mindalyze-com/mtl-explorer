package com.x8ing.mtl.server.mtlserver.indexer;

import org.slf4j.Logger;

public final class IndexerRescanSupport {

    private IndexerRescanSupport() {
    }

    public static void scheduledRescan(FileIndexerImpl indexer, Logger log, String indexName) {
        if (indexer == null) {
            log.debug("Scheduled rescan skipped — indexer not yet started for {}", indexName);
            return;
        }
        log.info("Scheduled rescan triggered for {} index", indexName);
        indexer.rescan();
    }

    public static FileIndexerImpl.RescanRequestStatus requestRescan(FileIndexerImpl indexer,
                                                                   Logger log,
                                                                   String indexName) {
        if (indexer == null) {
            log.warn("Manual rescan requested before {} indexer startup completed", indexName);
            return FileIndexerImpl.RescanRequestStatus.NOT_RUNNING;
        }
        log.info("Manual rescan requested for {} index", indexName);
        return indexer.rescan();
    }
}
