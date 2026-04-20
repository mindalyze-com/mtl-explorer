package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class IndexerStatusService {

    private final IndexerRepository indexerRepository;

    public IndexerStatusService(IndexerRepository indexerRepository) {
        this.indexerRepository = indexerRepository;
    }

    /**
     * Returns true if the given index still has files in SCHEDULED or PROCESSING state,
     * meaning indexing work is pending and dependent jobs should wait.
     */
    public boolean hasIndexPendingWork(String index) {
        return indexerRepository.countByIndexAndIndexerStatusIn(
                index,
                List.of(IndexedFile.IndexerStatus.SCHEDULED, IndexedFile.IndexerStatus.PROCESSING)
        ) > 0;
    }

    public record IndexSummaryDto(
            String index,
            long total,
            long pending,
            long completed,
            long failed,
            long removed,
            long excluded,
            int progressPercent
    ) {
    }

    public List<IndexSummaryDto> getIndexSummaries() {
        List<Object[]> rows = indexerRepository.countGroupedByIndexAndStatus();
        Map<String, Map<IndexedFile.IndexerStatus, Long>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String idx = (String) row[0];
            IndexedFile.IndexerStatus status = (IndexedFile.IndexerStatus) row[1];
            long count = (Long) row[2];
            grouped.computeIfAbsent(idx, k -> new EnumMap<>(IndexedFile.IndexerStatus.class))
                    .put(status, count);
        }
        List<IndexSummaryDto> result = new ArrayList<>();
        for (Map.Entry<String, Map<IndexedFile.IndexerStatus, Long>> entry : grouped.entrySet()) {
            String idx = entry.getKey();
            Map<IndexedFile.IndexerStatus, Long> counts = entry.getValue();
            long pending = counts.getOrDefault(IndexedFile.IndexerStatus.SCHEDULED, 0L)
                           + counts.getOrDefault(IndexedFile.IndexerStatus.PROCESSING, 0L);
            long completed = counts.getOrDefault(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS, 0L);
            long failed = counts.getOrDefault(IndexedFile.IndexerStatus.FAILED, 0L);
            long removed = counts.getOrDefault(IndexedFile.IndexerStatus.REMOVED, 0L);
            long excluded = counts.getOrDefault(IndexedFile.IndexerStatus.EXCLUDED, 0L);
            long total = pending + completed + failed + removed + excluded;
            int progress = total > 0 ? (int) (completed * 100L / total) : 100;
            result.add(new IndexSummaryDto(idx, total, pending, completed, failed, removed, excluded, progress));
        }
        return result;
    }

}

