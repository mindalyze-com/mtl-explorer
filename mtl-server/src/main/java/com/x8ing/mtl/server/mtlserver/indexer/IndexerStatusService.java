package com.x8ing.mtl.server.mtlserver.indexer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.sourceissues.IssueAccount;
import com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels;
import com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService.INDEX_GPS;
import static com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService.INDEX_MEDIA;

@Service
@JsonPropertyOrder({
        "indexerRepository",
        "sourceReviewRepository",
        "scanStates",
        "discoveryStates"
})
public class IndexerStatusService {

    private static final List<String> DEFAULT_INDEX_NAMES = List.of(INDEX_GPS, INDEX_MEDIA);

    private final IndexerRepository indexerRepository;
    private final SourceReviewRepository sourceReviewRepository;
    private final SourceIssueRepository sourceIssues;
    private final Map<String, BooleanSupplier> scanStates = new ConcurrentHashMap<>();
    private final Map<String, BooleanSupplier> discoveryStates = new ConcurrentHashMap<>();

    public IndexerStatusService(IndexerRepository indexerRepository, SourceReviewRepository sourceReviewRepository, SourceIssueRepository sourceIssues) {
        this.indexerRepository = indexerRepository;
        this.sourceReviewRepository = sourceReviewRepository;
        this.sourceIssues = sourceIssues;
    }

    /**
     * Includes active discovery/dispatch as well as committed SCHEDULED/PROCESSING/RESCHEDULED rows,
     * so dependent jobs cannot start in a gap between discovery commits.
     */
    public boolean hasIndexPendingWork(String index) {
        BooleanSupplier scanState = scanStates.get(index);
        return scanState != null && scanState.getAsBoolean() || indexerRepository.countByIndexAndIndexerStatusIn(
                index,
                List.of(IndexedFile.IndexerStatus.SCHEDULED, IndexedFile.IndexerStatus.PROCESSING, IndexedFile.IndexerStatus.RESCHEDULED)
        ) > 0;
    }

    public void registerScanState(String index, BooleanSupplier scanState) {
        scanStates.put(index, scanState);
    }

    public void registerDiscoveryState(String index, BooleanSupplier discoveryState) {
        discoveryStates.put(index, discoveryState);
    }

    /**
     * Live scan state includes startup before either indexer has registered.
     */
    public boolean isDemoIndexingBusy() {
        return DEFAULT_INDEX_NAMES.stream().anyMatch(index -> {
            BooleanSupplier scanState = scanStates.get(index);
            return scanState == null || scanState.getAsBoolean() || hasIndexPendingWork(index);
        });
    }

    @JsonPropertyOrder({
            "index",
            "total",
            "pending",
            "completed",
            "failed",
            "removed",
            "excluded",
            "progressPercent",
            "discoveryInProgress",
            "skipped",
            "pendingIssueFileCount",
            "acknowledgedIssueFileCount",
            "operationalFailed"
    })
    public record IndexSummaryDto(
            String index,
            long total,
            long pending,
            long completed,
            long failed,
            long removed,
            long excluded,
            int progressPercent,
            boolean discoveryInProgress,
            long skipped,
            long pendingIssueFileCount,
            long acknowledgedIssueFileCount,
            long operationalFailed
    ) {
    }

    @JsonPropertyOrder({"id", "name", "path", "message", "updatedAt"})
    public record FileFailureDto(Long id, String name, String path, String message, Date updatedAt) {
    }

    @JsonPropertyOrder({"files", "total", "page", "size"})
    public record FileFailuresDto(List<FileFailureDto> files, long total, int page, int size) {
    }

    @org.springframework.transaction.annotation.Transactional
    public void recordUnreadableSource(long sourceId, String message) {
        var finding = new SourceIssueModels.Finding("READ", SourceIssueModels.Reason.SOURCE_UNREADABLE,
                SourceIssueModels.Severity.REVIEW, -1, SourceIssueRepository.hash("READ:SOURCE_UNREADABLE:REVIEW"), message, Map.of());
        sourceIssues.sync(sourceId, sourceIssues.unreadableRevision(sourceId), List.of(finding), Set.of("READ"));
    }

