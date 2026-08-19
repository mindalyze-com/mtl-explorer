package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackAndDataService;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaQueryRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.TrackTimeBetweenTwoPoints;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsRequest;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterExecutionService;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingPointsResponseDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GeoPointDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GpsTrackDataPointDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterParamsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterRequestBean;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupKey;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupSelection;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracksControllerCrossingContractTest {

    @Test
    void crossingPointsEndpointUsesDtoContractWithNumericCoordinates() throws Exception {
        Method crossingPointsMethod = TracksController.class.getMethod("getCrossingPoints", CrossingPointsRequest.class);

        assertThat(crossingPointsMethod.getReturnType()).isEqualTo(CrossingPointsResponseDto.class);
        assertThat(CrossingDto.class.getDeclaredField("gpsTrackDataPoint").getType())
                .isEqualTo(GpsTrackDataPointDto.class);
        assertThat(GpsTrackDataPointDto.class.getDeclaredField("pointLongLat").getType())
                .isEqualTo(GeoPointDto.class);
        assertThat(GeoPointDto.class.getDeclaredField("coordinates").getType())
                .isEqualTo(double[].class);
    }

    @Test
    void subTrackEndpointUsesDtoContractWithNumericCoordinates() throws Exception {
        Method subTrackMethod = TracksController.class.getMethod("getSubTrackDetails", Long.class, Long.class, boolean.class);

        assertThat(listElementType(subTrackMethod)).isEqualTo(GpsTrackDataPointDto.class);
    }

    @Test
    void gpsTrackDataPointDtoMapsJtsPointsToNumericCoordinateArrays() {
        GeometryFactory longLatFactory = new GeometryFactory(new PrecisionModel(), 4326);
        GeometryFactory webMercatorFactory = new GeometryFactory(new PrecisionModel(), 3857);
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setPointLongLat(longLatFactory.createPoint(new Coordinate(8.5, 47.5, 430.0)));
        point.setPointXY(webMercatorFactory.createPoint(new Coordinate(946_215.671, 6_024_072.119)));

        GpsTrackDataPointDto dto = GpsTrackDataPointDto.from(point);

        assertThat(dto.getPointLongLat().getCoordinates()).containsExactly(8.5, 47.5, 430.0);
        assertThat(dto.getPointXY().getCoordinates()).containsExactly(946_215.671, 6_024_072.119);
    }

    @Test
    void subTrackEndpointUsesChronologicalGeometryWhenTimedPointIndexesRunBackwards() {
        GpsTrackDataPointRepository pointRepository = mock(GpsTrackDataPointRepository.class);
        long trackDataId = 9L;
        Date startTime = new Date(1_700_000_000_000L);
        Date middleTime = new Date(startTime.getTime() + 5_000L);
        Date endTime = new Date(startTime.getTime() + 10_000L);
        GpsTrackDataPoint from = point(30L, trackDataId, 3, startTime, 500, 0);
        GpsTrackDataPoint middle = point(20L, trackDataId, 2, middleTime, 550, 50);
        GpsTrackDataPoint to = point(10L, trackDataId, 1, endTime, 100, 100);
        from.setDurationSinceStart(-10d);
        when(pointRepository.findById(30L)).thenReturn(Optional.of(from));
        when(pointRepository.findById(10L)).thenReturn(Optional.of(to));
        when(pointRepository.getSubTrackDataByTimestamp(trackDataId, startTime, endTime))
                .thenReturn(List.of(from, middle, to));

        List<GpsTrackDataPointDto> result = controller(
                mock(FilterExecutionService.class),
                mock(TrackTimeBetweenTwoPoints.class),
                pointRepository)
                .getSubTrackDetails(30L, 10L, false)
                .getBody();

        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(GpsTrackDataPointDto::getDistanceInMeterSinceStart)
                .isSorted();
        assertThat(result)
                .extracting(GpsTrackDataPointDto::getDurationSinceStart)
                .containsExactly(-10.0, -5.0, 0.0);
        assertThat(result.getLast().getDistanceInMeterSinceStart()).isGreaterThan(590);
        verify(pointRepository).getSubTrackDataByTimestamp(trackDataId, startTime, endTime);
    }

    @Test
    void crossingPointsEndpointResolvesDefaultFilterWhenRequestFilterIsMissing() {
        FilterExecutionService filter = mock(FilterExecutionService.class);
        TrackTimeBetweenTwoPoints crossingService = mock(TrackTimeBetweenTwoPoints.class);
        CrossingPointsRequest request = new CrossingPointsRequest();
        when(filter.executeStandardFilter())
                .thenReturn(queryResult(7L, 11L));

        controller(filter, crossingService).getCrossingPoints(request);

        ArgumentCaptor<Long[]> filterIds = ArgumentCaptor.forClass(Long[].class);
        verify(crossingService).getTrackTimeBetweenPoints(same(request), filterIds.capture());
        assertThat(filterIds.getValue()).containsExactly(7L, 11L);
    }

    @Test
    void crossingPointsEndpointPassesTypedSelectionAndEffectiveIds() {
        FilterExecutionService filter = mock(FilterExecutionService.class);
        TrackTimeBetweenTwoPoints crossingService = mock(TrackTimeBetweenTwoPoints.class);
        FilterResultGroupSelection selection = new FilterResultGroupSelection();
        selection.setIncludedGroups(List.of(FilterResultGroupKey.grouped("WALKING")));
        FilterParamsRequest params = new FilterParamsRequest();
        params.setResultGroupSelection(selection);
        FilterRequestBean filterRequest = new FilterRequestBean();
        filterRequest.setFilterName("Activities");
        filterRequest.setParams(params);
        CrossingPointsRequest request = new CrossingPointsRequest();
        request.setFilter(filterRequest);
        when(filter.executeOptionalFilterName("Activities", params)).thenReturn(queryResult(7L, 11L));

        controller(filter, crossingService).getCrossingPoints(request);

        verify(filter).executeOptionalFilterName("Activities", params);
        ArgumentCaptor<Long[]> filterIds = ArgumentCaptor.forClass(Long[].class);
        verify(crossingService).getTrackTimeBetweenPoints(same(request), filterIds.capture());
        assertThat(filterIds.getValue()).containsExactly(7L, 11L);
    }

    private static Class<?> listElementType(Method method) {
        assertThat(method.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
        ParameterizedType responseType = (ParameterizedType) method.getGenericReturnType();
        assertThat(responseType.getRawType()).isEqualTo(ResponseEntity.class);
        assertThat(responseType.getActualTypeArguments()[0]).isInstanceOf(ParameterizedType.class);

        ParameterizedType listType = (ParameterizedType) responseType.getActualTypeArguments()[0];
        assertThat(listType.getRawType()).isEqualTo(List.class);
        assertThat(listType.getActualTypeArguments()[0]).isInstanceOf(Class.class);
        return (Class<?>) listType.getActualTypeArguments()[0];
    }

    private static TracksController controller(FilterExecutionService filter, TrackTimeBetweenTwoPoints crossingService) {
        return controller(filter, crossingService, mock(GpsTrackDataPointRepository.class));
    }

    private static TracksController controller(FilterExecutionService filter,
                                               TrackTimeBetweenTwoPoints crossingService,
                                               GpsTrackDataPointRepository pointRepository) {
        return new TracksController(
                mock(GpsTrackRepository.class),
                crossingService,
                mock(GpsTrackAndDataService.class),
                mock(GpsTrackDataRepository.class),
                pointRepository,
                mock(GpsTrackEventRepository.class),
                mock(TrackMediaQueryRepository.class),
                filter,
                mock(EnergyService.class),
                mock(TrackFileExportService.class),
                mock(StatisticsMilestoneService.class),
                Runnable::run
        );
    }

    private static GpsTrackDataPoint point(Long id,
                                           long trackDataId,
                                           int pointIndex,
                                           Date timestamp,
                                           double cumulativeDistance,
                                           double xMeters) {
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setId(id);
        point.setGpsTrackDataId(trackDataId);
        point.setPointIndex(pointIndex);
        point.setPointTimestamp(timestamp);
        point.setDistanceInMeterSinceStart(cumulativeDistance);
        point.setDurationSinceStart(0d);
        point.setPointLongLat(new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(xMeters / 111_320.0, 0)));
        return point;
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
}
