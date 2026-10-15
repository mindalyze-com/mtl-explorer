package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.*;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GpxDocumentStreamingTest {
    @Test
    void sectionOrderNamesFirstFieldsAndRepairProvenanceArePreserved() {
        String source = """
                <gpx xmlns='http://www.topografix.com/GPX/1/1' xmlns:x='urn:synthetic'
                     xmlns:r='https://mytraillog.com/xmlschemas/repair/v1'>
                  <x:trk><x:trkseg><x:trkpt lat='0' lon='0'/></x:trkseg></x:trk>
                  <trk><trkseg>
                    <trkpt lat='20' lon='10'>
                      <time> 2026-01-01T00:00:00Z </time><time>invalid ignored duplicate</time>
                      <ele>1<x:unit>00</x:unit></ele><ele>invalid ignored duplicate</ele>
                      <extensions><x:device><r:time/></x:device><trkpt lat='0' lon='0'/></extensions>
                    </trkpt>
                  </trkseg><trkseg/><name>Late <x:label>track</x:label> name</name></trk>
                  <rte><rtept lat='21' lon='11'><time>invalid</time><ele>NaN</ele></rtept><name>Route</name></rte>
                  <trk><trkseg><trkpt lat='22' lon='12'/></trkseg></trk>
                </gpx>
                """;
        var document = GpxDocument.parse(source);

        assertEquals(List.of("Late track name", "Late track name", "Route", "Recording 4"),
                document.sections().stream().map(TrackSection::name).toList());
        assertEquals(List.of(1, 0, 1, 1), document.sections().stream().map(TrackSection::pointCount).toList());
        assertEquals(List.of(0, 2, 3), document.points().stream().map(TrackObservation::sectionIndex).toList());
        assertEquals("ROUTE", document.sections().get(2).kind());
        assertEquals(100.0, document.points().getFirst().elevationMetres());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), document.points().getFirst().timestamp());
        assertTrue(document.points().getFirst().estimatedTime());
        assertNull(document.points().get(1).timestamp());
        assertEquals(List.of("time", "elevation"), document.problems().stream().map(GpxDocument.ParseProblem::field).toList());
        assertEquals(source, document.sourceXml());
    }

    @Test
    void gpx10AndNamespaceFreeExportsRemainReadable() {
        for (String namespace : new String[]{"", " xmlns='http://www.topografix.com/GPX/1/0'"}) {
            var document = GpxDocument.parse("<gpx" + namespace + "><rte><rtept lat='20' lon='10'/></rte></gpx>");
            assertEquals(1, document.points().size());
            assertTrue(document.points().getFirst().hasPosition());
        }
    }

    @Test
    void originalEncodingExtensionsAndImmutableSourceSurviveReviewToImport() {
        for (String encoding : new String[]{"UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "ISO-8859-1"}) {
            String xml = "<?xml version='1.0' encoding='" + encoding + "'?>"
                         + richRecording(20).replace("Synthetic long recording", "Synthetic café")
                                 .replace("</gpx>", "<!-- preserved source comment --></gpx>");
            byte[] bytes = xml.getBytes(Charset.forName(encoding));
            String hash = GpxDocument.hash(bytes);
            var document = GpxDocument.parse(bytes);
            Arrays.fill(bytes, (byte) 0);

            assertEquals(hash, document.sourceHash());
            assertEquals(xml, document.sourceXml());
            assertEquals("Synthetic café", document.sections().getFirst().name());
            IndexedFile file = new IndexedFile();
            file.setId(1L);
            file.setName("synthetic-encoding.gpx");
            var assessed = new SourceReviewService.AssessedSource(null, document,
                    new TrackDiagnosticEngine().diagnose(document), List.of());
            var result = SourceImportPlan.load(file, assessed).getFirst();
            assertEquals(com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.LOAD_STATUS.SUCCESS, result.gpsTrack.getLoadStatus());
            assertEquals(20, result.trackRAW.getNumPoints());
            assertEquals("Synthetic café", result.gpsTrack.getTrackName());
        }
    }

    @Test
    void malformedXmlUnsupportedRootsAndDoctypesStillFail() {
        for (String xml : new String[]{"<gpx><trk></gpx>", "<other/>", "<gpx xmlns='urn:foreign'/>",
                "<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///unavailable'>]><gpx>&x;</gpx>"}) {
            assertThrows(GpxDocument.ParseFailure.class, () -> GpxDocument.parse(xml));
        }
    }

    @Test
    void longRecordingWithDeviceExtensionsCanBeFullyReviewed() {
        int pointCount = 20_000;
        byte[] source = richRecording(pointCount).getBytes(StandardCharsets.UTF_8);
        String hash = GpxDocument.hash(source);

        GpxDocument document = assertDoesNotThrow(() -> GpxDocument.parse(source));
        TrackDiagnosis diagnosis = new TrackDiagnosticEngine().diagnose(document);

        assertEquals(pointCount, diagnosis.pointCount());
        assertEquals(pointCount, diagnosis.sections().getFirst().timedPointCount());
        assertFalse(diagnosis.needsReview());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").plusSeconds((pointCount - 1L) * 2),
                document.points().getLast().timestamp());
        assertEquals(hash, document.sourceHash());
        assertEquals(hash, GpxDocument.hash(source));
    }

    @Test
    void extensionRichRecordingCanProceedFromReviewToImport() {
        byte[] source = richRecording(20_000).getBytes(StandardCharsets.UTF_8);
        GpxDocument document = GpxDocument.parse(source);
        var assessed = new SourceReviewService.AssessedSource(null, document,
                new TrackDiagnosticEngine().diagnose(document), List.of());
        IndexedFile file = new IndexedFile();
        file.setId(1L);
        file.setName("synthetic-extensions.gpx");

        var imported = assertDoesNotThrow(() -> SourceImportPlan.load(file, assessed));

        assertEquals(1, imported.size());
        assertEquals(com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.LOAD_STATUS.SUCCESS,
                imported.getFirst().gpsTrack.getLoadStatus());
        assertEquals(20_000, imported.getFirst().trackRAW.getNumPoints());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                imported.getFirst().trackRAW.getCoordinateN(0).getM());
    }

    private static String richRecording(int points) {
        StringBuilder xml = new StringBuilder("<gpx version='1.1' creator='synthetic' xmlns='http://www.topografix.com/GPX/1/1' "
                                              + "xmlns:g='http://www.garmin.com/xmlschemas/TrackPointExtension/v1'><trk><name>Synthetic long recording</name><trkseg>");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < points; i++) {
            xml.append("<trkpt lat='20' lon='").append(10 + i * 0.00001).append("'><ele>100</ele><time>")
                    .append(start.plusSeconds(i * 2L)).append("</time><extensions><g:TrackPointExtension>")
                    .append("<g:hr>100</g:hr><g:cad>80</g:cad><g:atemp>15</g:atemp>")
                    .append("</g:TrackPointExtension></extensions></trkpt>");
        }
        return xml.append("</trkseg></trk></gpx>").toString();
    }
}
