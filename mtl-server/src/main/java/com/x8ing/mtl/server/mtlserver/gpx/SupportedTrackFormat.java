package com.x8ing.mtl.server.mtlserver.gpx;

import lombok.Getter;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Enumerates all GPS track file formats supported for ingestion.
 * Each entry maps a file extension to its GPSBabel input format name.
 * <p>
 * GPX is the native format and does not require conversion.
 * All other formats are converted to GPX via GPSBabel before parsing.
 */
@Getter
public enum SupportedTrackFormat {

    GPX("gpx", null),                    // native — no conversion needed
    FIT("fit", "garmin_fit"),            // Garmin FIT binary
    TCX("tcx", "gtrnctr"),              // Garmin Training Center XML
    KML("kml", "kml"),                  // Google Earth KML
    KMZ("kmz", "kmz"),                  // Google Earth KMZ (zipped KML)
    IGC("igc", "igc"),                  // FAI glider/paraglider logger
    SBP("sbp", "sbp"),                  // NaviGPS binary
    NMEA("nmea", "nmea"),               // NMEA 0183 sentences
    GEOJSON("geojson", "geojson"),      // GeoJSON
    GDB("gdb", "gdb"),                  // Garmin MapSource GDB
    ;

    private final String extension;
    /**
     * -- GETTER --
     *  The GPSBabel
     *  format identifier, or
     *  for GPX (no conversion needed).
     */
    private final String gpsBabelFormat;

    SupportedTrackFormat(String extension, String gpsBabelFormat) {
        this.extension = extension;
        this.gpsBabelFormat = gpsBabelFormat;
    }

    public boolean needsConversion() {
        return gpsBabelFormat != null;
    }

    /**
     * Resolves the format from a file path's extension (case-insensitive).
     *
     * @return the matching format, or {@code null} if the extension is not supported
     */
    public static SupportedTrackFormat fromPath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (SupportedTrackFormat fmt : values()) {
            if (name.endsWith("." + fmt.extension)) {
                return fmt;
            }
        }
        return null;
    }

    /**
     * Returns a regex fragment matching all supported extensions (case-insensitive),
     * suitable for use in a Java NIO {@code PathMatcher}.
     * Example output: {@code .*(?i)\.(gpx|fit|tcx|kml|kmz|igc|sbp|nmea)$}
     */
    public static String inclusionRegex() {
        StringBuilder sb = new StringBuilder(".*(?i)\\.(");
        SupportedTrackFormat[] values = values();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(values[i].extension);
        }
        sb.append(")$");
        return sb.toString();
    }
}
