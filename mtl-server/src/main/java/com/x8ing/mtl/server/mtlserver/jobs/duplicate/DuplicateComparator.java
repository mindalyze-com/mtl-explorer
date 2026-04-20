package com.x8ing.mtl.server.mtlserver.jobs.duplicate;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;

public class DuplicateComparator implements Comparator<GpsTrack> {

    private static final List<String> CONVERTER_TOOLS = List.of("gpsbabel");

    private static final List<String> KNOWN_SOURCE_APPS = List.of(
            "garmin connect", "strava", "komoot", "wahoo", "polar"
    );

    /**
     * Returns true if the creator string identifies a converter tool (not an originating source).
     * Converter tools produce derived files, so they should never be preferred as the UNIQUE track.
     */
    static boolean isConverterTool(String creator) {
        if (creator == null) return false;
        String lower = creator.toLowerCase();
        return CONVERTER_TOOLS.stream().anyMatch(lower::contains);
    }

    /**
     * Returns true if the creator string identifies a known originating source app.
     */
    static boolean isKnownSourceApp(String creator) {
        if (creator == null) return false;
        String lower = creator.toLowerCase();
        return KNOWN_SOURCE_APPS.stream().anyMatch(lower::contains);
    }

    /**
     * Parses the GPX version string as a double score. Higher versions are preferred (1.1 > 1.0).
     * Returns 0.0 for null or unparseable values.
     */
    static double gpxVersionScore(String gpxVersion) {
        if (gpxVersion == null) return 0.0;
        try {
            return Double.parseDouble(gpxVersion.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @Override
    public int compare(GpsTrack o1, GpsTrack o2) {

        if (o1 == null) {
            return -1;
        }

        if (o2 == null) {
            return 1;
        }

        return Comparator
                .<GpsTrack>nullsLast(Comparator.comparingInt(GpsTrack::getNumberOfTrackPoints).reversed()) // More points are more important
                .thenComparing(g -> !StringUtils.isBlank(g.getTrackName()), Comparator.reverseOrder()) // Non-empty track name is more important
                .thenComparing(g -> !StringUtils.isBlank(g.getTrackDescription()), Comparator.reverseOrder()) // Non-empty track description is more important
                .thenComparing(g -> g.getActivityType() != null, Comparator.reverseOrder()) // Non-null activity type is more important
                .thenComparing(g -> isConverterTool(g.getCreator()), Comparator.naturalOrder()) // Converter tools (e.g. GPSBabel) are less preferred than originals
                .thenComparing(g -> isKnownSourceApp(g.getCreator()), Comparator.reverseOrder()) // Known source apps (e.g. Garmin Connect) are preferred
                .thenComparingDouble(g -> -gpxVersionScore(g.getGpxVersion())) // Higher GPX version is more important (negated for descending)
                .thenComparing(g -> g.getDuplicateOf() == null, Comparator.reverseOrder()) // Not marked as duplicate is more important
                .compare(o1, o2);
    }
}