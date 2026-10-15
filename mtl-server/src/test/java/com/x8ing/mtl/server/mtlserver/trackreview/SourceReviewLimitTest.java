package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.gpx.TrackFileConverterService;
import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.web.security.DemoWriteAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions;
import static com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceReviewLimitTest {
    @TempDir
    Path directory;

    @Test
    void xmlNestingLimitKeepsTheOriginalRevisionAndDoesNotExcludeImportedTracks() throws Exception {
        Path path = Files.writeString(directory.resolve("complex-synthetic.gpx"),
                "<gpx><extensions>" + "<extra>".repeat(70) + "</extra>".repeat(70) + "</extensions></gpx>");
        var repository = mock(SourceReviewRepository.class);
        var service = new SourceReviewService(access(path), repository, directTransactions());
        var assessed = service.assess(1);
        assertNull(assessed.document());
        assertNull(assessed.source().bytes());
        assertTrue(SourceReviewService.assessmentLimited(assessed.diagnosis()));
        assertEquals(GpxDocument.hash(Files.readAllBytes(path)), assessed.diagnosis().sourceHash());
        var track = new GpsTrack();
        service.applyDecision(assessed, track);
        assertFalse(track.isReviewExcluded());
        assertFalse(track.isReviewMapExcluded());
        assertEquals(ReviewState.NOT_ASSESSED.name(), track.getReviewStatus());
        assertDoesNotThrow(() -> service.requireCurrentRevision(assessed));
    }

    @Test
    void limitedReimportRecognizesProvenanceKeepsRawAndDoesNotRestrictTheIndependentHealthySection() throws Exception {
        byte[] repaired = SyntheticTrackSources.document(SyntheticTrackSources.estimatedSection(SyntheticTrackSources.regular(200), 0),
                SyntheticTrackSources.section(SyntheticTrackSources.regular(200), 300));
        Path path = directory.resolve("large-repaired.gpx");
        byte[] padding = new byte[8192];
        Arrays.fill(padding, (byte) ' ');
        try (var output = new BufferedOutputStream(Files.newOutputStream(path))) {
            output.write(repaired);
            // Trailing XML whitespace is valid, and places repair provenance before the review size boundary.
            for (int n = 0; n < GpxDocument.MAX_SOURCE_BYTES; n += padding.length) output.write(padding);
        }
        var repository = mock(SourceReviewRepository.class);
        var persisted = new AtomicReference<SourceReviewRepository.Assessment>();
        when(repository.find(1L)).thenAnswer(call -> Optional.ofNullable(persisted.get()));
        doAnswer(call -> {
            persisted.set(new SourceReviewRepository.Assessment(1, call.getArgument(1), call.getArgument(2), Instant.now()));
            return null;
        }).when(repository).save(eq(1L), anyString(), any(), any());
        var access = access(path);
        var service = new SourceReviewService(access, repository, directTransactions());
        var assessed = service.assess(1);
        assertTrue(SourceReviewService.assessmentLimited(assessed.diagnosis()));
        var results = new GPXReader().importGpxFile(access.indexedFile(1));
        assertEquals(2, results.size());
        var estimated = results.get(0);
        var healthy = results.get(1);
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, estimated.gpsTrack.getLoadStatus());
        assertTrue(estimated.gpsTrack.isTimingEstimated());
        assertEquals(200, estimated.trackRAW.getNumPoints());
        var original = GpxDocument.parse(repaired);
        for (int i = 0; i < 200; i++) assertEquals(original.points().get(i).timestamp().getEpochSecond(), estimated.trackRAW.getCoordinateN(i).getM());
        assertTrue(Arrays.stream(estimated.trackCleaned.getCoordinates()).allMatch(p -> Double.isNaN(p.getM())));
        var control = new GPXReader().importGpxXml(access.indexedFile(1), new String(SyntheticTrackSources.document(
                SyntheticTrackSources.section(SyntheticTrackSources.regular(200), 300)), StandardCharsets.UTF_8)).getFirst();
        assertEquals(control.trackRAW.toText(), healthy.trackRAW.toText());
        assertEquals(control.trackCleaned.toText(), healthy.trackCleaned.toText());
        for (int i = 0; i < healthy.trackCleaned.getNumPoints(); i++)
            assertEquals(control.trackCleaned.getCoordinateN(i).getM(), healthy.trackCleaned.getCoordinateN(i).getM());

        service.importCompleted(assessed, results);
        assertEquals("NOT_ASSESSED", estimated.gpsTrack.getReviewStatus());
        assertFalse(estimated.gpsTrack.isReviewExcluded());
        assertEquals("NOT_ASSESSED", healthy.gpsTrack.getReviewStatus());
        assertFalse(healthy.gpsTrack.isReviewExcluded());
        assertFalse(healthy.gpsTrack.isTimingUnreliable());
        var reassessed = service.assess(1);
        assertTrue(reassessed.diagnosis().estimatedTiming());
        assertEquals(List.of(ReviewState.NOT_ASSESSED, ReviewState.NOT_ASSESSED), reassessed.sections().stream().map(SourceSectionReview::state).toList());
        assertEquals(200, reassessed.diagnosis().findings().stream().filter(f -> f.reason() == TrackFinding.Reason.TIME_ESTIMATED).findFirst().orElseThrow().affectedCount());
        assertDoesNotThrow(() -> service.requireCurrentRevision(reassessed));
    }

    @Test
    void navigationRoutePointsRemainImportableWithoutRequiringRepair() {
        String route = new String(SyntheticTrackSources.source(new java.util.ArrayList<>(java.util.Collections.nCopies(60, null))), StandardCharsets.UTF_8)
                .replace("<trk><name>Synthetic route</name><trkseg>", "<rte><name>Synthetic route</name>")
                .replace("</trkseg></trk>", "</rte>").replace("trkpt", "rtept");
        IndexedFile file = new IndexedFile();
        file.setId(1L);
        file.setName("navigation.gpx");
        var result = new GPXReader().importGpxXml(file, route).getFirst();
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, result.gpsTrack.getLoadStatus());
        assertEquals(60, result.trackRAW.getNumPoints());
        assertFalse(result.gpsTrack.isTimingEstimated());
        assertEquals(0, result.sourceRecordingEvidence.timedPointCount());
    }

    @Test
    void oversizedValidSourceRetainsRevisionDownloadAndNormalInclusionWithoutClaimingACompletedAssessment() throws Exception {
        Path path = directory.resolve("large-synthetic.gpx");
        // Synthetic GPX padded with legal XML whitespace; no private track data is used.
        try (var output = new BufferedOutputStream(Files.newOutputStream(path))) {
            output.write("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"synthetic\">".getBytes(StandardCharsets.UTF_8));
            byte[] whitespace = new byte[8192];
            Arrays.fill(whitespace, (byte) ' ');
            for (int written = 0; written < GpxDocument.MAX_SOURCE_BYTES; written += whitespace.length) output.write(whitespace);
            output.write("<trk><trkseg><trkpt lat=\"60\" lon=\"24\"/><trkpt lat=\"60.001\" lon=\"24.001\"/></trkseg></trk></gpx>".getBytes(StandardCharsets.UTF_8));
        }
        var access = access(path);
        var repository = mock(SourceReviewRepository.class);
        var service = new SourceReviewService(access, repository, directTransactions());
        var assessed = service.assess(1);
        assertNull(assessed.document());
        assertNull(assessed.source().bytes());
        assertTrue(SourceReviewService.assessmentLimited(assessed.diagnosis()));
        assertFalse(assessed.diagnosis().needsReview());
        assertEquals(ReviewState.NOT_ASSESSED, assessed.sections().getFirst().state());
        verify(repository).save(eq(1L), eq(assessed.source().hash()), any(), eq(ReviewState.NOT_ASSESSED));
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> access.read(1)).getStatusCode().value());
        assertDoesNotThrow(() -> service.requireCurrentRevision(assessed));

        GpsTrack healthy = new GpsTrack();
        healthy.setSourceRecordingSection(3);
        service.applyDecision(assessed, healthy);
        assertEquals("NOT_ASSESSED", healthy.getReviewStatus());
        assertFalse(healthy.isReviewExcluded());
        GpsTrack previouslyUnsupported = new GpsTrack();
        previouslyUnsupported.setTimingUnreliable(true);
        service.applyDecision(assessed, previouslyUnsupported);
        assertTrue(previouslyUnsupported.isTimingUnreliable(), "An incomplete check cannot clear existing metric restrictions.");

        var response = new SourceReviewController(service, access, new DemoWriteAccess(new MtlAppProperties())).original(1);
        assertNotNull(response.getBody());
        try (var download = response.getBody().getInputStream()) {
            assertEquals(assessed.source().hash(), GpxDocument.hash(download));
        }
        Files.writeString(path, "\n", StandardOpenOption.APPEND);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.requireCurrentRevision(assessed)).getStatusCode().value());
    }

    @Test
    void malformedSmallSourceStillRequiresReview() throws Exception {
        Path path = Files.writeString(directory.resolve("malformed.gpx"), "<gpx>");
        var assessed = new SourceReviewService(access(path), mock(SourceReviewRepository.class), directTransactions()).assess(1);
        assertNull(assessed.document());
        assertFalse(SourceReviewService.assessmentLimited(assessed.diagnosis()));
        assertTrue(assessed.diagnosis().needsReview());
        assertEquals(TrackFinding.Reason.PARSE_FAILED, assessed.diagnosis().findings().getFirst().reason());
    }

    @Test
    void failedConversionOffersTheOriginalWithoutClaimingAGpxDownloadOrEmptyRecording() throws Exception {
        Path path = Files.writeString(directory.resolve("synthetic.kml"), "<kml/>");
        var access = access(path);
        // Exercise the same controlled conversion failure as a missing converter executable.
        var source = access.read(1);
        var controlledAccess = spy(access);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Conversion failed"))
                .when(controlledAccess).gpx(any());
        var repository = mock(SourceReviewRepository.class);
        var service = new SourceReviewService(controlledAccess, repository, directTransactions());
        var assessed = service.assess(1);
        when(repository.find(1)).thenReturn(Optional.of(new SourceReviewRepository.Assessment(1, source.hash(), assessed.diagnosis(), Instant.now())));
        var detail = service.detail(1);
        assertFalse(detail.canDownloadGpx());
        assertTrue(detail.previewUnavailableReason().contains("could not convert"));
        assertArrayEquals(Files.readAllBytes(path), new SourceReviewController(service, controlledAccess, new DemoWriteAccess(new MtlAppProperties())).original(1).getBody().getContentAsByteArray());
    }

    @Test
    void anUnmappedTrackRejectsSectionChoicesButStillAllowsWholeSourceChoices() throws Exception {
        var times = SyntheticTrackSources.regular(90);
        Path path = Files.write(directory.resolve("synthetic-sections.gpx"), SyntheticTrackSources.document(
                SyntheticTrackSources.section(times, 0), SyntheticTrackSources.section(times, 90)));
        var repository = mock(SourceReviewRepository.class);
        var service = new SourceReviewService(access(path), repository, directTransactions());
        var assessed = service.assess(1);
        when(repository.find(1)).thenReturn(Optional.of(new SourceReviewRepository.Assessment(1, assessed.source().hash(), assessed.diagnosis(), Instant.now())));
        when(repository.tracks(1)).thenReturn(List.of(new ReviewedTrack(10, "Combined track", null, "INCLUDED")));
        var failure = assertThrows(ResponseStatusException.class, () -> service.decide(1,
                new ReviewDecisionRequest(assessed.source().hash(), 1, Decision.EXCLUDE_MAP)));
        assertEquals(409, failure.getStatusCode().value());
        verify(repository, never()).decide(any(), anyInt(), any());
        assertDoesNotThrow(() -> service.decide(1, new ReviewDecisionRequest(assessed.source().hash(), null, Decision.EXCLUDE_MAP)));
        verify(repository, times(2)).decide(any(), anyInt(), eq(Decision.EXCLUDE_MAP));
    }

    private TrackSourceAccess access(Path path) {
        IndexedFile file = new IndexedFile();
        file.setId(1L);
        file.setIndex("GPS");
        file.setFullPath(path.toString());
        file.setName(path.getFileName().toString());
        var files = mock(IndexerRepository.class);
        when(files.findById(1L)).thenReturn(Optional.of(file));
        return new TrackSourceAccess(files, mock(TrackFileConverterService.class), directory.toString());
    }
}
