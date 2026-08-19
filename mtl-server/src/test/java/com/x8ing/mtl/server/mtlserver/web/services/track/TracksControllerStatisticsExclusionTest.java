package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.GpsTrackOverviewPeriod;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackAndDataService;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TracksControllerStatisticsExclusionTest {

    @Test
    void updatesTrackActivityTypeAsUserSetAndRecalculatesEnergy() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        EnergyService energyService = mock(EnergyService.class);
        GpsTrack track = new GpsTrack();
        track.setId(42L);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.BICYCLE);
        when(repository.findById(42L)).thenReturn(Optional.of(track));
        when(repository.save(any(GpsTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GpsTrack saved = controller(repository, mock(FilterExecutionService.class), energyService).updateTrackActivityType(
                42L,
                new ActivityTypeUpdateRequest(GpsTrack.ACTIVITY_TYPE.WALKING)
        );

        assertThat(saved.getActivityType()).isEqualTo(GpsTrack.ACTIVITY_TYPE.WALKING);
        assertThat(saved.getActivityTypeSource()).isEqualTo(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        verify(repository).save(track);
        verify(energyService).getDefaultParameters();
        verify(energyService).recalculateEnergyForTrack(eq(42L), any());
    }

    @Test
    void skipsActivityTypeSaveWhenValueIsUnchanged() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        EnergyService energyService = mock(EnergyService.class);
        GpsTrack track = new GpsTrack();
        track.setId(42L);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.BICYCLE);
        when(repository.findById(42L)).thenReturn(Optional.of(track));

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
        when(repository.findById(42L)).thenReturn(Optional.of(track));
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
        when(repository.findById(42L)).thenReturn(Optional.of(track));
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

    private static TracksController controller(GpsTrackRepository repository) {
        return controller(repository, mock(FilterExecutionService.class));
    }

    private static TracksController controller(GpsTrackRepository repository, FilterExecutionService filter) {
        return controller(repository, filter, mock(EnergyService.class));
    }

    private static TracksController controller(GpsTrackRepository repository, FilterExecutionService filter, EnergyService energyService) {
        return controller(repository, filter, energyService, mock(StatisticsMilestoneService.class));
    }

    private static TracksController controller(
            GpsTrackRepository repository,
            FilterExecutionService filter,
            EnergyService energyService,
            StatisticsMilestoneService milestoneService
    ) {
        return new TracksController(
                repository,
                mock(TrackTimeBetweenTwoPoints.class),
                mock(GpsTrackAndDataService.class),
                mock(GpsTrackDataRepository.class),
                mock(GpsTrackDataPointRepository.class),
                mock(GpsTrackEventRepository.class),
                mock(TrackMediaQueryRepository.class),
                filter,
                energyService,
                mock(TrackFileExportService.class),
                milestoneService,
                Runnable::run
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
