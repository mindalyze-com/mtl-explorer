package com.x8ing.mtl.server.mtlserver.jobs.exploration;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExplorationScoreJobTest {
    @Test
    void sequentialShutdownStopsTheBatchAndDoesNotRequestAnImmediateRerun() {
        var worker = mock(ExplorationScoreAtomicWorker.class);
        var signal = new ShutdownSignal();
        var job = job(worker, signal, 1);
        doAnswer(call -> {
            signal.onContextClosed();
            return true;
        }).when(worker).processOne(1L);
        assertFalse(job.run());
        assertFalse(job.run());
        verify(worker).processOne(1L);
        verifyNoMoreInteractions(worker);
    }

    @Test
    void parallelShutdownSkipsQueuedTracksAndWaitsForActiveTransactions() throws Exception {
        var worker = mock(ExplorationScoreAtomicWorker.class);
        var signal = new ShutdownSignal();
        var job = job(worker, signal, 2);
        var active = new CountDownLatch(2);
        var finish = new CountDownLatch(1);
        doAnswer(call -> {
            active.countDown();
            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            return true;
        }).when(worker).processOne(anyLong());
        try (var runner = Executors.newSingleThreadExecutor()) {
            var result = runner.submit(job::run);
            try {
                assertThat(active.await(5, TimeUnit.SECONDS)).isTrue();
                signal.onContextClosed();
                assertThat(result.isDone()).isFalse();
            } finally {
                finish.countDown();
            }
            assertThat(result.get(5, TimeUnit.SECONDS)).isFalse();
        }
        verify(worker, times(2)).processOne(anyLong());
        verify(worker, never()).processOne(3L);
        verify(worker, never()).processOne(4L);
        verify(worker, never()).processOne(5L);
    }

    private ExplorationScoreJob job(ExplorationScoreAtomicWorker worker, ShutdownSignal signal, int workers) {
        var tracks = mock(GpsTrackRepository.class);
        List<GpsTrack> batch = LongStream.rangeClosed(1, 5).mapToObj(id -> {
            var track = new GpsTrack();
            track.setId(id);
            return track;
        }).toList();
        when(tracks.findByExplorationStatusIn(any(), any())).thenReturn(batch);
        return job(tracks, worker, signal, workers, batch.size());
    }

    private ExplorationScoreJob job(GpsTrackRepository tracks, ExplorationScoreAtomicWorker worker,
                                    ShutdownSignal signal, int workers, int batchSize) {
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var job = new ExplorationScoreJob(tracks, worker, mock(ConfigRepository.class), mock(IndexerStatusService.class),
                new TransactionTemplate(transactions), signal);
        ReflectionTestUtils.setField(job, "maxTracksPerRun", batchSize);
        ReflectionTestUtils.setField(job, "workerThreads", workers);
        return job;
    }

    @Test
    void waitsForReleasedDuplicatesToBeClassifiedBeforeRecalculatingHistory() {
        var tracks = mock(GpsTrackRepository.class);
        var worker = mock(ExplorationScoreAtomicWorker.class);
        var configs = mock(ConfigRepository.class);
        var indexing = mock(IndexerStatusService.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var job = new ExplorationScoreJob(tracks, worker, configs, indexing, new TransactionTemplate(transactions), new ShutdownSignal());
        ReflectionTestUtils.setField(job, "maxTracksPerRun", 20);
        when(tracks.existsByTrackSourceAndDuplicateStatus(GpsTrack.TRACK_SOURCE.IMPORTED,
                GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)).thenReturn(true, false);
        assertFalse(job.run());
        verifyNoInteractions(worker, configs);
        verify(tracks, never()).invalidateCalculatedTracksOverlappingScheduled();
        assertFalse(job.run());
        verify(tracks).invalidateCalculatedTracksOverlappingScheduled();
        verify(tracks).findByExplorationStatusIn(any(), any());
    }
    @Test
    void failedFullBatchWaitsForTheIdleRetryInsteadOfReprocessingTheSameTracks() {
        var worker = mock(ExplorationScoreAtomicWorker.class);
        var job = job(worker, new ShutdownSignal(), 1);
        when(worker.processOne(anyLong())).thenReturn(false);
        assertFalse(job.run());
        verify(worker, times(5)).processOne(anyLong());
    }

    @Test
    void completedFullBatchRequestsContinuation() {
        var worker = mock(ExplorationScoreAtomicWorker.class);
        var job = job(worker, new ShutdownSignal(), 1);
        when(worker.processOne(anyLong())).thenReturn(true);
        Assertions.assertTrue(job.run());
    }

    @Test
    void stopsContinuationWhenAParallelWorkerThrowsBeforeCommitting() {
        var worker = mock(ExplorationScoreAtomicWorker.class);
        var job = job(worker, new ShutdownSignal(), 2);
        when(worker.processOne(anyLong())).thenReturn(true);
        when(worker.processOne(1L)).thenThrow(new IllegalStateException("synthetic transaction rollback"));

        assertThat(job.run()).isFalse();
        verify(worker, times(5)).processOne(anyLong());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void keepsDrainingHealthyTracksWhenTheEarliestTrackAlwaysFails(int workers) {
        var tracks = mock(GpsTrackRepository.class);
        var worker = mock(ExplorationScoreAtomicWorker.class);
        var pending = new ArrayList<>(LongStream.rangeClosed(1, 12).mapToObj(id -> {
            var track = new GpsTrack();
            track.setId(id);
            return track;
        }).toList());
        when(tracks.findByExplorationStatusIn(any(), any())).thenAnswer(call -> {
            synchronized (pending) {
                return pending.stream().limit(5).toList();
            }
        });
        when(worker.processOne(anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0);
            if (id == 1) return false;
            synchronized (pending) {
                pending.removeIf(track -> track.getId() == id);
            }
            return true;
        });
        var job = job(tracks, worker, new ShutdownSignal(), workers, 5);

        assertThat(job.run()).isTrue();
        assertThat(job.run()).isTrue();
        assertThat(job.run()).isFalse();
        assertThat(pending).extracting(GpsTrack::getId).containsExactly(1L);
        assertThat(job.run()).isFalse();
        verify(worker, times(4)).processOne(1L);
        for (long id = 2; id <= 12; id++) verify(worker).processOne(id);
    }

}