    public FileFailuresDto getFileFailures(String index, int page, int size) {
        return getFileOutcomes(index, page, size, IndexedFile.IndexerStatus.FAILED);
    }

    public FileFailuresDto getFileOutcomes(String index, int page, int size, IndexedFile.IndexerStatus outcome) {
        var failures = indexerRepository.findByIndexAndIndexerStatus(index, outcome,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("indexUpdateDate"), Sort.Order.asc("id"))));
        Map<Long, String> diagnosisMessages = INDEX_GPS.equals(index) && outcome == IndexedFile.IndexerStatus.FAILED
                ? sourceReviewRepository.failureMessages(failures.getContent().stream().map(IndexedFile::getId).toList())
                : Map.of();
        var files = failures.getContent().stream().map(file -> new FileFailureDto(
                file.getId(), file.getName(), file.getPath(), file.getPendingOperation() == IndexedFile.Operation.DELETE
                ? file.getLastMessage() : diagnosisMessages.getOrDefault(file.getId(), file.getLastMessage()), file.getIndexUpdateDate()
        )).toList();
        return new FileFailuresDto(files, failures.getTotalElements(), page, size);
    }

    public List<IndexSummaryDto> getIndexSummaries() {
        return getIndexSummaries(IssueAccount.current());
    }

    public List<IndexSummaryDto> getIndexSummaries(String account) {
        var issueSummary = sourceIssues.summary(account);
        Map<String, SourceIssueModels.SourceIssueIndexSummary> issueCounts = new HashMap<>();
        issueSummary.indexes().forEach(summary -> issueCounts.put(summary.index(), summary));
        List<Object[]> rows = indexerRepository.countGroupedByIndexAndStatus();
        Map<String, Map<IndexedFile.IndexerStatus, Long>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String idx = (String) row[0];
            IndexedFile.IndexerStatus status = (IndexedFile.IndexerStatus) row[1];
            long count = (Long) row[2];
            grouped.computeIfAbsent(idx, k -> new EnumMap<>(IndexedFile.IndexerStatus.class))
                    .put(status, count);
        }
        for (String defaultIndexName : DEFAULT_INDEX_NAMES) {
            grouped.computeIfAbsent(defaultIndexName, k -> new EnumMap<>(IndexedFile.IndexerStatus.class));
        }
        List<IndexSummaryDto> result = new ArrayList<>();
        for (Map.Entry<String, Map<IndexedFile.IndexerStatus, Long>> entry : grouped.entrySet()) {
            String idx = entry.getKey();
            Map<IndexedFile.IndexerStatus, Long> counts = entry.getValue();
            long pending = counts.getOrDefault(IndexedFile.IndexerStatus.SCHEDULED, 0L)
                           + counts.getOrDefault(IndexedFile.IndexerStatus.PROCESSING, 0L)
                           + counts.getOrDefault(IndexedFile.IndexerStatus.RESCHEDULED, 0L);
            long completed = counts.getOrDefault(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS, 0L);
            long failed = counts.getOrDefault(IndexedFile.IndexerStatus.FAILED, 0L);
            long removed = counts.getOrDefault(IndexedFile.IndexerStatus.REMOVED, 0L);
            long excluded = counts.getOrDefault(IndexedFile.IndexerStatus.EXCLUDED, 0L);
            long skipped = counts.getOrDefault(IndexedFile.IndexerStatus.SKIPPED, 0L);
            long total = pending + completed + failed + skipped + removed + excluded;
            long finished = completed + failed + skipped + removed + excluded;
            int progress = total > 0 ? (int) (finished * 100L / total) : 100;
            BooleanSupplier discoveryState = discoveryStates.get(idx);
            boolean discovering = discoveryState != null && discoveryState.getAsBoolean();
            result.add(new IndexSummaryDto(idx, total, pending, completed, failed, removed, excluded, progress, discovering,
                    skipped, issueCounts.containsKey(idx) ? issueCounts.get(idx).pendingFileCount() : 0,
                    issueCounts.containsKey(idx) ? issueCounts.get(idx).acknowledgedFileCount() : 0,
                    indexerRepository.countByIndexAndIndexerStatusAndSourceFailure(idx, IndexedFile.IndexerStatus.FAILED, false)));
        }
        return result;
    }

}
