package com.x8ing.mtl.server.mtlserver.db.repository.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndexerRepository extends JpaRepository<IndexedFile, Long> {

    long countByIndexAndIndexerStatusAndSourceFailure(String index, IndexedFile.IndexerStatus status, boolean sourceFailure);


    Optional<IndexedFile> findByIndexAndNameAndPath(String index, String fileName, String filePath);


    List<IndexedFile> findByIndexAndIndexUpdateDateBefore(String index, Date cutoffDate);

    List<IndexedFile> findByIndex(String index);

    String SCAN_ENTRY_SELECT = """
            SELECT new com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexedFileScanEntry(
                i.id, i.name, i.path, i.size, i.lastModifiedDate, i.indexerStatus, i.version)
            FROM IndexedFile i
            """;

    @Query(SCAN_ENTRY_SELECT + " WHERE i.index = :index AND i.path = :path AND i.name IN :names")
    List<IndexedFileScanEntry> findScanEntries(String index, String path, Collection<String> names);

    @Query(SCAN_ENTRY_SELECT + " WHERE i.index = :index AND i.id > :afterId ORDER BY i.id")
    List<IndexedFileScanEntry> findScanEntriesAfterId(String index, long afterId, Pageable pageable);

    @Query(SCAN_ENTRY_SELECT + " WHERE i.index = :index AND i.id > :afterId AND i.indexerStatus IN :statuses ORDER BY i.id")
    List<IndexedFileScanEntry> findPendingScanEntriesAfterId(String index, long afterId,
                                                          Collection<IndexedFile.IndexerStatus> statuses, Pageable pageable);

    /**
     * Count files by index and specific status.
     * Used to check if indexing is complete before starting dependent services.
     */
    long countByIndexAndIndexerStatus(String index, IndexedFile.IndexerStatus status);

    /**
     * Count files by index matching any of the given statuses.
     * Used to check for both SCHEDULED and PROCESSING files before starting dependent services.
     */
    long countByIndexAndIndexerStatusIn(String index, Collection<IndexedFile.IndexerStatus> statuses);

    /**
     * Find all files by index and specific status.
     * Used to re-process files left in SCHEDULED state after server restart.
     */
    List<IndexedFile> findByIndexAndIndexerStatus(String index, IndexedFile.IndexerStatus status);

    Page<IndexedFile> findByIndexAndIndexerStatus(String index, IndexedFile.IndexerStatus status, Pageable pageable);

    /**
     * Returns (index, indexerStatus, count) grouped rows for building a status overview.
     */
    @Query("SELECT i.index, i.indexerStatus, COUNT(i) FROM IndexedFile i GROUP BY i.index, i.indexerStatus")
    List<Object[]> countGroupedByIndexAndStatus();

}
