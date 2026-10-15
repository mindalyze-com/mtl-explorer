package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientConnectionException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityTypeClassifierJobTest {
    @Test
    void temporaryPointLoadingFailureLeavesClassificationPendingUntilTheNextTurn() {
        var repository = mock(GpsTrackRepository.class);
        var selector = mock(GpsTrackVariantSelector.class);
        var classifier = mock(ActivityTypeAutoClassifier.class);
        var logs = mock(SystemLogService.class);
        var job = new ActivityTypeClassifierJob(repository, selector, classifier, logs, mock(IndexerStatusService.class), new ShutdownSignal());
        var points = List.of(new GpsTrackDataPoint());
        when(repository.findIdsPendingActivityClassification(any(Pageable.class))).thenReturn(List.of(101L, 102L));
        when(selector.pointsForMetrics(101L))
                .thenThrow(new CannotGetJdbcConnectionException("Synthetic pool wait", new SQLTransientConnectionException("Pool busy")))
                .thenReturn(points);
        when(selector.pointsForMetrics(102L)).thenReturn(points);
        when(classifier.classifyActivity(anyLong(), anyList())).thenReturn(
                new ActivityTypeAutoClassifier.ClassificationResult(null, GpsTrack.ACTIVITY_TYPE.WALKING, true));

        assertThat(job.run()).isFalse();
        verify(classifier, never()).markFailed(anyLong(), anyString());
        verify(selector, never()).pointsForMetrics(102L);
        verifyNoInteractions(logs);

        job.run();
        verify(classifier).classifyActivity(101L, points);
        verify(classifier).classifyActivity(102L, points);
        verify(classifier, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void temporaryTransactionFailureDoesNotConsumeOrFailLaterTracks() {
        var repository = mock(GpsTrackRepository.class);
        var selector = mock(GpsTrackVariantSelector.class);
        var classifier = mock(ActivityTypeAutoClassifier.class);
        var logs = mock(SystemLogService.class);
        var job = new ActivityTypeClassifierJob(repository, selector, classifier, logs, mock(IndexerStatusService.class), new ShutdownSignal());
        var points = List.of(new GpsTrackDataPoint());
        when(repository.findIdsPendingActivityClassification(any(Pageable.class))).thenReturn(List.of(101L, 102L));
        when(selector.pointsForMetrics(101L)).thenReturn(points);
        when(classifier.classifyActivity(101L, points))
                .thenThrow(new CannotCreateTransactionException("Synthetic pool wait", new SQLTransientConnectionException("Pool busy")))
                .thenReturn(new ActivityTypeAutoClassifier.ClassificationResult(null, GpsTrack.ACTIVITY_TYPE.WALKING, true));
        when(selector.pointsForMetrics(102L)).thenReturn(points);
        when(classifier.classifyActivity(102L, points)).thenReturn(
                new ActivityTypeAutoClassifier.ClassificationResult(null, GpsTrack.ACTIVITY_TYPE.WALKING, true));

        assertThat(job.run()).isFalse();
        verify(selector, never()).pointsForMetrics(102L);
        verify(classifier, never()).markFailed(anyLong(), anyString());

        job.run();
        verify(classifier, times(2)).classifyActivity(101L, points);
        verify(classifier).classifyActivity(102L, points);
        verifyNoInteractions(logs);
    }

    @Test
    void shutdownFinishesClassificationAndDoesNotStartTheNextTrack() {
        var repository = mock(GpsTrackRepository.class);
        var selector = mock(GpsTrackVariantSelector.class);
        var classifier = mock(ActivityTypeAutoClassifier.class);
        var logs = mock(SystemLogService.class);
        var signal = new ShutdownSignal();
        var job = new ActivityTypeClassifierJob(repository, selector, classifier, logs, mock(IndexerStatusService.class), signal);
        var points = List.of(new GpsTrackDataPoint());
        when(repository.findIdsPendingActivityClassification(any(Pageable.class))).thenReturn(List.of(101L, 102L));
        when(selector.pointsForMetrics(101L)).thenReturn(points);
        when(classifier.classifyActivity(101L, points)).thenAnswer(call -> {
            signal.onContextClosed();
            return new ActivityTypeAutoClassifier.ClassificationResult(null, GpsTrack.ACTIVITY_TYPE.WALKING, true);
        });

        job.run();
        job.run();

        verify(classifier).classifyActivity(101L, points);
        verify(selector, never()).pointsForMetrics(102L);
        verify(classifier, never()).markFailed(ArgumentMatchers.anyLong(), anyString());
        verifyNoInteractions(logs);
        verify(repository).findIdsPendingActivityClassification(any(Pageable.class));
    }

    @Test
    void runUsesPendingIdsAndDoesNotLoadStaleGpsTrackEntities() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackVariantSelector gpsTrackVariantSelector = mock(GpsTrackVariantSelector.class);
        ActivityTypeAutoClassifier activityTypeAutoClassifier = mock(ActivityTypeAutoClassifier.class);
        EnergyService energyService = mock(EnergyService.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        IndexerStatusService indexerStatusService = mock(IndexerStatusService.class);
        ActivityTypeClassifierJob job = new ActivityTypeClassifierJob(
                gpsTrackRepository,
                gpsTrackVariantSelector,
                activityTypeAutoClassifier,
                systemLogService,
                indexerStatusService, new ShutdownSignal());

        when(gpsTrackRepository.findIdsPendingActivityClassification(any(Pageable.class))).thenReturn(List.of());

        job.run();

        verify(gpsTrackRepository).findIdsPendingActivityClassification(any(Pageable.class));
        verify(gpsTrackRepository, never()).findByActivityTypeSourceNull();
        verifyNoInteractions(gpsTrackVariantSelector, activityTypeAutoClassifier, energyService, systemLogService);
    }

    @Test
    void runClassifiesPendingIdsAndLeavesEnergyToTheDurableHandoff() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackVariantSelector gpsTrackVariantSelector = mock(GpsTrackVariantSelector.class);
        ActivityTypeAutoClassifier activityTypeAutoClassifier = mock(ActivityTypeAutoClassifier.class);
        EnergyService energyService = mock(EnergyService.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        IndexerStatusService indexerStatusService = mock(IndexerStatusService.class);
        ActivityTypeClassifierJob job = new ActivityTypeClassifierJob(
                gpsTrackRepository,
                gpsTrackVariantSelector,
                activityTypeAutoClassifier,
                systemLogService,
                indexerStatusService, new ShutdownSignal());
        List<GpsTrackDataPoint> trackPoints = List.of(new GpsTrackDataPoint());

        when(gpsTrackRepository.findIdsPendingActivityClassification(any(Pageable.class))).thenReturn(List.of(42L));
        when(gpsTrackVariantSelector.pointsForMetrics(42L)).thenReturn(trackPoints);
        when(activityTypeAutoClassifier.classifyActivity(42L, trackPoints))
                .thenReturn(new ActivityTypeAutoClassifier.ClassificationResult(
                        null,
                        GpsTrack.ACTIVITY_TYPE.BICYCLE,
                        true));

        job.run();

        verify(gpsTrackVariantSelector).pointsForMetrics(42L);
        verify(activityTypeAutoClassifier).classifyActivity(42L, trackPoints);
        verifyNoInteractions(energyService);
        verifyNoInteractions(systemLogService);
    }

    @Test
    void runWaitsForGpsIndexingToSettle() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        IndexerStatusService indexerStatusService = mock(IndexerStatusService.class);
        ActivityTypeClassifierJob job = new ActivityTypeClassifierJob(
                gpsTrackRepository,
                mock(GpsTrackVariantSelector.class),
                mock(ActivityTypeAutoClassifier.class),
                mock(SystemLogService.class),
                indexerStatusService, new ShutdownSignal());
        when(indexerStatusService.hasIndexPendingWork(GPXDirectoryWatcherService.INDEX_GPS))
                .thenReturn(true);

        job.run();

        verifyNoInteractions(gpsTrackRepository);
    }

    @Test
    void pointLoadingAndClassificationFailuresAreTerminalAndLaterTracksStillRun() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        GpsTrackVariantSelector selector = mock(GpsTrackVariantSelector.class);
        ActivityTypeAutoClassifier classifier = mock(ActivityTypeAutoClassifier.class);
        SystemLogService logs = mock(SystemLogService.class);
        ActivityTypeClassifierJob job = new ActivityTypeClassifierJob(repository, selector, classifier, logs,
                mock(IndexerStatusService.class), new ShutdownSignal());
        List<GpsTrackDataPoint> points = List.of(new GpsTrackDataPoint());
        when(repository.findIdsPendingActivityClassification(any(Pageable.class))).thenReturn(List.of(101L, 102L, 103L), List.of());
        when(selector.pointsForMetrics(101L)).thenThrow(new IllegalStateException("Synthetic point load failure"));
        when(selector.pointsForMetrics(102L)).thenReturn(points);
        when(selector.pointsForMetrics(103L)).thenReturn(points);
        when(classifier.classifyActivity(102L, points)).thenThrow(new IllegalArgumentException("Synthetic classification failure"));
        when(classifier.classifyActivity(103L, points)).thenReturn(
                new ActivityTypeAutoClassifier.ClassificationResult(null, GpsTrack.ACTIVITY_TYPE.WALKING, true));

        assertThatCode(job::run).doesNotThrowAnyException();
        assertThatCode(job::run).doesNotThrowAnyException();

        var order = inOrder(classifier);
        order.verify(classifier).markFailed(101L, "Activity classification failed (IllegalStateException). See server logs.");
        order.verify(classifier).classifyActivity(102L, points);
        order.verify(classifier).markFailed(102L, "Activity classification failed (IllegalArgumentException). See server logs.");
        order.verify(classifier).classifyActivity(103L, points);
    }

    @Test
    void failureStatusAndErrorLogPersistenceFailuresDoNotStopTheBatch() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        GpsTrackVariantSelector selector = mock(GpsTrackVariantSelector.class);
        ActivityTypeAutoClassifier classifier = mock(ActivityTypeAutoClassifier.class);
        SystemLogService logs = mock(SystemLogService.class);
        ActivityTypeClassifierJob job = new ActivityTypeClassifierJob(repository, selector, classifier, logs,
                mock(IndexerStatusService.class), new ShutdownSignal());
        List<GpsTrackDataPoint> points = List.of(new GpsTrackDataPoint());
        when(repository.findIdsPendingActivityClassification(any(Pageable.class))).thenReturn(List.of(101L, 102L));
        when(selector.pointsForMetrics(101L)).thenThrow(new IllegalStateException("Synthetic processing failure"));
        doThrow(new IllegalStateException("Synthetic persistence failure")).when(classifier).markFailed(eq(101L), anyString());
        doThrow(new IllegalStateException("Synthetic log failure"))
                .when(logs).saveLogForException(any(), anyString(), anyString(), any());
        when(selector.pointsForMetrics(102L)).thenReturn(points);
        when(classifier.classifyActivity(102L, points)).thenReturn(
                new ActivityTypeAutoClassifier.ClassificationResult(null, GpsTrack.ACTIVITY_TYPE.WALKING, true));

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(classifier).classifyActivity(102L, points);
    }
}
