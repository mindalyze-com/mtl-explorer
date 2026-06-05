package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingPointsResponseDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GeoPointDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GpsTrackDataPointDto;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
