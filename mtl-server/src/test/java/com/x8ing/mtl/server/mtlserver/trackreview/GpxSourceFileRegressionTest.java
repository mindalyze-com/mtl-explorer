package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.gpx.TrackFileConverterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GpxSourceFileRegressionTest {
    @TempDir
    Path directory;

    @Test
    void generatedDeviceExportReceivesCompleteAssessmentAndImport() throws Exception {
        // Runtime-only synthetic data: no GPX fixture is stored in the repository.
        int pointCount = 60_000;
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Path source = directory.resolve("synthetic-device-export.gpx");
        try (var writer = Files.newBufferedWriter(source)) {
            writer.write("""
                    <gpx version="1.1" creator="synthetic" xmlns="http://www.topografix.com/GPX/1/1"
                         xmlns:g="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
                      <trk><name>Synthetic device export</name><trkseg>
                    """);
            for (int i = 0; i < pointCount; i++) {
                writer.write("  <trkpt lat=\"20\" lon=\"" + (10 + i * 0.00001) + "\">\n"
                             + "    <ele>100</ele><time>" + start.plusSeconds(i * 2L) + "</time>\n"
                             + "    <extensions><g:TrackPointExtension>\n"
                             + "      <g:hr>100</g:hr><g:cad>80</g:cad><g:atemp>15</g:atemp>\n"
                             + "    </g:TrackPointExtension></extensions>\n"
                             + "  </trkpt>\n");
            }
            writer.write("</trkseg></trk></gpx>");
        }
        assertTrue(Files.size(source) > 10L * 1024 * 1024, "Exercise a large device export");
        assertTrue(Files.size(source) < GpxDocument.MAX_SOURCE_BYTES);
        System.out.printf("Generated synthetic GPX regression input: points=%d bytes=%d%n", pointCount, Files.size(source));
        String hash;
        try (var input = Files.newInputStream(source)) {
            hash = GpxDocument.hash(input);
        }

        IndexedFile file = new IndexedFile();
        file.setId(1L);
        file.setIndex("GPS");
        file.setName(source.getFileName().toString());
        file.setFullPath(source.toString());
        var files = mock(IndexerRepository.class);
        when(files.findById(1L)).thenReturn(Optional.of(file));
        var access = new TrackSourceAccess(files, mock(TrackFileConverterService.class), directory.toString());
        var service = new SourceReviewService(access, mock(SourceReviewRepository.class), directTransactions());

        // File access, XML review, diagnosis and import are real; database persistence is mocked.
        var assessed = service.assess(1L);
        assertFalse(SourceReviewService.assessmentLimited(assessed.diagnosis()),
                "A valid export must not fall back to an incomplete XML-complexity assessment");
        assertNotNull(assessed.document());
        assertEquals(pointCount, assessed.diagnosis().pointCount());
        assertEquals(pointCount, assessed.diagnosis().sections().getFirst().timedPointCount());
        assertFalse(assessed.diagnosis().findings().stream().anyMatch(f -> f.reason() == TrackFinding.Reason.PARSE_FAILED));
        assertEquals(hash, assessed.diagnosis().sourceHash());
        assertEquals(start.plusSeconds((pointCount - 1L) * 2), assessed.document().points().getLast().timestamp());

        var imported = SourceImportPlan.load(file, assessed);
        assertEquals(1, imported.size());
        var result = imported.getFirst();
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, result.gpsTrack.getLoadStatus());
        assertEquals(pointCount, result.trackRAW.getNumPoints());
        assertEquals("Synthetic device export", result.gpsTrack.getTrackName());
        assertEquals(start.getEpochSecond(), result.trackRAW.getCoordinateN(0).getM());
        assertEquals(start.plusSeconds((pointCount - 1L) * 2).getEpochSecond(),
                result.trackRAW.getCoordinateN(pointCount - 1).getM());
        service.applyDecision(assessed, result.gpsTrack);
        service.importCompleted(assessed, imported);
        assertEquals(TrackReviewModels.ReviewState.INCLUDED.name(), result.gpsTrack.getReviewStatus());
        assertFalse(result.gpsTrack.isReviewExcluded());
        try (var input = Files.newInputStream(source)) {
            assertEquals(hash, GpxDocument.hash(input));
        }
    }
}
