package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceImportPlan;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GPXReaderTimestampTest {
    private static final Instant FILE_TIME = Instant.parse("2026-04-01T00:00:00Z");
    private static final double TIMESTAMP_TOLERANCE_SECONDS = 0.000001;
    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({
            "2026-01-01T10:00:%02dZ, 2026-01-01T10:00:00Z",
            "2026-01-01T10:00:%02d.00, 2026-01-01T10:00:00Z",
            "2026-01-01T10:00:%02d.12, 2026-01-01T10:00:00.120Z",
            "2026-01-01T10:00:%02d+02:00, 2026-01-01T08:00:00Z",
            "2026-01-01T10:00:%02d-05:30, 2026-01-01T15:30:00Z",
            "2026-01-01t10:00:%02dz, 2026-01-01T10:00:00Z"
    })
    void reviewXmlAndFileImportsAgreeWithoutLosingTiming(String template, String expected) throws Exception {
        String xml = document(List.of(template.formatted(0), template.formatted(10), template.formatted(20)));
        var reviewed = GpxDocument.parse(xml);
        var diagnosis = new TrackDiagnosticEngine().diagnose(reviewed);
        var assessed = new SourceReviewService.AssessedSource(null, reviewed, diagnosis, List.of());
        Path path = directory.resolve("synthetic.gpx");
        Files.writeString(path, xml);

        assertTrue(reviewed.problems().isEmpty());
        assertEquals(3, diagnosis.sections().getFirst().timedPointCount());
        assertFalse(SourceImportPlan.unreliableTiming(diagnosis, 0));
        for (var result : List.of(SourceImportPlan.load(file(path), assessed).getFirst(),
                new GPXReader().importGpxFile(file(path)).getFirst())) {
            assertGeometryRetained(result);
            assertFalse(result.gpsTrack.isTimingUnreliable());
            for (int i = 0; i < 3; i++) {
                Instant time = Instant.parse(expected).plusSeconds(i * 10L);
                assertEquals(time, reviewed.points().get(i).timestamp());
                assertEquals(time.toEpochMilli() / 1000.0, result.trackRAW.getCoordinateN(i).getM(), TIMESTAMP_TOLERANCE_SECONDS);
            }
        }
        assertEquals(xml, reviewed.sourceXml());
        assertEquals(template.formatted(0), reviewed.points().getFirst().sourceTimestamp());
        assertEquals(xml, Files.readString(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30T10:00:10Z", "2026-01-01T25:00:10Z", "2026-01-01T10:61:10Z",
            "2016-12-31T23:59:60Z", "2026-01-01T10:00:10+02:00:30", "not-a-date"})
    void invalidMiddleTimeIsMissingInBothParsersAndItsGeometrySurvives(String invalid) {
        String xml = document(List.of("2026-01-01T10:00:00Z", invalid, "2026-01-01T10:00:20Z"));
        var reviewed = GpxDocument.parse(xml);
        var result = new GPXReader().importGpxXml(file(null), xml).getFirst();

        assertGeometryRetained(result);
        assertNull(reviewed.points().get(1).timestamp());
        assertEquals(invalid, reviewed.points().get(1).sourceTimestamp());
        assertEquals(1, reviewed.problems().size());
        assertTrue(Double.isNaN(result.trackRAW.getCoordinateN(1).getM()));
        assertEquals(Instant.parse("2026-01-01T10:00:00Z").getEpochSecond(), result.trackRAW.getCoordinateN(0).getM());
        assertEquals(Instant.parse("2026-01-01T10:00:20Z").getEpochSecond(), result.trackRAW.getCoordinateN(2).getM());
    }

    @Test
    void entirelyInvalidTimesCannotBecomeMeasuredDates() {
        String xml = document(List.of("2026-02-30T10:00:00Z", "2026-02-30T10:00:10Z", "2026-02-30T10:00:20Z"));
        var diagnosis = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(xml));
        var result = new GPXReader().importGpxXml(file(null), xml).getFirst();

        assertGeometryRetained(result);
        assertEquals(0, diagnosis.sections().getFirst().timedPointCount());
        assertTrue(SourceImportPlan.unreliableTiming(diagnosis, 0));
        for (var coordinate : result.trackRAW.getCoordinates()) assertTrue(Double.isNaN(coordinate.getM()));
        assertEquals(FILE_TIME, result.gpsTrack.getStartDate().toInstant());
        assertEquals(FILE_TIME, result.gpsTrack.getEndDate().toInstant());
    }

    @Test
    void endOfDayHasTheSameMeaningInReviewAndImport() {
        String xml = document(List.of("2026-01-01T23:59:50Z", "2026-01-01T24:00:00.000Z", "2026-01-02T00:00:10Z"));
        var reviewed = GpxDocument.parse(xml);
        var result = new GPXReader().importGpxXml(file(null), xml).getFirst();
        assertGeometryRetained(result);
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), reviewed.points().get(1).timestamp());
        assertEquals(reviewed.points().get(1).timestamp().getEpochSecond(), result.trackRAW.getCoordinateN(1).getM());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://www.topografix.com/GPX/1/1", "http://www.topografix.com/GPX/1/0", ""})
    void routeTimesUseTheSameRulesAcrossGpxNamespaces(String namespace) {
        String xml = document(List.of("2026-01-01T10:00:00.00", "2026-01-01T10:00:10.00", "2026-01-01T10:00:20.00"))
                .replace("xmlns='http://www.topografix.com/GPX/1/1'", "xmlns='" + namespace + "'")
                .replace("<trk><trkseg>", "<rte>").replace("</trkseg></trk>", "</rte>")
                .replace("trkpt", "rtept");
        var reviewed = GpxDocument.parse(xml);
        var assessed = new SourceReviewService.AssessedSource(null, reviewed,
                new TrackDiagnosticEngine().diagnose(reviewed), List.of());
        var result = SourceImportPlan.load(file(null), assessed).getFirst();
        assertGeometryRetained(result);
        for (int i = 0; i < 3; i++)
            assertEquals(reviewed.points().get(i).timestamp().getEpochSecond(), result.trackRAW.getCoordinateN(i).getM());
    }

    @Test
    void oversizedTimeFieldsCannotGrowImportBuffersWithoutBound() {
        String xml = document(List.of("x".repeat(65 * 1024), "2026-01-01T10:00:10Z", "2026-01-01T10:00:20Z"));
        var result = new GPXReader().importGpxXml(file(null), xml).getFirst();
        assertGeometryRetained(result);
        assertTrue(Double.isNaN(result.trackRAW.getCoordinateN(0).getM()));
        assertEquals(Instant.parse("2026-01-01T10:00:10Z").getEpochSecond(), result.trackRAW.getCoordinateN(1).getM());
    }

    @Test
    void invalidMetadataTimeCannotSupplyAnInventedFallbackDate() {
        String xml = document(List.of("", "", "")).replace("<trk>", "<metadata><time>2026-02-30T10:00:00Z</time></metadata><trk>");
        var result = new GPXReader().importGpxXml(file(null), xml).getFirst();
        assertGeometryRetained(result);
        assertEquals(FILE_TIME, result.gpsTrack.getStartDate().toInstant());
    }

    @Test
    void metadataWithoutAnOffsetUsesUtcForTheFallback() {
        String xml = document(List.of("", "", "")).replace("<trk>", "<metadata><time>2026-01-01t10:00:00.00</time></metadata><trk>");
        var result = new GPXReader().importGpxXml(file(null), xml).getFirst();
        assertGeometryRetained(result);
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), result.gpsTrack.getStartDate().toInstant());
    }

    @Test
    void firstTimeFieldAndForeignExtensionTimesStayDistinct() {
        String xml = document(List.of("2026-01-01t10:00:00z", "2026-01-01t10:00:10z", "2026-01-01t10:00:20z"))
                .replace("</time>", "</time><time>invalid duplicate</time>")
                .replace("</trkpt>", "<extensions xmlns:x='urn:synthetic'><x:time>not a GPX clock</x:time></extensions></trkpt>");
        var reviewed = GpxDocument.parse(xml);
        var result = new GPXReader().importGpxXml(file(null), xml).getFirst();
        assertGeometryRetained(result);
        assertTrue(reviewed.problems().isEmpty());
        for (int i = 0; i < 3; i++)
            assertEquals(reviewed.points().get(i).timestamp().getEpochSecond(), result.trackRAW.getCoordinateN(i).getM());
    }

    private static void assertGeometryRetained(GPXReader.LoadResult result) {
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, result.gpsTrack.getLoadStatus());
        assertEquals(3, result.trackRAW.getNumPoints());
        for (int i = 0; i < 3; i++) {
            var coordinate = result.trackRAW.getCoordinateN(i);
            assertEquals(8.0, coordinate.getX());
            assertEquals(47.0 + i * 0.0001, coordinate.getY());
        }
    }

    private static IndexedFile file(Path path) {
        var file = new IndexedFile();
        file.setId(1L);
        file.setName("synthetic.gpx");
        file.setFullPath(path == null ? "/synthetic/not-read.gpx" : path.toString());
        file.setLastModifiedDate(Date.from(FILE_TIME));
        return file;
    }

    private static String document(List<String> times) {
        StringBuilder xml = new StringBuilder("<gpx version='1.1' creator='MTL Explorer synthetic test' xmlns='http://www.topografix.com/GPX/1/1'><trk><trkseg>");
        for (int i = 0; i < times.size(); i++)
            xml.append("<trkpt lat='").append(47.0 + i * 0.0001)
                    .append("' lon='8.0'><ele>500</ele><time>").append(times.get(i)).append("</time></trkpt>");
        return xml.append("</trkseg></trk></gpx>").toString();
    }
}
