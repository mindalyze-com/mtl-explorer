package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.NearbyTrackDistance;
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
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterParamsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupKey;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupSelection;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.NearbyTrackMediaDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TracksSimplifiedResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracksControllerFilterScopingTest {

    @Test
    void relatedAndDistanceLookupsReceiveOnlyEffectiveIds() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        FilterExecutionService filterExecutionService = mock(FilterExecutionService.class);
        FilterParamsRequest request = requestWithSelectionAndTrackIds();
        when(filterExecutionService.executeOptionalFilterName("Years", request))
                .thenReturn(queryResult(entry(2L, "2025"), entry(3L, "2025")));
        when(repository.getDuplicatesForGpsTrackId(99L)).thenReturn(List.of());
        when(repository.getRelatedTrackIdsPrevious(eq(99L), any(Long[].class))).thenReturn(List.of());
        when(repository.getRelatedTrackIdsNext(eq(99L), any(Long[].class))).thenReturn(List.of());
        when(repository.findSegmentSiblingIds(99L)).thenReturn(List.of());
        when(repository.findAllById(any())).thenReturn(List.of());
        when(repository.getTracksWithinDistanceToPoint(eq(8.0), eq(47.0), eq(250.0), any(Long[].class)))
                .thenReturn(List.of(2L));
        TracksController controller = controller(repository, filterExecutionService, mock(GpsTrackAndDataService.class));

        controller.getRelatedTracks(request, 99L, "Years");
        List<Long> nearby = controller.getTrackIdsWithinDistanceOfPoint("Years", request, 8.0, 47.0, 250.0);

        verify(repository).getRelatedTrackIdsPrevious(eq(99L), argThat(ids -> List.of(ids).equals(List.of(2L, 3L))));
        verify(repository).getRelatedTrackIdsNext(eq(99L), argThat(ids -> List.of(ids).equals(List.of(2L, 3L))));
        verify(repository).getTracksWithinDistanceToPoint(
                eq(8.0),
                eq(47.0),
                eq(250.0),
                argThat(ids -> List.of(ids).equals(List.of(2L, 3L))));
        assertThat(nearby).containsExactly(2L);
    }

    @Test
    void nearbyMediaOptionsRetainTracksWithoutMediaAndAddMatchedCounts() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        TrackMediaQueryRepository mediaRepository = mock(TrackMediaQueryRepository.class);
        FilterExecutionService filterExecutionService = mock(FilterExecutionService.class);
        FilterParamsRequest request = requestWithSelectionAndTrackIds();
        when(filterExecutionService.executeOptionalFilterName("Years", request))
                .thenReturn(queryResult(entry(2L, "2025"), entry(3L, "2025")));
        when(repository.getTracksWithDistanceToPoint(eq(8.0), eq(47.0), eq(250.0), any(Long[].class)))
                .thenReturn(List.of(nearbyTrack(2L, 12.5), nearbyTrack(3L, 48.25)));
        when(mediaRepository.findSelectedMediaCountsByTrackIds(List.of(2L, 3L)))
                .thenReturn(Map.of(2L, 4L));

        List<NearbyTrackMediaDto> options = controller(
                repository,
                filterExecutionService,
                mock(GpsTrackAndDataService.class),
                mediaRepository)
                .getTrackMediaOptionsWithinDistanceOfPoint("Years", request, 8.0, 47.0, 250.0);

        assertThat(options).containsExactly(
                new NearbyTrackMediaDto(2L, 12.5, 4L),
                new NearbyTrackMediaDto(3L, 48.25, 0L));
        verify(mediaRepository).findSelectedMediaCountsByTrackIds(List.of(2L, 3L));
        verify(repository).getTracksWithDistanceToPoint(
                eq(8.0),
                eq(47.0),
                eq(250.0),
                argThat(ids -> List.of(ids).equals(List.of(2L, 3L))));
    }

    @Test
    void idsOnlyLooksUpVersionsForEffectiveIdsAndPassesTypedSelection() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        FilterExecutionService filterExecutionService = mock(FilterExecutionService.class);
        FilterParamsRequest request = requestWithSelectionAndTrackIds();
        when(filterExecutionService.executeOptionalFilterName("Years", request))
                .thenReturn(queryResult(entry(2L, "2025")));
        when(filterExecutionService.standardFilterCount(1L)).thenReturn(2L);
        when(repository.findVersionMapByIds(anyList())).thenReturn(Map.of(2L, 7L));

        TracksSimplifiedResponse response = controller(repository, filterExecutionService, mock(GpsTrackAndDataService.class))
                .getTracksSimplified(request, BigDecimal.TEN, "Years", "ids");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> versionIds = ArgumentCaptor.forClass(List.class);
        verify(repository).findVersionMapByIds(versionIds.capture());
        assertThat(versionIds.getValue()).containsExactly(2L);
        assertThat(response.getTrackVersions()).containsExactlyEntriesOf(Map.of(2L, 7L));
        assertThat(response.getFilterGroups()).containsExactlyEntriesOf(Map.of(2L, "2025"));
        assertThat(response.getNumberOfFilteredMatchedTracks()).isEqualTo(1);
        assertThat(response.getStandardFilterCount()).isEqualTo(2L);
        verify(filterExecutionService).executeOptionalFilterName("Years", request);
    }

    @Test
    void hydrationSliceCannotReintroduceIdsExcludedByResultGroupSelection() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        FilterExecutionService filterExecutionService = mock(FilterExecutionService.class);
        GpsTrackAndDataService trackAndDataService = mock(GpsTrackAndDataService.class);
        FilterParamsRequest request = requestWithSelectionAndTrackIds();
        when(filterExecutionService.executeOptionalFilterName("Years", request))
                .thenReturn(queryResult(entry(2L, "2025"), entry(3L, "2025")));
        when(filterExecutionService.standardFilterCount(1L)).thenReturn(3L);
        GpsTrack effectiveTrack = new GpsTrack();
        effectiveTrack.setId(2L);
        when(trackAndDataService.findAllGpsTracksWithData(eq(BigDecimal.TEN), any())).thenReturn(List.of(effectiveTrack));

        TracksSimplifiedResponse response = controller(repository, filterExecutionService, trackAndDataService)
                .getTracksSimplified(request, BigDecimal.TEN, "Years", "full");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> hydratedIds = ArgumentCaptor.forClass(List.class);
        verify(trackAndDataService).findAllGpsTracksWithData(eq(BigDecimal.TEN), hydratedIds.capture());
        assertThat(hydratedIds.getValue()).containsExactly(2L);
        assertThat(response.getFilteredTracks()).singleElement()
                .satisfies(track -> assertThat(track.getGpsTrack().getId()).isEqualTo(2L));
    }

    private static FilterParamsRequest requestWithSelectionAndTrackIds() {
        FilterResultGroupSelection selection = new FilterResultGroupSelection();
        selection.setIncludedGroups(List.of(FilterResultGroupKey.grouped("2025")));
        FilterParamsRequest request = new FilterParamsRequest();
        request.setResultGroupSelection(selection);
        request.setTrackIds(List.of(1L, 2L));
        return request;
    }

    private static QueryResult queryResult(QueryResult.QueryResultEntry... entries) {
        QueryResult result = new QueryResult();
        result.setResultEntries(List.of(entries));
        return result;
    }

    private static QueryResult.QueryResultEntry entry(Long id, String group) {
        QueryResult.QueryResultEntry entry = new QueryResult.QueryResultEntry();
        entry.setId(id);
        entry.setGroup(group);
        return entry;
    }

    private static NearbyTrackDistance nearbyTrack(Long trackId, Double distanceMeters) {
        return new NearbyTrackDistance() {
            @Override
            public Long getTrackId() {
                return trackId;
            }

            @Override
            public Double getDistanceMeters() {
                return distanceMeters;
            }
        };
    }

    private static TracksController controller(
            GpsTrackRepository repository,
            FilterExecutionService filterExecutionService,
            GpsTrackAndDataService trackAndDataService
    ) {
        return controller(repository, filterExecutionService, trackAndDataService, mock(TrackMediaQueryRepository.class));
    }

    private static TracksController controller(
            GpsTrackRepository repository,
            FilterExecutionService filterExecutionService,
            GpsTrackAndDataService trackAndDataService,
            TrackMediaQueryRepository trackMediaQueryRepository
    ) {
        return new TracksController(
                repository,
                mock(TrackTimeBetweenTwoPoints.class),
                trackAndDataService,
                mock(GpsTrackDataRepository.class),
                mock(GpsTrackDataPointRepository.class),
                mock(GpsTrackEventRepository.class),
                trackMediaQueryRepository,
                filterExecutionService,
                mock(EnergyService.class),
                mock(TrackFileExportService.class),
                mock(StatisticsMilestoneService.class),
                Runnable::run
        );
    }
}
