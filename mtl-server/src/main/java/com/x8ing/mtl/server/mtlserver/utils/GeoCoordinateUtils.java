package com.x8ing.mtl.server.mtlserver.utils;

/**
 * Shared WGS-84 coordinate limits and lightweight planar conversion values.
 */
public final class GeoCoordinateUtils {

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;
    public static final double APPROX_METERS_PER_DEGREE_LATITUDE = 111_320.0;

    private GeoCoordinateUtils() {
    }

    public static boolean isValidLatitude(double latitude) {
        return Double.isFinite(latitude) && latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE;
    }

    public static boolean isValidLongitude(double longitude) {
        return Double.isFinite(longitude) && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    public static void requireValidLatitude(double latitude) {
        if (!isValidLatitude(latitude)) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
    }

    public static void requireValidLongitude(double longitude) {
        if (!isValidLongitude(longitude)) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }
}
