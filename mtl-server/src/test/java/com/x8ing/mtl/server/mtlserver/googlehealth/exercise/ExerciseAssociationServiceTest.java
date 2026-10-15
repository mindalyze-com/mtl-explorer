package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.ExercisePoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directRunner;
import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions;
import static org.mockito.Mockito.*;

class ExerciseAssociationServiceTest {
    @Test
    void shutdownCompletesTheCurrentAssociationAndLeavesTheNextExercisePending() {
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic-client");
        properties.setClientSecret("synthetic-secret");
        var repository = mock(MirrorRepository.class);
        var mapper = mock(ExerciseTypeMapper.class);
        var signal = new ShutdownSignal();
        var first = new ExercisePoint(1L, "first", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), "fp1", "{}",
                "first.tcx", ExercisePoint.RouteState.PENDING, 2L, 3L);
        var second = new ExercisePoint(4L, "second", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), "fp2", "{}",
                "second.tcx", ExercisePoint.RouteState.PENDING, 5L, 6L);
        when(repository.findPublishedExercisesAwaitingAssociation(ExerciseAssociationService.BATCH_SIZE)).thenReturn(List.of(first, second));
        when(repository.associateExerciseRoute(1L, 2L, 3L, "fp1")).thenAnswer(call -> {
            signal.onContextClosed();
            return 1;
        });
        var service = new ExerciseAssociationService(properties, new MtlAppProperties(), repository,
                mock(GpsTrackRepository.class), mapper, mock(EnergyRecalculationQueue.class), signal, directRunner(), directTransactions());
        service.associateCompletedImports();
        service.associateCompletedImports();
        verify(mapper).map(first.rawJson());
        verify(repository, never()).associateExerciseRoute(4L, 5L, 6L, "fp2");
        verify(repository).findPublishedExercisesAwaitingAssociation(ExerciseAssociationService.BATCH_SIZE);
    }

    @Test
    void missingConfigurationDoesNotPollMirrorTables() {
        ConnectorProperties properties = new ConnectorProperties();
        MirrorRepository repository = mock(MirrorRepository.class);
        ExerciseAssociationService service = new ExerciseAssociationService(
                properties,
                new MtlAppProperties(),
                repository,
                mock(GpsTrackRepository.class),
                mock(ExerciseTypeMapper.class), mock(EnergyRecalculationQueue.class), new ShutdownSignal(), directRunner(), directTransactions());

        service.associateCompletedImports();

        verify(repository, never()).findPublishedExercisesAwaitingAssociation(ExerciseAssociationService.BATCH_SIZE);
    }

    @Test
    void reappliesGoogleClassificationAfterATrackReplacement() {
        ConnectorProperties properties = new ConnectorProperties();
        MirrorRepository repository = mock(MirrorRepository.class);
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        ExerciseTypeMapper mapper = mock(ExerciseTypeMapper.class);
        ExercisePoint exercise = new ExercisePoint(
                1L, "exercise", Instant.now(), Instant.now(), "fingerprint",
                "{\"exercise\":{\"exerciseType\":\"RUNNING\"}}", "route.tcx",
                ExercisePoint.RouteState.IMPORTED, 2L, 20L);
        when(mapper.map(exercise.rawJson())).thenReturn(Optional.of(
                new ExerciseTypeMapper.MappedType(GpsTrack.ACTIVITY_TYPE.RUNNING, "RUNNING")));
        when(gpsTrackRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(new GpsTrack()));
        ExerciseAssociationService service = new ExerciseAssociationService(
                properties, new MtlAppProperties(), repository, gpsTrackRepository, mapper, mock(EnergyRecalculationQueue.class), new ShutdownSignal(), directRunner(), directTransactions());

        service.applyExerciseType(exercise);

        verify(gpsTrackRepository).updateActivityClassificationUnlessUserSet(
                20L,
                GpsTrack.ACTIVITY_TYPE.RUNNING,
                GpsTrack.ACTIVITY_TYPE_SOURCE.GOOGLE_HEALTH,
                "Google Health exercise type: RUNNING");
    }

    @Test
    void identicalClassificationDoesNotRewriteTheTrackOrRequestEnergy() {
        var tracks = mock(GpsTrackRepository.class);
        var mapper = mock(ExerciseTypeMapper.class);
        var queue = mock(EnergyRecalculationQueue.class);
        var track = new GpsTrack();
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.RUNNING);
        track.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.GOOGLE_HEALTH);
        track.setActivityTypeSourceDetails("Google Health exercise type: RUNNING");
        var exercise = new ExercisePoint(1L, "exercise", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), "fp", "{}",
                "route.tcx", ExercisePoint.RouteState.IMPORTED, 2L, 3L);
        when(mapper.map(exercise.rawJson())).thenReturn(Optional.of(
                new ExerciseTypeMapper.MappedType(GpsTrack.ACTIVITY_TYPE.RUNNING, "RUNNING")));
        when(tracks.findByIdForUpdate(3L)).thenReturn(Optional.of(track));

        new ExerciseAssociationService(new ConnectorProperties(), new MtlAppProperties(), mock(MirrorRepository.class),
                tracks, mapper, queue, new ShutdownSignal(), directRunner(), directTransactions()).applyExerciseType(exercise);

        verify(tracks).findByIdForUpdate(3L);
        verifyNoMoreInteractions(tracks);
        verifyNoInteractions(queue);
    }

    @Test
    void supersededMetadataDoesNotApplyAStaleClassification() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("client");
        properties.setClientSecret("secret");
        MirrorRepository repository = mock(MirrorRepository.class);
        GpsTrackRepository gpsTracks = mock(GpsTrackRepository.class);
        ExerciseTypeMapper mapper = mock(ExerciseTypeMapper.class);
        EnergyRecalculationQueue queue = mock(EnergyRecalculationQueue.class);
        ExercisePoint candidate = new ExercisePoint(1L, "exercise", Instant.EPOCH, Instant.EPOCH.plusSeconds(60),
                "old-metadata", "{}", "route.tcx", ExercisePoint.RouteState.PENDING, 2L, 3L);
        when(repository.findPublishedExercisesAwaitingAssociation(ExerciseAssociationService.BATCH_SIZE)).thenReturn(List.of(candidate));
        when(repository.associateExerciseRoute(1L, 2L, 3L, "old-metadata")).thenReturn(0);

        new ExerciseAssociationService(properties, new MtlAppProperties(), repository, gpsTracks, mapper, queue, new ShutdownSignal(), directRunner(), directTransactions())
                .associateCompletedImports();

        Mockito.verifyNoInteractions(gpsTracks, mapper, queue);
    }

    @Test
    void demoModeDoesNotPollMirrorTablesEvenWhenConfigured() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("client");
        properties.setClientSecret("secret");
        MtlAppProperties appProperties = new MtlAppProperties();
        appProperties.setDemoMode(true);
        MirrorRepository repository = mock(MirrorRepository.class);
        ExerciseAssociationService service = new ExerciseAssociationService(
                properties,
                appProperties,
                repository,
                mock(GpsTrackRepository.class),
                mock(ExerciseTypeMapper.class), mock(EnergyRecalculationQueue.class), new ShutdownSignal(), directRunner(), directTransactions());

        service.associateCompletedImports();

        verify(repository, never()).findPublishedExercisesAwaitingAssociation(ExerciseAssociationService.BATCH_SIZE);
    }
    @Test
    void continuationIsScheduledOnlyAfterTheAssociationTransactionCommits() {
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic-client");
        properties.setClientSecret("synthetic-secret");
        var repository = mock(MirrorRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var scheduler = mock(TaskScheduler.class);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("taskScheduler", scheduler);
        var signal = new ShutdownSignal();
        var runner = new BackgroundJobRunner(
                scheduler, signal);
        var exercises = LongStream.rangeClosed(1, ExerciseAssociationService.BATCH_SIZE)
                .mapToObj(id -> new ExercisePoint(id, "exercise-" + id, Instant.EPOCH, Instant.EPOCH.plusSeconds(60),
                        "fp-" + id, "{}", "route.tcx", ExercisePoint.RouteState.PENDING, 2L, 3L)).toList();
        when(repository.findPublishedExercisesAwaitingAssociation(ExerciseAssociationService.BATCH_SIZE)).thenReturn(exercises);
        when(repository.associateExerciseRoute(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(1);
        var service = new ExerciseAssociationService(properties, new MtlAppProperties(), repository,
                mock(GpsTrackRepository.class), mock(ExerciseTypeMapper.class), mock(EnergyRecalculationQueue.class),
                signal, runner, new TransactionTemplate(manager));
        service.associateCompletedImports();
        var order = inOrder(repository, manager, scheduler);
        order.verify(repository, times(ExerciseAssociationService.BATCH_SIZE))
                .associateExerciseRoute(anyLong(), anyLong(), anyLong(), anyString());
        order.verify(manager).commit(any());
        order.verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

}
