package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackFileConverterServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void geoJsonCommandTransformsRoutesIntoTracks() {
        List<String> command = TrackFileConverterService.buildGpsBabelCommand(
                Path.of("sample.geojson"),
                SupportedTrackFormat.GEOJSON);

        assertTrue(command.contains("-x"));
        assertTrue(command.contains("transform,trk=rte,del"));
    }

    @Test
    void nonGeoJsonCommandDoesNotAddRouteTransform() {
        List<String> command = TrackFileConverterService.buildGpsBabelCommand(
                Path.of("sample.tcx"),
                SupportedTrackFormat.TCX);

        assertFalse(command.contains("-x"));
        assertFalse(command.contains("transform,trk=rte,del"));
    }

    @Test
    void extractsKmlDocumentFromKmzArchive() throws Exception {
        Path kmz = tempDir.resolve("sample.kmz");
        String kml = "<?xml version=\"1.0\"?><kml><Document><name>sample</name></Document></kml>";
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(kmz))) {
            output.putNextEntry(new ZipEntry("files/readme.txt"));
            output.write("ignored".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("doc.kml"));
            output.write(kml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        Path extracted = TrackFileConverterService.extractKmlFromKmz(kmz, tempDir);

        assertEquals(kml, Files.readString(extracted));
    }

    @Test
    void prefersDocKmlWhenKmzArchiveContainsMultipleKmlDocuments() throws Exception {
        Path kmz = tempDir.resolve("multiple.kmz");
        String fallbackKml = "<?xml version=\"1.0\"?><kml><Document><name>fallback</name></Document></kml>";
        String docKml = "<?xml version=\"1.0\"?><kml><Document><name>main</name></Document></kml>";
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(kmz))) {
            output.putNextEntry(new ZipEntry("files/overlay.kml"));
            output.write(fallbackKml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("doc.kml"));
            output.write(docKml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        Path extracted = TrackFileConverterService.extractKmlFromKmz(kmz, tempDir);

        assertEquals(docKml, Files.readString(extracted));
    }
}
