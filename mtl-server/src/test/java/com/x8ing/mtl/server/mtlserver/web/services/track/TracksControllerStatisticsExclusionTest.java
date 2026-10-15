package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.GpsTrackOverviewPeriod;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.*;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaQueryRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.TrackTimeBetweenTwoPoints;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterExecutionService;
import com.x8ing.mtl.server.mtlserver.measurement.MeasurementSystem;
import com.x8ing.mtl.server.mtlserver.measurement.MilestoneDimension;
import com.x8ing.mtl.server.mtlserver.measurement.MilestoneResult;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.ActivityTypeUpdateRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.StatisticsExclusionUpdateRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.StatisticsOverviewResponseDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TracksControllerStatisticsExclusionTest {
    private final com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue energyQueue = mock(com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue.class);

    @Test
    void pointResponsesApplyTheOwningTracksTimingPolicyToRawAndDerivedVariants() {
        var tracks = mock(GpsTrackRepository.class);
        var points = mock(GpsTrackDataPointRepository.class);
        var track = new GpsTrack();
        track.setId(42L);
        var point = new com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint();
        point.setDurationSinceStart(900.0);
        when(tracks.findById(42L)).thenReturn(Optional.of(track));
        when(points.getTrackDetailsByGpsTrackIdAndPrecisionAndType(eq(42L), any(), any()))
                .thenReturn(List.of(point));
        var controller = controller(tracks, mock(FilterExecutionService.class), mock(EnergyService.class),
                mock(StatisticsMilestoneService.class), points);
        for (String variant : List.of("RAW", "RAW_OUTLIER_CLEANED", "SIMPLIFIED_SHAPE")) {
            for (boolean unsupported : List.of(true, false)) {
                track.setTimingUnreliable(unsupported);
                var response = controller.getTrackDetails(42L, java.math.BigDecimal.ONE, variant);
                assertThat(response.getBody()).hasSize(1);
                assertThat(response.getBody().getFirst().isTimingUnsupportedForResponse()).isEqualTo(unsupported);
                assertThat(point.getDurationSinceStart()).isEqualTo(900);
            }
        }
    }

    @Test
    void metadataSaveCannotClearReviewFlagsOrEraseHiddenTimingEvidence() {
        var repository = mock(GpsTrackRepository.class);
        var stored = new GpsTrack();
        stored.setId(42L);
        stored.setReviewStatus("NEEDS_REVIEW");
        stored.setReviewExcluded(true);
        stored.setTimingUnreliable(true);
        stored.setTimingEstimated(true);
        stored.setSourceContentHash("synthetic-source-revision");
        stored.setSourceRecordingSection(2);
        stored.setTrackDurationInMotionSecs(500.0);
        stored.setPowerWattsAvg(200.0);
        stored.setExplorationScore(0.75);
        var request = new GpsTrack();
        request.setId(42L);
        request.setTrackName("Updated name");
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(stored));
        when(repository.save(any(GpsTrack.class))).thenAnswer(call -> call.getArgument(0));
        GpsTrack saved = controller(repository).saveTrack(request);
        assertThat(saved.getTrackName()).isEqualTo("Updated name");
        assertThat(saved.getReviewStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(saved.isReviewExcluded() && saved.isTimingUnreliable() && saved.isTimingEstimated()).isTrue();
        assertThat(saved.getSourceContentHash()).isEqualTo("synthetic-source-revision");
        assertThat(saved.getSourceRecordingSection()).isEqualTo(2);
        assertThat(saved.getTrackDurationInMotionSecs()).isEqualTo(500.0);
        assertThat(saved.getPowerWattsAvg()).isEqualTo(200.0);
        assertThat(saved.getExplorationScore()).isEqualTo(0.75);
    }

    @Test
    void updatesTrackActivityTypeAsUserSetAndRecalculatesEnergy() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        EnergyService energyService = mock(EnergyService.class);
        GpsTrack track = new GpsTrack();
        track.setId(42L);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.BICYCLE);
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(track));
        track.setEnergyNetTotalWh(100.0);
        when(energyService.parametersForTrack(track)).thenReturn(com.x8ing.mtl.server.mtlserver.energy.EnergyParameters.builder().riderWeightKg(82).build());
        when(repository.save(any(GpsTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GpsTrack saved = controller(repository, mock(FilterExecutionService.class), energyService).updateTrackActivityType(
                42L,
                new ActivityTypeUpdateRequest(GpsTrack.ACTIVITY_TYPE.WALKING)
        );

        assertThat(saved.getActivityType()).isEqualTo(GpsTrack.ACTIVITY_TYPE.WALKING);
        assertThat(saved.getActivityTypeSource()).isEqualTo(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        verify(repository).save(track);
        assertThat(saved.getEnergyNetTotalWh()).isNull();
        assertThat(saved.getMetabolicEnergy().getBodyWeightKg()).isEqualTo(82);
        verify(energyService).parametersForTrack(track);
        verify(energyQueue).request(42L);
        verify(energyService, never()).recalculateEnergyForTrack(any(), any());
    }

    @Test
    void metadataSavePreservesCalculatedCaloriesAndWeightAgainstOmittedOrForgedInput() {
        var repository = mock(GpsTrackRepository.class);
        var stored = new GpsTrack();
        stored.setId(42L);
        stored.setActivityType(GpsTrack.ACTIVITY_TYPE.KAYAKING);
        stored.setEnergyNetTotalWh(20.0);
        stored.setEnergyWeightKgUsed(102.0);
        stored.setPowerWattsAvg(30.0);
        var estimate = new com.x8ing.mtl.server.mtlserver.energy.MetabolicEnergy();
        estimate.setBodyWeightKg(82.0);
        estimate.setActiveKcal(100.0);
        stored.setMetabolicEnergy(estimate);
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(stored));
        when(repository.save(any(GpsTrack.class))).thenAnswer(call -> call.getArgument(0));
        for (boolean forged : List.of(false, true)) {
            var request = new GpsTrack();
            request.setId(42L);
            request.setTrackName("Updated");
            request.setActivityType(stored.getActivityType());
            if (forged) {
                var bad = new com.x8ing.mtl.server.mtlserver.energy.MetabolicEnergy();
                bad.setBodyWeightKg(40.0);
                bad.setActiveKcal(9999.0);
                request.setMetabolicEnergy(bad);
            }
            var saved = controller(repository).saveTrack(request);
            assertThat(saved.getMetabolicEnergy()).isEqualTo(estimate);
            assertThat(saved.getEnergyNetTotalWh()).isEqualTo(20.0);
            assertThat(saved.getEnergyWeightKgUsed()).isEqualTo(102.0);
            assertThat(saved.getPowerWattsAvg()).isEqualTo(30.0);
        }
        verifyNoInteractions(energyQueue);
    }

    @Test
    void legacyActivityChangeKeepsBodyWeightAndQueuesCalculation() {
        var repository = mock(GpsTrackRepository.class);
        var energy = mock(EnergyService.class);
        var stored = new GpsTrack();
        stored.setId(42L);
        stored.setActivityType(GpsTrack.ACTIVITY_TYPE.BICYCLE);
        stored.setEnergyNetTotalWh(100.0);
        stored.setEnergyWeightKgUsed(92.0);
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(stored));
        when(repository.save(any(GpsTrack.class))).thenAnswer(call -> call.getArgument(0));
        when(energy.parametersForTrack(stored)).thenReturn(com.x8ing.mtl.server.mtlserver.energy.EnergyParameters.builder().riderWeightKg(82).build());
        var request = new GpsTrack();
        request.setId(42L);
        request.setActivityType(GpsTrack.ACTIVITY_TYPE.ROWING);
        var saved = controller(repository, mock(FilterExecutionService.class), energy).saveTrack(request);
        assertThat(saved.getEnergyNetTotalWh()).isNull();
        assertThat(saved.getPowerWattsAvg()).isNull();
        assertThat(saved.getMetabolicEnergy().getBodyWeightKg()).isEqualTo(82);
        assertThat(saved.getMetabolicEnergy().getActiveKcal()).isNull();
        verify(energyQueue).request(42L);
    }

    @Test
    void skipsActivityTypeSaveWhenValueIsUnchanged() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        EnergyService energyService = mock(EnergyService.class);
        GpsTrack track = new GpsTrack();
        track.setId(42L);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.BICYCLE);
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(track));

        GpsTrack saved = controller(repository, mock(FilterExecutionService.class), energyService).updateTrackActivityType(
                42L,
                new ActivityTypeUpdateRequest(GpsTrack.ACTIVITY_TYPE.BICYCLE)
        );

        assertThat(saved).isSameAs(track);
        verify(repository, never()).save(any(GpsTrack.class));
        verify(energyService, never()).recalculateEnergyForTrack(any(), any());
    }

    @Test
    void updatesTrackStatisticsExclusionReasons() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        GpsTrack track = new GpsTrack();
        track.setId(42L);
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(track));
        when(repository.save(any(GpsTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GpsTrack saved = controller(repository).updateTrackStatisticsExclusion(
                42L,
                new StatisticsExclusionUpdateRequest(
                        GpsTrack.STATISTICS_EXCLUSION_REASON.GPS_NOISE,
                        GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER
                )
        );

        assertThat(saved.getHighlightExclusionReason()).isEqualTo(GpsTrack.STATISTICS_EXCLUSION_REASON.GPS_NOISE);
        assertThat(saved.getStatisticsExclusionReason()).isEqualTo(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
        verify(repository).save(track);
    }

    @Test
    void clearsTrackStatisticsExclusionReasonsWithNullRequestValues() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        GpsTrack track = new GpsTrack();
        track.setId(42L);
        track.setHighlightExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.GPS_NOISE);
        track.setStatisticsExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.IMPORT_ARTIFACT);
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(track));
        when(repository.save(any(GpsTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GpsTrack saved = controller(repository).updateTrackStatisticsExclusion(
                42L,
                new StatisticsExclusionUpdateRequest(null, null)
        );

        assertThat(saved.getHighlightExclusionReason()).isNull();
        assertThat(saved.getStatisticsExclusionReason()).isNull();
        verify(repository).save(track);
    }

    @Test
    void derivesActivePeriodsFromTopPeriodDistributionRows() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        FilterExecutionService filter = mock(FilterExecutionService.class);
        List<GpsTrackOverviewPeriod> periods = List.of(
                period(1, "day", "2026-05-24", "2026-05-24", 3L, 30_000.0, 9_000_000.0),
                period(2, "day", "2026-05-23", "2026-05-23", 2L, 20_000.0, 7_000_000.0),
                period(1, "week", "2026-W21", "2026-W21", 8L, 80_000.0, 20_000_000.0),
                period(1, "weekday", "7", "Sunday", 5L, 50_000.0, 12_000_000.0)
        );
        when(repository.getTrackOverviewActivityBreakdown(any(Long[].class))).thenReturn(List.of());
        when(repository.getTrackOverviewTrackRankings(any(Long[].class), anyInt())).thenReturn(List.of());
        when(repository.getTrackOverviewRecentActivities(any(Long[].class))).thenReturn(List.of());
        when(repository.getTrackOverviewPeriodDistributions(any(Long[].class), anyInt())).thenReturn(periods);
        when(repository.getTrackOverviewActivityBounds(any(Long[].class))).thenReturn(List.of());

        StatisticsOverviewResponseDto response = controller(repository, filter).getTrackOverview(
                List.of(1L, 2L),
                MeasurementSystem.METRIC
        );

        assertThat(response.activePeriods())
                .extracting(
                        StatisticsOverviewResponseDto.PeriodRow::periodType,
                        StatisticsOverviewResponseDto.PeriodRow::periodKey,
                        StatisticsOverviewResponseDto.PeriodRow::sortOrder
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("day", "2026-05-24", 10),
                        org.assertj.core.groups.Tuple.tuple("week", "2026-W21", 20),
                        org.assertj.core.groups.Tuple.tuple("weekday", "7", 40)
                );
        verifyNoInteractions(filter);
    }

    @Test
    void trackStatisticsUseProvidedTrackIdsWithoutResolvingFilter() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        FilterExecutionService filter = mock(FilterExecutionService.class);
        EnergyService energyService = mock(EnergyService.class);
        when(energyService.getThresholdPowerWatts()).thenReturn(250.0);

        var response = controller(repository, filter, energyService).getTrackStatistics(
                List.of(5L, 8L),
                "YYYY",
                null
        );

        assertThat(response).isEmpty();
        ArgumentCaptor<Long[]> trackIds = ArgumentCaptor.forClass(Long[].class);
        verify(repository).getTrackStatistics(eq("YYYY"), isNull(), trackIds.capture(), eq(250.0));
        assertThat(trackIds.getValue()).containsExactly(5L, 8L);
        verifyNoInteractions(filter);
    }

    @Test
    void evaluatesAndReturnsMilestonesForTheRequestedMeasurementSystem() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        StatisticsMilestoneService milestoneService = mock(StatisticsMilestoneService.class);
        when(repository.getTrackOverviewActivityBounds(any(Long[].class))).thenReturn(List.of());
        when(milestoneService.findMilestones(any(Long[].class), eq(MeasurementSystem.US_CUSTOMARY)))
                .thenReturn(List.of(new MilestoneResult(
                        MilestoneDimension.DISTANCE,
                        160_934.4,
                        5,
                        42L,
                        165_000.0
                )));

        StatisticsOverviewResponseDto response = controller(
                repository,
                mock(FilterExecutionService.class),
                mock(EnergyService.class),
                milestoneService
        ).getTrackOverview(List.of(7L, 42L), MeasurementSystem.US_CUSTOMARY);

        assertThat(response.measurementSystem()).isEqualTo(MeasurementSystem.US_CUSTOMARY);
        assertThat(response.milestones()).singleElement().satisfies(milestone -> {
            assertThat(milestone.dimension()).isEqualTo(MilestoneDimension.DISTANCE);
            assertThat(milestone.thresholdM()).isEqualTo(160_934.4);
            assertThat(milestone.achievedM()).isEqualTo(165_000.0);
            assertThat(milestone.trackId()).isEqualTo(42L);
            assertThat(milestone.thresholdWh()).isNull();
        });
    }

    private TracksController controller(GpsTrackRepository repository) {
        return controller(repository, mock(FilterExecutionService.class));
    }

    private TracksController controller(GpsTrackRepository repository, FilterExecutionService filter) {
        return controller(repository, filter, mock(EnergyService.class));
    }

    private TracksController controller(GpsTrackRepository repository, FilterExecutionService filter, EnergyService energyService) {
        return controller(repository, filter, energyService, mock(StatisticsMilestoneService.class));
    }

    private TracksController controller(
            GpsTrackRepository repository,
            FilterExecutionService filter,
            EnergyService energyService,
            StatisticsMilestoneService milestoneService
    ) {
        return controller(repository, filter, energyService, milestoneService, mock(GpsTrackDataPointRepository.class));
    }

    private TracksController controller(
            GpsTrackRepository repository,
            FilterExecutionService filter,
            EnergyService energyService,
            StatisticsMilestoneService milestoneService,
            GpsTrackDataPointRepository pointRepository
    ) {
        return new TracksController(
                repository,
                mock(TrackTimeBetweenTwoPoints.class),
                mock(GpsTrackAndDataService.class),
                mock(GpsTrackDataRepository.class),
                pointRepository,
                mock(GpsTrackEventRepository.class),
                mock(TrackMediaQueryRepository.class),
                filter,
                energyService,
                energyQueue,
                mock(TrackFileExportService.class),
                milestoneService,
                Runnable::run,
                mock(com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateService.class),
                mock(com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAttemptResolver.class),
                mock(com.x8ing.mtl.server.mtlserver.indexer.TrackProcessingStatusService.class),
                mock(com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentReadScope.class)
        );
    }

    private static QueryResult queryResult(Long... ids) {
        QueryResult result = new QueryResult();
        result.setResultEntries(List.of(ids).stream()
                .map(id -> {
                    QueryResult.QueryResultEntry entry = new QueryResult.QueryResultEntry();
                    entry.setId(id);
                    return entry;
                })
                .toList());
        return result;
    }

    private static GpsTrackOverviewPeriod period(
            Integer sortOrder,
            String periodType,
            String periodKey,
            String label,
            Long trackCount,
            Double distanceM,
            Double durationMs
    ) {
        GpsTrackOverviewPeriod period = mock(GpsTrackOverviewPeriod.class);
        when(period.getSortOrder()).thenReturn(sortOrder);
        when(period.getPeriodType()).thenReturn(periodType);
        when(period.getPeriodKey()).thenReturn(periodKey);
        when(period.getLabel()).thenReturn(label);
        when(period.getTrackCount()).thenReturn(trackCount);
        when(period.getDistanceM()).thenReturn(distanceM);
        when(period.getDurationMs()).thenReturn(durationMs);
        return period;
    }
}
