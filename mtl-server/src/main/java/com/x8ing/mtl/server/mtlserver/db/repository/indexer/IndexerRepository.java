package com.x8ing.mtl.server.mtlserver.db.repository.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndexerRepository extends JpaRepository<IndexedFile, Long> {


    Optional<IndexedFile> findByIndexAndNameAndPath(String index, String fileName, String filePath);


    List<IndexedFile> findByIndexAndIndexUpdateDateBefore(String index, Date cutoffDate);

    List<IndexedFile> findByIndex(String index);

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

    /**
     * Returns (index, indexerStatus, count) grouped rows for building a status overview.
     */
    @Query("SELECT i.index, i.indexerStatus, COUNT(i) FROM IndexedFile i GROUP BY i.index, i.indexerStatus")
    List<Object[]> countGroupedByIndexAndStatus();

}
