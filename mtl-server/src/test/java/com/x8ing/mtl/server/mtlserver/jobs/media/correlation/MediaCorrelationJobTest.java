package com.x8ing.mtl.server.mtlserver.jobs.media.correlation;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MediaCorrelationJobTest {

    private final MediaCorrelationRepository repository = mock(MediaCorrelationRepository.class);
    private final MediaCorrelationAtomicWorker worker = mock(MediaCorrelationAtomicWorker.class);
    private final IndexerStatusService indexerStatusService = mock(IndexerStatusService.class);
    private final ShutdownSignal signal = new ShutdownSignal();
    private final MediaCorrelationJob job = new MediaCorrelationJob(repository, worker, indexerStatusService, signal, directRunner());

    @Test
    void shutdownBetweenTransactionsStopsBeforeMediaRebuild() {
        when(worker.expandTrackWork(MediaCorrelationJob.TRACK_BATCH_SIZE)).thenAnswer(call -> {
            signal.onContextClosed();
            return 1;
        });
        job.run();
        job.run();
        verify(worker).expandTrackWork(MediaCorrelationJob.TRACK_BATCH_SIZE);
        verify(worker, never()).rebuildMediaBatch(anyInt(), anyInt());
    }

    @Test
    void shutdownDuringFallbackLeavesRemainingMediaPendingInsteadOfDeferringThem() {
        when(worker.rebuildMediaBatch(anyInt(), anyInt())).thenThrow(
                new MediaCorrelationBatchException(List.of(7L, 8L), new IllegalStateException("synthetic failure")));
        when(worker.rebuildMediaId(7L, MediaCorrelationJob.ALGORITHM_VERSION)).thenAnswer(call -> {
            signal.onContextClosed();
            return true;
        });
        job.run();
        verify(worker).rebuildMediaId(7L, MediaCorrelationJob.ALGORITHM_VERSION);
        verify(worker, never()).rebuildMediaId(8L, MediaCorrelationJob.ALGORITHM_VERSION);
        verify(worker, never()).deferMediaFailure(anyLong(), anyString(), anyInt());
        verify(worker, times(1)).expandTrackWork(anyInt());
    }

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

    @Test
    void deferredMediaDoesNotLookRunningDuringAnEmptyCheck() {
        var runner = new BackgroundJobRunner(mock(TaskScheduler.class), signal);
        var reported = new MediaCorrelationJob(repository, worker, indexerStatusService, signal, runner);
        var context = new ProcessingContext(indexerStatusService, runner, new ResourceIntensiveJobGuard(), false);
        when(repository.processingCounts(MediaCorrelationJob.ALGORITHM_VERSION)).thenReturn(
                new MediaCorrelationRepository.ProcessingCounts(1, 0, 1, Instant.now().plusSeconds(60)));
        when(worker.expandTrackWork(anyInt())).thenAnswer(call -> {
            assertThat(reported.processingStatus(context).state()).isEqualTo(ProcessingDetails.State.RETRYING);
            assertThat(runner.isExecuting(reported.jobId())).isFalse();
            return 0;
        });
        reported.run();

        when(repository.hasReadyWork()).thenReturn(true);
        when(worker.expandTrackWork(anyInt())).thenAnswer(call -> {
            assertThat(reported.processingStatus(context).state()).isEqualTo(ProcessingDetails.State.RUNNING);
            return 0;
        });
        reported.run();
        assertThat(runner.isExecuting(reported.jobId())).isFalse();
    }
}
