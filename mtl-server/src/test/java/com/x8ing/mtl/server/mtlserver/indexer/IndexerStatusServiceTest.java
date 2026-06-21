package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndexerStatusServiceTest {

    @Test
    void includesKnownIndexesWhenNoIndexedFilesExist() {
        IndexerRepository repository = mock(IndexerRepository.class);
        when(repository.countGroupedByIndexAndStatus()).thenReturn(List.of());

        List<IndexerStatusService.IndexSummaryDto> summaries = new IndexerStatusService(repository).getIndexSummaries();

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

        List<IndexerStatusService.IndexSummaryDto> summaries = new IndexerStatusService(repository).getIndexSummaries();

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
}
