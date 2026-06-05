package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedTrackFormatTest {

    @Test
    void resolvesSupportedFormats() {
        assertEquals(SupportedTrackFormat.GPX, SupportedTrackFormat.fromPath(Path.of("track.gpx")));
        assertEquals(SupportedTrackFormat.FIT, SupportedTrackFormat.fromPath(Path.of("activity.fit")));
        assertEquals(SupportedTrackFormat.KMZ, SupportedTrackFormat.fromPath(Path.of("earth.KMZ")));
        assertEquals(SupportedTrackFormat.GEOJSON, SupportedTrackFormat.fromPath(Path.of("route.geojson")));
    }

    @Test
    void rejectsSbpBecauseBundledGpsBabelDoesNotSupportIt() {
        assertNull(SupportedTrackFormat.fromPath(Path.of("track.sbp")));
    }

    @Test
    void inclusionRegexExcludesSbp() {
        Pattern pattern = Pattern.compile(SupportedTrackFormat.inclusionRegex());

        assertTrue(pattern.matcher("route.kmz").matches());
        assertFalse(pattern.matcher("track.sbp").matches());
    }
}
