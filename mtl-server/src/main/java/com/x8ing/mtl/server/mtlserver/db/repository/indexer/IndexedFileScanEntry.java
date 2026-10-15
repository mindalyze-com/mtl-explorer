package com.x8ing.mtl.server.mtlserver.db.repository.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;

import java.util.Date;

/** Only the identity and revision fields needed by bounded directory scans. */
public record IndexedFileScanEntry(Long id, String name, String path, Long size, Date lastModifiedDate,
                                   IndexedFile.IndexerStatus indexerStatus, Long version) {
}
