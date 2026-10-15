package com.x8ing.mtl.server.mtlserver.jobs.duplicate;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DuplicateDetectorJobTest {
    @Test
    void temporaryConnectionFailureStopsTheBatchAndRetriesPendingTracksOnTheNextTurn() {
        var repository = mock(GpsTrackRepository.class);
        var worker = mock(DuplicateDetectorAtomicWorker.class);
        var job = new DuplicateDetectorJob(repository, worker, mock(IndexerStatusService.class), new ShutdownSignal());
        Duration tolerance = Duration.ofMinutes(5);
        ReflectionTestUtils.setField(job, "timeTolerance", tolerance);
        ReflectionTestUtils.setField(job, "distanceTolerance", 100d);
        when(repository.findIdsByDuplicateStatusExcludingPlanned(any(), any(Pageable.class)))
                .thenReturn(List.of(101L, 102L));
        doThrow(new CannotCreateTransactionException("Synthetic pool wait", new SQLTransientConnectionException("Pool busy")))
                .doNothing().when(worker).processOne(101L, tolerance, 100d);

        assertThat(job.run()).isFalse();
        verify(worker, never()).markFailed(anyLong(), anyString());
        verify(worker, never()).processOne(102L, tolerance, 100d);

        job.run();
        verify(worker, times(2)).processOne(101L, tolerance, 100d);
        verify(worker).processOne(102L, tolerance, 100d);
        verify(worker, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void shutdownAfterAnActiveTrackLeavesLaterTracksPending() {
        var repository = mock(GpsTrackRepository.class);
        var worker = mock(DuplicateDetectorAtomicWorker.class);
        var signal = new ShutdownSignal();
        var job = new DuplicateDetectorJob(repository, worker, mock(IndexerStatusService.class), signal);
        var tolerance = Duration.ofMinutes(5);
        ReflectionTestUtils.setField(job, "timeTolerance", tolerance);
        ReflectionTestUtils.setField(job, "distanceTolerance", 100d);
        var first = new GpsTrack();
        first.setId(101L);
        var second = new GpsTrack();
        second.setId(102L);
        when(repository.findIdsByDuplicateStatusExcludingPlanned(eq(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET), any(Pageable.class)))
                .thenReturn(List.of(first.getId(), second.getId()));
        Mockito.doAnswer(call -> {
                    signal.onContextClosed();
                    return null;
                })
                .when(worker).processOne(101L, tolerance, 100d);

        job.run();
        job.run();

        verify(worker).processOne(101L, tolerance, 100d);
        verify(worker, Mockito.never()).processOne(102L, tolerance, 100d);
        verify(worker, Mockito.never()).markFailed(ArgumentMatchers.anyLong(), anyString());
        verify(repository).findIdsByDuplicateStatusExcludingPlanned(eq(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET), any(Pageable.class));
    }

    @Test
    void failedTrackIsMarkedFailedAndDoesNotBlockRemainingTracks() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        DuplicateDetectorAtomicWorker worker = mock(DuplicateDetectorAtomicWorker.class);
        DuplicateDetectorJob job = new DuplicateDetectorJob(repository, worker, mock(IndexerStatusService.class), new ShutdownSignal());
        Duration tolerance = Duration.ofMinutes(5);
        double distanceTolerance = 100;
        ReflectionTestUtils.setField(job, "timeTolerance", tolerance);
        ReflectionTestUtils.setField(job, "distanceTolerance", distanceTolerance);
        GpsTrack first = new GpsTrack();
        first.setId(101L);
        GpsTrack failed = new GpsTrack();
        failed.setId(102L);
        GpsTrack last = new GpsTrack();
        last.setId(103L);
        when(repository.findIdsByDuplicateStatusExcludingPlanned(eq(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET), any(Pageable.class)))
                .thenReturn(List.of(first.getId(), failed.getId(), last.getId()), List.of());
        doThrow(new IllegalStateException("Synthetic distance calculation failure"))
                .when(worker).processOne(failed.getId(), tolerance, distanceTolerance);

        assertThatCode(job::run).doesNotThrowAnyException();
        assertThatCode(job::run).doesNotThrowAnyException();

        var order = inOrder(repository, worker);
        order.verify(repository).findIdsByDuplicateStatusExcludingPlanned(eq(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET), any(Pageable.class));
        order.verify(worker).processOne(first.getId(), tolerance, distanceTolerance);
        order.verify(worker).processOne(failed.getId(), tolerance, distanceTolerance);
        order.verify(worker).markFailed(failed.getId(), "Duplicate detection failed (IllegalStateException). See server logs.");
        order.verify(worker).processOne(last.getId(), tolerance, distanceTolerance);
        order.verify(repository).findIdsByDuplicateStatusExcludingPlanned(eq(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET), any(Pageable.class));
        verifyNoMoreInteractions(repository, worker);
    }

    @Test
    void waitsForGpsIndexingToSettle() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        DuplicateDetectorAtomicWorker worker = mock(DuplicateDetectorAtomicWorker.class);
        IndexerStatusService indexerStatusService = mock(IndexerStatusService.class);
        DuplicateDetectorJob job = new DuplicateDetectorJob(repository, worker, indexerStatusService, new ShutdownSignal());
        when(indexerStatusService.hasIndexPendingWork(GPXDirectoryWatcherService.INDEX_GPS))
                .thenReturn(true);

        job.run();

        verifyNoInteractions(repository, worker);
    }

    @Test
    void failureStatusPersistenceErrorDoesNotStopTheBatch() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        DuplicateDetectorAtomicWorker worker = mock(DuplicateDetectorAtomicWorker.class);
        DuplicateDetectorJob job = new DuplicateDetectorJob(repository, worker, mock(IndexerStatusService.class), new ShutdownSignal());
        Duration tolerance = Duration.ofMinutes(5);
        ReflectionTestUtils.setField(job, "timeTolerance", tolerance);
        ReflectionTestUtils.setField(job, "distanceTolerance", 100d);
        GpsTrack failed = new GpsTrack();
        failed.setId(101L);
        GpsTrack healthy = new GpsTrack();
        healthy.setId(102L);
        when(repository.findIdsByDuplicateStatusExcludingPlanned(eq(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET), any(Pageable.class)))
                .thenReturn(List.of(failed.getId(), healthy.getId()));
        doThrow(new IllegalStateException("Synthetic processing failure"))
                .when(worker).processOne(failed.getId(), tolerance, 100d);
        doThrow(new IllegalStateException("Synthetic persistence failure"))
                .when(worker).markFailed(eq(failed.getId()), anyString());

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(worker).processOne(healthy.getId(), tolerance, 100d);
    }
}
