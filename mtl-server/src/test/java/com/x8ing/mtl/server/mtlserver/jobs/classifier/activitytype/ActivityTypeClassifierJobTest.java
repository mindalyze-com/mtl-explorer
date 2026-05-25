package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActivityTypeClassifierJobTest {

    @Test
    void runUsesPendingIdsAndDoesNotLoadStaleGpsTrackEntities() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackVariantSelector gpsTrackVariantSelector = mock(GpsTrackVariantSelector.class);
        ActivityTypeAutoClassifier activityTypeAutoClassifier = mock(ActivityTypeAutoClassifier.class);
        EnergyService energyService = mock(EnergyService.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        ActivityTypeClassifierJob job = new ActivityTypeClassifierJob(
                gpsTrackRepository,
                gpsTrackVariantSelector,
                activityTypeAutoClassifier,
                energyService,
                systemLogService);

        when(gpsTrackRepository.findIdsPendingActivityClassification()).thenReturn(List.of());

        job.run();

        verify(gpsTrackRepository).findIdsPendingActivityClassification();
        verify(gpsTrackRepository, never()).findByActivityTypeSourceNull();
        verifyNoInteractions(gpsTrackVariantSelector, activityTypeAutoClassifier, energyService, systemLogService);
    }

    @Test
    void runClassifiesPendingIdsAndRecalculatesEnergyAfterChangedType() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackVariantSelector gpsTrackVariantSelector = mock(GpsTrackVariantSelector.class);
        ActivityTypeAutoClassifier activityTypeAutoClassifier = mock(ActivityTypeAutoClassifier.class);
        EnergyService energyService = mock(EnergyService.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        ActivityTypeClassifierJob job = new ActivityTypeClassifierJob(
                gpsTrackRepository,
                gpsTrackVariantSelector,
                activityTypeAutoClassifier,
                energyService,
                systemLogService);
        List<GpsTrackDataPoint> trackPoints = List.of(new GpsTrackDataPoint());

        when(gpsTrackRepository.findIdsPendingActivityClassification()).thenReturn(List.of(42L));
        when(gpsTrackVariantSelector.pointsForMetrics(42L)).thenReturn(trackPoints);
        when(activityTypeAutoClassifier.classifyActivity(42L, trackPoints))
                .thenReturn(new ActivityTypeAutoClassifier.ClassificationResult(
                        null,
                        GpsTrack.ACTIVITY_TYPE.BICYCLE,
                        true));

        job.run();

        verify(gpsTrackVariantSelector).pointsForMetrics(42L);
        verify(activityTypeAutoClassifier).classifyActivity(42L, trackPoints);
        verify(energyService).getDefaultParameters();
        verify(energyService).recalculateEnergyForTrack(eq(42L), isNull());
        verifyNoInteractions(systemLogService);
    }
}
