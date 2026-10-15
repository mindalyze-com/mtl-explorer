package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConvertedSourceReviewTest {
    final TrackSourceAccess access = mock(TrackSourceAccess.class);
    final SourceReviewRepository repository = mock(SourceReviewRepository.class);
    final SourceReviewService service = new SourceReviewService(access, repository, directTransactions());
    final AtomicReference<SourceReviewRepository.Assessment> stored = new AtomicReference<>();
    final Instant start = Instant.parse("2026-01-01T00:00:00Z");
    TrackSourceAccess.Source source;

    @BeforeEach
    void setup() {
        var file = new IndexedFile();
        file.setId(123L);
        file.setName("synthetic.tcx");
        var bytes = "synthetic original TCX bytes".getBytes(StandardCharsets.UTF_8);
        source = new TrackSourceAccess.Source(file, Path.of("/synthetic.tcx"), bytes, GpxDocument.hash(bytes), SupportedTrackFormat.TCX);
        var xml = "<gpx version=\"1.1\" creator=\"synthetic\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>"
                  + "<trkpt lat=\"47\" lon=\"8\"><ele>500</ele><time>2026-01-01T00:00:00Z</time></trkpt>"
                  + "<trkpt lat=\"47.0001\" lon=\"8\"><ele>500</ele><time>2026-01-01T00:00:10Z</time></trkpt>"
                  + "</trkseg></trk></gpx>";
        when(access.readForAssessment(123)).thenReturn(source);
        when(access.gpx(source)).thenReturn(xml.getBytes(StandardCharsets.UTF_8));
        when(access.gpxForImport(source)).thenReturn(xml.getBytes(StandardCharsets.UTF_8));
        when(repository.find(123)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        doAnswer(call -> {
            stored.set(new SourceReviewRepository.Assessment(123L, call.getArgument(1), call.getArgument(2), Instant.now()));
            return null;
        })
                .when(repository).save(eq(123L), anyString(), any(), any());
    }

    @Test
    void convertedSourceUsesOriginalRevisionForProcessingChecks() {
        var assessed = service.assess(123);
        assertEquals(source.hash(), assessed.diagnosis().sourceHash());
        var track = new GpsTrack();
        track.setLoadStatus(GpsTrack.LOAD_STATUS.SUCCESS);
        track.setSourceRecordingSection(0);
        track.setStartDate(Date.from(start.minusSeconds(86400)));
        track.setEndDate(Date.from(start.plusSeconds(10)));
        track.setTrackDurationInMotionSecs(10d);
        track.setTrackLengthInMeter(11d);
        service.applyDecision(assessed, track);
        var result = new GPXReader.LoadResult();
        result.gpsTrack = track;
        service.importCompleted(assessed, List.of(result));
        assertTrue(stored.get().diagnosis().findings().stream().anyMatch(f -> f.reason() == TrackFinding.Reason.INCONSISTENT_TIME_DOMAIN),
                "Converted sources must detect processed dates outside the original observations");
    }

    @Test
    void convertedImportFailureRemainsReviewable() {
        var assessed = service.assess(123);
        assertEquals(source.hash(), assessed.diagnosis().sourceHash());
        // GpxProcessingWorker records the diagnosis hash after rollback.
        service.importFailed(new SourceImportFailure(123, assessed.diagnosis().sourceHash(), "IllegalStateException", "Synthetic failure"));
        assertTrue(stored.get().diagnosis().findings().stream().anyMatch(f -> f.reason() == TrackFinding.Reason.PROCESSING_FAILED),
                "A failure of the current converted source must be recorded, not skipped as superseded");
    }

    @Test
    void backgroundAssessmentUsesItsConversionAllowanceWithoutChangingTheDiagnosis() {
        var interactive = service.assess(123);
        var background = service.assessForImport(123);

        assertEquals(interactive.diagnosis(), background.diagnosis());
        assertEquals(source.hash(), background.diagnosis().sourceHash());
        verify(access).gpx(source);
        verify(access).gpxForImport(source);
    }
}
