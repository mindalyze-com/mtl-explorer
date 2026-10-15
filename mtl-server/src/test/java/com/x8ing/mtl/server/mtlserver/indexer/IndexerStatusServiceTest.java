package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IndexerStatusServiceTest {

    private static com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository issueRepository() {
        var issues = mock(com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository.class);
        when(issues.summary(anyString())).thenReturn(new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.SourceIssueSummary(0, 0, List.of()));
        return issues;
    }

    @Test
    void cleanupFailureIsNotReplacedByAnOlderSourceDiagnosis() {
        var repository = mock(IndexerRepository.class);
        var reviews = mock(SourceReviewRepository.class);
        var paging = PageRequest.of(0, 25, Sort.by(Sort.Order.desc("indexUpdateDate"), Sort.Order.asc("id")));
        var file = new IndexedFile();
        file.setId(1L);
        file.setPendingOperation(IndexedFile.Operation.DELETE);
        file.setLastMessage("Database cleanup failed: synthetic transaction failure");
        when(repository.findByIndexAndIndexerStatus("GPS", IndexedFile.IndexerStatus.FAILED, paging))
                .thenReturn(new PageImpl<>(List.of(file), paging, 1));
        when(reviews.failureMessages(List.of(1L))).thenReturn(java.util.Map.of(1L, "Older source parse failure"));

        var result = new IndexerStatusService(repository, reviews, issueRepository()).getFileFailures("GPS", 0, 25);

        assertThat(result.files().getFirst().message()).isEqualTo(file.getLastMessage());
    }

    @Test
    void demoWaitsForBothScanRegistrationsAndLiveScans() {
        var service = new IndexerStatusService(mock(IndexerRepository.class), mock(SourceReviewRepository.class), issueRepository());
        assertThat(service.isDemoIndexingBusy()).isTrue();
        service.registerScanState("GPS", () -> false);
        assertThat(service.isDemoIndexingBusy()).isTrue();
        service.registerScanState("MEDIA", () -> true);
        assertThat(service.isDemoIndexingBusy()).isTrue();
        service.registerScanState("MEDIA", () -> false);
        assertThat(service.isDemoIndexingBusy()).isFalse();
        service.registerScanState("GPS", () -> true);
        assertThat(service.isDemoIndexingBusy()).isTrue();
    }

    @Test
    void demoWaitsForScheduledOrProcessingFilesInEitherIndexButNotFailedFiles() {
        var repository = mock(IndexerRepository.class);
        var service = new IndexerStatusService(repository, mock(SourceReviewRepository.class), issueRepository());
        var pending = List.of(IndexedFile.IndexerStatus.SCHEDULED, IndexedFile.IndexerStatus.PROCESSING, IndexedFile.IndexerStatus.RESCHEDULED);
        service.registerScanState("GPS", () -> false);
        service.registerScanState("MEDIA", () -> false);
        for (String index : List.of("GPS", "MEDIA")) {
            when(repository.countByIndexAndIndexerStatusIn(index, pending)).thenReturn(1L);
            assertThat(service.isDemoIndexingBusy()).isTrue();
            when(repository.countByIndexAndIndexerStatusIn(index, pending)).thenReturn(0L);
        }
        assertThat(service.isDemoIndexingBusy()).isFalse();
        verify(repository, never()).countByIndexAndIndexerStatusIn("GPS", List.of(IndexedFile.IndexerStatus.FAILED));
        verify(repository, never()).countByIndexAndIndexerStatusIn("MEDIA", List.of(IndexedFile.IndexerStatus.FAILED));
    }

    @Test
    void activeDiscoveryIsVisibleAndBlocksDependentJobsBeforeAnyRowsExist() {
        var repository = mock(IndexerRepository.class);
        var service = new IndexerStatusService(repository, mock(SourceReviewRepository.class), issueRepository());
        service.registerScanState("GPS", () -> true);
        service.registerDiscoveryState("GPS", () -> true);
        assertThat(service.hasIndexPendingWork("GPS")).isTrue();
        assertThat(service.getIndexSummaries().getFirst().discoveryInProgress()).isTrue();
        service.registerScanState("GPS", () -> false);
        service.registerDiscoveryState("GPS", () -> false);
        assertThat(service.hasIndexPendingWork("GPS")).isFalse();
        assertThat(service.getIndexSummaries().getFirst().discoveryInProgress()).isFalse();
    }

    @Test
    void rescheduledFilesRemainPendingUntilTheirNewRevisionCompletes() {
        var repository = mock(IndexerRepository.class);
        when(repository.countGroupedByIndexAndStatus()).thenReturn(List.of(
                row("GPS", IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS, 3L),
                row("GPS", IndexedFile.IndexerStatus.RESCHEDULED, 1L)));
        var summary = new IndexerStatusService(repository, mock(SourceReviewRepository.class), issueRepository()).getIndexSummaries().getFirst();
        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.pending()).isEqualTo(1);
        assertThat(summary.progressPercent()).isEqualTo(75);
    }

    @Test
    void includesKnownIndexesWhenNoIndexedFilesExist() {
        IndexerRepository repository = mock(IndexerRepository.class);
        when(repository.countGroupedByIndexAndStatus()).thenReturn(List.of());

        List<IndexerStatusService.IndexSummaryDto> summaries = new IndexerStatusService(repository, mock(SourceReviewRepository.class), issueRepository()).getIndexSummaries();

        assertThat(summaries)
                .extracting(IndexerStatusService.IndexSummaryDto::index)
                .containsExactly("GPS", "MEDIA");
        assertThat(summaries)
                .allSatisfy(summary -> {
                    assertThat(summary.total()).isZero();
                    assertThat(summary.progressPercent()).isEqualTo(100);
                });
    }

    @Test
    void preservesRepositoryIndexesAndAppendsMissingKnownIndexes() {
        IndexerRepository repository = mock(IndexerRepository.class);
        when(repository.countGroupedByIndexAndStatus()).thenReturn(List.of(
                row("GPS", IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS, 3L),
                row("GPS", IndexedFile.IndexerStatus.SCHEDULED, 1L),
                row("CUSTOM", IndexedFile.IndexerStatus.FAILED, 2L)
        ));

        List<IndexerStatusService.IndexSummaryDto> summaries = new IndexerStatusService(repository, mock(SourceReviewRepository.class), issueRepository()).getIndexSummaries();

        assertThat(summaries)
                .extracting(IndexerStatusService.IndexSummaryDto::index)
                .containsExactly("GPS", "CUSTOM", "MEDIA");
        assertThat(summaries.getFirst().total()).isEqualTo(4);
        assertThat(summaries.getFirst().pending()).isEqualTo(1);
        assertThat(summaries.getFirst().completed()).isEqualTo(3);
    }

    private static Object[] row(String index, IndexedFile.IndexerStatus status, long count) {
        return new Object[]{index, status, count};
    }

    @Test
    void completedScanIncludesUnreadableRemovedAndExcludedFilesInProgress() {
        var repository = mock(IndexerRepository.class);
        when(repository.countGroupedByIndexAndStatus()).thenReturn(List.of(
                row("MEDIA", IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS, 7),
                row("MEDIA", IndexedFile.IndexerStatus.FAILED, 1),
                row("MEDIA", IndexedFile.IndexerStatus.REMOVED, 1),
                row("MEDIA", IndexedFile.IndexerStatus.EXCLUDED, 1)));

        var summary = new IndexerStatusService(repository, mock(SourceReviewRepository.class), issueRepository()).getIndexSummaries().getFirst();
        assertThat(summary.progressPercent()).isEqualTo(100);
        assertThat(summary.completed()).isEqualTo(7);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.pendingIssueFileCount()).isZero();
    }

    @Test
    void pendingWorkStillReducesProgressEvenWhenOtherFilesFailed() {
        var repository = mock(IndexerRepository.class);
        when(repository.countGroupedByIndexAndStatus()).thenReturn(List.of(
                row("MEDIA", IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS, 2),
                row("MEDIA", IndexedFile.IndexerStatus.FAILED, 1),
                row("MEDIA", IndexedFile.IndexerStatus.PROCESSING, 1)));
        var summary = new IndexerStatusService(repository, mock(SourceReviewRepository.class), issueRepository()).getIndexSummaries().getFirst();
        assertThat(summary.progressPercent()).isEqualTo(75);
        assertThat(summary.pending()).isEqualTo(1);
    }

    @Test
    void retrievesOnlyFailedFilesInTheRequestedIndexWithStablePagingAndSavedDiagnostics() {
        var repository = mock(IndexerRepository.class);
        var paging = PageRequest.of(1, 25, Sort.by(Sort.Order.desc("indexUpdateDate"), Sort.Order.asc("id")));
        var file = new IndexedFile();
        file.setId(42L);
        file.setName("broken.gpx");
        file.setPath("recordings/broken.gpx");
        file.setFullPath("/private/imports/recordings/broken.gpx");
        file.setLastMessage("No usable GPS observations");
        file.setIndexUpdateDate(new java.util.Date(1234));
        when(repository.findByIndexAndIndexerStatus("GPS", IndexedFile.IndexerStatus.FAILED, paging))
                .thenReturn(new PageImpl<>(List.of(file), paging, 26));

        var result = new IndexerStatusService(repository, mock(SourceReviewRepository.class), issueRepository()).getFileFailures("GPS", 1, 25);

        assertThat(result.total()).isEqualTo(26);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(25);
        assertThat(result.files()).containsExactly(new IndexerStatusService.FileFailureDto(
                42L, "broken.gpx", "recordings/broken.gpx", "No usable GPS observations", new java.util.Date(1234)));
        verify(repository).findByIndexAndIndexerStatus("GPS", IndexedFile.IndexerStatus.FAILED, paging);
    }

    @Test
    void usesSpecificSavedSourceDiagnosisAndFallsBackToTheIndexerError() {
        var repository = mock(IndexerRepository.class);
        var reviews = mock(SourceReviewRepository.class);
        var paging = PageRequest.of(0, 25, Sort.by(Sort.Order.desc("indexUpdateDate"), Sort.Order.asc("id")));
        var parsed = new IndexedFile();
        parsed.setId(1L);
        parsed.setLastMessage("Source could not be parsed; the diagnosis is available in Track review.");
        var unreadable = new IndexedFile();
        unreadable.setId(2L);
        unreadable.setLastMessage("Permission denied");
        when(repository.findByIndexAndIndexerStatus("GPS", IndexedFile.IndexerStatus.FAILED, paging))
                .thenReturn(new PageImpl<>(List.of(parsed, unreadable), paging, 2));
        when(reviews.failureMessages(List.of(1L, 2L))).thenReturn(java.util.Map.of(1L, "Invalid GPX XML at line 12"));

        var result = new IndexerStatusService(repository, reviews, issueRepository()).getFileFailures("GPS", 0, 25);

        assertThat(result.files()).extracting(IndexerStatusService.FileFailureDto::message)
                .containsExactly("Invalid GPX XML at line 12", "Permission denied");
        verify(reviews).failureMessages(List.of(1L, 2L));
    }
}
