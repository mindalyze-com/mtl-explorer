package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.GpsTrackOverviewPeriod;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackAndDataService;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.TrackTimeBetweenTwoPoints;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterParamResolver;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.GpsTrackSQLFilter;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.StatisticsExclusionUpdateRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.StatisticsOverviewResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracksControllerStatisticsExclusionTest {

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
        GpsTrackSQLFilter filter = mock(GpsTrackSQLFilter.class);
        List<GpsTrackOverviewPeriod> periods = List.of(
                period(1, "day", "2026-05-24", "2026-05-24", 3L, 30_000.0, 9_000_000.0),
                period(2, "day", "2026-05-23", "2026-05-23", 2L, 20_000.0, 7_000_000.0),
                period(1, "week", "2026-W21", "2026-W21", 8L, 80_000.0, 20_000_000.0),
                period(1, "weekday", "7", "Sunday", 5L, 50_000.0, 12_000_000.0)
        );
        when(filter.getGpsTrackIdsForOptionalFilterName("SmartBaseFilter", Map.of())).thenReturn(queryResult(1L, 2L));
        when(repository.getTrackOverviewActivityBreakdown(any(Long[].class))).thenReturn(List.of());
        when(repository.getTrackOverviewTrackRankings(any(Long[].class), anyInt())).thenReturn(List.of());
        when(repository.getTrackOverviewRecentActivities(any(Long[].class))).thenReturn(List.of());
        when(repository.getTrackOverviewPeriodDistributions(any(Long[].class), anyInt())).thenReturn(periods);
        when(repository.getTrackOverviewMilestones(any(Long[].class))).thenReturn(List.of());

        StatisticsOverviewResponseDto response = controller(repository, filter).getTrackOverview(Map.of(), "SmartBaseFilter");

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
        verify(filter).getGpsTrackIdsForOptionalFilterName("SmartBaseFilter", Map.of());
    }

    private static TracksController controller(GpsTrackRepository repository) {
        return controller(repository, mock(GpsTrackSQLFilter.class));
    }

    private static TracksController controller(GpsTrackRepository repository, GpsTrackSQLFilter filter) {
        return new TracksController(
                repository,
                mock(TrackTimeBetweenTwoPoints.class),
                mock(GpsTrackAndDataService.class),
                mock(GpsTrackDataRepository.class),
                mock(GpsTrackDataPointRepository.class),
                mock(GpsTrackEventRepository.class),
                filter,
                mock(FilterParamResolver.class),
                mock(EnergyService.class),
                mock(TrackFileExportService.class),
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
