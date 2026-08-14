package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.utils.GeoCoordinateUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
        "coordinates"
})
public class GeoPointDto {

    /**
     * Coordinates in the source point's CRS.
     * pointLongLat uses [lng, lat]; pointXY uses EPSG:3857 [x, y].
     */
    private double[] coordinates;

    public static GeoPointDto from(Point point) {
        if (point == null) {
            return null;
        }
        Coordinate coordinate = point.getCoordinate();
        if (coordinate == null || !isFiniteCoordinate(coordinate.x, coordinate.y)) {
            return null;
        }
        if (Double.isFinite(coordinate.z)) {
            return new GeoPointDto(new double[]{coordinate.x, coordinate.y, coordinate.z});
        }
        return new GeoPointDto(new double[]{coordinate.x, coordinate.y});
    }

    public static GeoPointDto fromLongLat(Point point) {
        GeoPointDto dto = from(point);
        if (dto == null || !isValidLongLat(dto.coordinates[0], dto.coordinates[1])) {
            return null;
        }
        return dto;
    }

    private static boolean isFiniteCoordinate(double x, double y) {
        return Double.isFinite(x) && Double.isFinite(y);
    }

    private static boolean isValidLongLat(double lng, double lat) {
        return GeoCoordinateUtils.isValidLongitude(lng) && GeoCoordinateUtils.isValidLatitude(lat);
    }
}
