package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.xmp.XmpDirectory;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves embedded coordinates across image, MP4, and QuickTime metadata. */
final class MediaGpsLocationResolver {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;
    private static final Pattern ISO_6709_COORDINATES = Pattern.compile(
            "^([+-]\\d{2}(?:\\.\\d+)?)([+-]\\d{3}(?:\\.\\d+)?)");
    private static final String XMP_GPS_LATITUDE = "gpslatitude";
    private static final String XMP_GPS_LONGITUDE = "gpslongitude";

    private MediaGpsLocationResolver() {
    }

    static Location resolve(Metadata metadata) {
        GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gpsDirectory != null) {
            GeoLocation geoLocation = gpsDirectory.getGeoLocation();
            if (geoLocation != null) {
                Location location = validLocation(geoLocation.getLatitude(), geoLocation.getLongitude());
                if (location != null) return location;
            }
        }

        for (Mp4Directory directory : metadata.getDirectoriesOfType(Mp4Directory.class)) {
            Double latitude = directory.getDoubleObject(Mp4Directory.TAG_LATITUDE);
            Double longitude = directory.getDoubleObject(Mp4Directory.TAG_LONGITUDE);
            if (latitude == null || longitude == null) continue;
            Location location = validLocation(latitude, longitude);
            if (location != null) return location;
        }

        for (QuickTimeMetadataDirectory directory
                : metadata.getDirectoriesOfType(QuickTimeMetadataDirectory.class)) {
            Location location = parseIso6709(
                    directory.getString(QuickTimeMetadataDirectory.TAG_LOCATION_ISO6709));
            if (location != null) return location;
        }

        for (XmpDirectory directory : metadata.getDirectoriesOfType(XmpDirectory.class)) {
            Double latitude = findXmpCoordinate(directory, XMP_GPS_LATITUDE);
            Double longitude = findXmpCoordinate(directory, XMP_GPS_LONGITUDE);
            if (latitude == null || longitude == null) continue;
            Location location = validLocation(latitude, longitude);
            if (location != null) return location;
        }

        return null;
    }

    private static Double findXmpCoordinate(XmpDirectory directory, String targetProperty) {
        for (Map.Entry<String, String> property : directory.getXmpProperties().entrySet()) {
            if (!normalizedPropertyName(property.getKey()).endsWith(targetProperty)) continue;
            return parseXmpCoordinate(property.getValue());
        }
        return null;
    }

    private static String normalizedPropertyName(String propertyName) {
        return propertyName == null ? "" : propertyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static Double parseXmpCoordinate(String rawValue) {
        if (rawValue == null) return null;
        String value = rawValue.replace("°", "").trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) return null;

        double direction = 1.0;
        char suffix = value.charAt(value.length() - 1);
        if (suffix == 'N' || suffix == 'S' || suffix == 'E' || suffix == 'W') {
            direction = suffix == 'S' || suffix == 'W' ? -1.0 : 1.0;
            value = value.substring(0, value.length() - 1).trim();
        }

        String[] parts = value.split("[,\\s]+", -1);
        try {
            double degrees = Math.abs(Double.parseDouble(parts[0]));
            if (parts.length > 1 && !parts[1].isBlank()) degrees += Double.parseDouble(parts[1]) / 60.0;
            if (parts.length > 2 && !parts[2].isBlank()) degrees += Double.parseDouble(parts[2]) / 3_600.0;
            if (Double.parseDouble(parts[0]) < 0) direction = -1.0;
            return direction * degrees;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Location parseIso6709(String rawValue) {
        if (rawValue == null) return null;
        Matcher matcher = ISO_6709_COORDINATES.matcher(rawValue.replace("\0", "").trim());
        if (!matcher.find()) return null;

        try {
            return validLocation(
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Location validLocation(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < MIN_LATITUDE || latitude > MAX_LATITUDE
                || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            return null;
        }
        return new Location(latitude, longitude);
    }

    record Location(double latitude, double longitude) {
    }
}
