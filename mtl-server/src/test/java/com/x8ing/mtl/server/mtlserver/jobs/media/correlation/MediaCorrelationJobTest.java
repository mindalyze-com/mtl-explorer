package com.x8ing.mtl.server.mtlserver.jobs.media.correlation;

import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class MediaCorrelationJobTest {

    private final MediaCorrelationRepository repository = mock(MediaCorrelationRepository.class);
    private final MediaCorrelationAtomicWorker worker = mock(MediaCorrelationAtomicWorker.class);
    private final IndexerStatusService indexerStatusService = mock(IndexerStatusService.class);
    private final MediaCorrelationJob job = new MediaCorrelationJob(repository, worker, indexerStatusService);

    @Test
    void isolatesFailedBatchAndDefersOnlyPoisonItem() {
        MediaCorrelationBatchException batchFailure = new MediaCorrelationBatchException(
                List.of(7L, 8L),
                new IllegalStateException("batch failed"));
        when(worker.expandTrackWork(MediaCorrelationJob.TRACK_BATCH_SIZE)).thenReturn(0);
        when(worker.rebuildMediaBatch(MediaCorrelationJob.MEDIA_BATCH_SIZE, MediaCorrelationJob.ALGORITHM_VERSION))
                .thenThrow(batchFailure)
                .thenReturn(0);
        when(worker.rebuildMediaId(7L, MediaCorrelationJob.ALGORITHM_VERSION)).thenReturn(true);
        when(worker.rebuildMediaId(8L, MediaCorrelationJob.ALGORITHM_VERSION))
                .thenThrow(new IllegalStateException("invalid correlation"));

        job.run();

        verify(worker).rebuildMediaId(7L, MediaCorrelationJob.ALGORITHM_VERSION);
        verify(worker).rebuildMediaId(8L, MediaCorrelationJob.ALGORITHM_VERSION);
        verify(worker).deferMediaFailure(
                8L,
                "invalid correlation",
                MediaCorrelationJob.FAILED_MEDIA_RETRY_DELAY_SECONDS);
    }

    @Test
    void waitsForGpsIndexingToSettle() {
        when(indexerStatusService.hasIndexPendingWork(GPXDirectoryWatcherService.INDEX_GPS))
                .thenReturn(true);

        job.run();

        verifyNoInteractions(repository, worker);
    }
}
