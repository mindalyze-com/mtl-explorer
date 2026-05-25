package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.GpsTrackBounds;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import org.springframework.stereotype.Service;

@Service
@JsonPropertyOrder({
        "gpsTrackRepository"
})
public class InitialMapViewportService {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;
    // Luzern/Lucerne city center.
    private static final double DEFAULT_CENTER_LNG = 8.30635;
    private static final double DEFAULT_CENTER_LAT = 47.05048;
    private static final double DEFAULT_BOUNDS_LNG_SPAN = 0.88;
    private static final double DEFAULT_BOUNDS_LAT_SPAN = 0.34;
    private static final double MIN_BOUNDS_SPAN_DEGREES = 0.01;

    private final GpsTrackRepository gpsTrackRepository;

    public InitialMapViewportService(GpsTrackRepository gpsTrackRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
    }

    public MapBoundsDto resolve(MapServerProperties properties) {
        if (hasValidBounds(properties.getInitialBounds())) {
            return expandTinyBounds(properties.getInitialBounds());
        }

        GpsTrackBounds trackBounds = gpsTrackRepository.findImportedTrackBounds(
                MIN_LATITUDE,
                MAX_LATITUDE,
                MIN_LONGITUDE,
                MAX_LONGITUDE);
        if (hasBounds(trackBounds)) {
            return expandTinyBounds(trackBounds);
        }

        return defaultBounds();
    }

    private static boolean hasBounds(GpsTrackBounds bounds) {
        return bounds != null
               && bounds.getMinLng() != null
               && bounds.getMinLat() != null
               && bounds.getMaxLng() != null
               && bounds.getMaxLat() != null;
    }

    private static boolean hasValidBounds(MapBoundsDto bounds) {
        return bounds != null
               && Double.isFinite(bounds.getMinLng())
               && Double.isFinite(bounds.getMinLat())
               && Double.isFinite(bounds.getMaxLng())
               && Double.isFinite(bounds.getMaxLat())
               && bounds.getMinLng() < bounds.getMaxLng()
               && bounds.getMinLat() < bounds.getMaxLat()
               && bounds.getMinLng() >= MIN_LONGITUDE
               && bounds.getMaxLng() <= MAX_LONGITUDE
               && bounds.getMinLat() >= MIN_LATITUDE
               && bounds.getMaxLat() <= MAX_LATITUDE;
    }

    private static MapBoundsDto expandTinyBounds(GpsTrackBounds bounds) {
        return expandTinyBounds(bounds.getMinLng(), bounds.getMinLat(), bounds.getMaxLng(), bounds.getMaxLat());
    }

    private static MapBoundsDto expandTinyBounds(MapBoundsDto bounds) {
        return expandTinyBounds(bounds.getMinLng(), bounds.getMinLat(), bounds.getMaxLng(), bounds.getMaxLat());
    }

    private static MapBoundsDto expandTinyBounds(double minLng, double minLat, double maxLng, double maxLat) {
        double[] lngBounds = expandAxis(minLng, maxLng, MIN_LONGITUDE, MAX_LONGITUDE);
        double[] latBounds = expandAxis(minLat, maxLat, MIN_LATITUDE, MAX_LATITUDE);
        return new MapBoundsDto(lngBounds[0], latBounds[0], lngBounds[1], latBounds[1]);
    }

    private static MapBoundsDto defaultBounds() {
        double halfLngSpan = DEFAULT_BOUNDS_LNG_SPAN / 2.0;
        double halfLatSpan = DEFAULT_BOUNDS_LAT_SPAN / 2.0;
        return new MapBoundsDto(
                DEFAULT_CENTER_LNG - halfLngSpan,
                DEFAULT_CENTER_LAT - halfLatSpan,
                DEFAULT_CENTER_LNG + halfLngSpan,
                DEFAULT_CENTER_LAT + halfLatSpan);
    }

    private static double[] expandAxis(double min, double max, double lowerLimit, double upperLimit) {
        double span = max - min;
        if (span >= MIN_BOUNDS_SPAN_DEGREES) {
            return new double[]{min, max};
        }
        double center = (min + max) / 2.0;
        double halfSpan = MIN_BOUNDS_SPAN_DEGREES / 2.0;
        double expandedMin = center - halfSpan;
        double expandedMax = center + halfSpan;
        if (expandedMin < lowerLimit) {
            expandedMax += lowerLimit - expandedMin;
            expandedMin = lowerLimit;
        }
        if (expandedMax > upperLimit) {
            expandedMin -= expandedMax - upperLimit;
            expandedMax = upperLimit;
        }
        return new double[]{
                clamp(expandedMin, lowerLimit, upperLimit),
                clamp(expandedMax, lowerLimit, upperLimit)
        };
    }

    private static double clamp(double value, double lowerLimit, double upperLimit) {
        return Math.max(lowerLimit, Math.min(upperLimit, value));
    }

}
