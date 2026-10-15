package com.x8ing.mtl.server.mtlserver.gpx;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.WayPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TrackFileConverterServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void geoJsonConversionPreservesCoordinateElevations() throws Exception {
        Path source = tempDir.resolve("elevation.geojson");
        String json = """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                  "properties":{"name":"Synthetic elevation","description":"A & B"},
                  "geometry":{"type":"LineString","coordinates":[
                    [7.44,46.95,520],[7.441,46.951,560],[7.442,46.952,540]]}}]}
                """;
        Files.writeString(source, json);

        String xml = new TrackFileConverterService().convertToGpx(source, SupportedTrackFormat.GEOJSON);
        GPX gpx = GPX.Reader.DEFAULT.read(new StreamSource(new StringReader(xml)));
        List<WayPoint> points = gpx.getTracks().stream().flatMap(track -> track.getSegments().stream())
                .flatMap(segment -> segment.getPoints().stream()).toList();

        assertEquals(List.of(520.0, 560.0, 540.0), points.stream()
                .map(point -> point.getElevation().map(Number::doubleValue).orElse(Double.NaN)).toList());
        assertEquals(List.of(7.44, 7.441, 7.442), points.stream()
                .map(point -> point.getLongitude().doubleValue()).toList());
        assertTrue(points.stream().allMatch(point -> point.getTime().isEmpty()));
        assertEquals("Synthetic elevation", gpx.getTracks().getFirst().getName().orElseThrow());
        assertEquals("A & B", gpx.getTracks().getFirst().getDescription().orElseThrow());
        assertEquals(json, Files.readString(source));
    }

    @Test
    void geoJsonKeepsDisconnectedLinesAndDoesNotInventMissingElevations() throws Exception {
        GPX gpx = convertGeoJson("""
                {"type":"MultiLineString","coordinates":[
                  [[7.44,46.95,0],[7.441,46.951,-12.5]],
                  [[8,47],[8.001,47.001,null]]
                ]}
                """);

        var segments = gpx.getTracks().getFirst().getSegments();
        assertEquals(2, segments.size());
        assertEquals(List.of(0.0, -12.5), segments.getFirst().getPoints().stream()
                .map(point -> point.getElevation().orElseThrow().doubleValue()).toList());
        assertEquals(2, segments.getLast().getPoints().size());
        assertTrue(segments.getLast().getPoints().stream().allMatch(point -> point.getElevation().isEmpty()));
        assertTrue(segments.stream().flatMap(segment -> segment.getPoints().stream())
                .allMatch(point -> point.getTime().isEmpty()));
    }

    @Test
    void geoJsonRetainsPointAndPolygonGeometryElevations() throws Exception {
        GPX gpx = convertGeoJson("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","geometry":{"type":"Point","coordinates":[7,46,4]},"properties":{"name":"Summit"}},
                  {"type":"Feature","geometry":{"type":"MultiPoint","coordinates":[[7,46,5],[8,47,6]]}},
                  {"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[7,46,10],[7.1,46,10],[7,46.1,10],[7,46,10]]]}},
                  {"type":"Feature","geometry":{"type":"MultiPolygon","coordinates":[
                    [[[8,47,20],[8.1,47,20],[8,47.1,20],[8,47,20]]],
                    [[[9,48,30],[9.1,48,30],[9,48.1,30],[9,48,30]]]
                  ]}}
                ]}
                """);

        assertEquals(List.of(4.0, 5.0, 6.0), gpx.getWayPoints().stream()
                .map(point -> point.getElevation().orElseThrow().doubleValue()).toList());
        assertEquals("Summit", gpx.getWayPoints().getFirst().getName().orElseThrow());
        assertEquals(3, gpx.getTracks().size());
        assertEquals(List.of(10.0, 20.0, 30.0), gpx.getTracks().stream()
                .map(track -> track.getSegments().getFirst().getPoints().getFirst().getElevation().orElseThrow().doubleValue())
                .toList());
        assertTrue(gpx.getTracks().stream().allMatch(track -> track.getSegments().getFirst().getPoints().size() == 4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[181,47,500]", "[7,91,500]", "[7,47,\"unknown\"]", "[7]", "[7,47,1e999]"})
    void geoJsonRejectsInvalidCoordinatesInsteadOfInventingValues(String position) {
        assertThrows(IOException.class, () -> convertGeoJson("{\"type\":\"LineString\",\"coordinates\":[" + position + "]}"));
    }

    private GPX convertGeoJson(String json) throws Exception {
        Path source = tempDir.resolve("geometry.geojson");
        Files.writeString(source, json);
        String xml = new TrackFileConverterService().convertToGpx(source, SupportedTrackFormat.GEOJSON);
        return GPX.Reader.DEFAULT.read(new StreamSource(new StringReader(xml)));
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
