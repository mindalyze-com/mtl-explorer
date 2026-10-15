package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosis;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewService;
import com.x8ing.mtl.server.mtlserver.utils.TimingCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GpxProcessingWorkerTest {

    private static final long FILE_ID = 7L;

    @TempDir
    Path sourceDirectory;

    private final IndexerRepository indexerRepository = mock(IndexerRepository.class);
    private final GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
    private final GPXStoreService gpsStoreService = mock(GPXStoreService.class);
    private final TrackFileConverterService converterService = mock(TrackFileConverterService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EnergyRecalculationQueue energyQueue = mock(EnergyRecalculationQueue.class);
    private final SourceReviewService sourceReview = mock(SourceReviewService.class);
    private final GpxProcessingWorker worker = new GpxProcessingWorker(
            indexerRepository,
            gpsTrackRepository,
            gpsStoreService,
            converterService,
            eventPublisher, energyQueue, sourceReview, directTransactions(), mock(com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueIngestService.class));

    @BeforeEach
    void setUp() throws IOException {
        IndexedFile file = new IndexedFile();
        file.setId(FILE_ID);
        file.setName("synthetic.gpx");
        file.setPath("");
        Path source = sourceDirectory.resolve("synthetic.gpx");
        Files.writeString(source, "<gpx/>");
        file.setBasePath(sourceDirectory.toString());
        file.setFullPath(source.toString());
        file.setSize(Files.size(source));
        file.setLastModifiedDate(new Date(Files.getLastModifiedTime(source).toMillis()));
        when(indexerRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
        var document = GpxDocument.parse("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"synthetic\"><trk><trkseg><trkpt lat=\"47\" lon=\"8\"><time>2026-10-11T10:00:00Z</time></trkpt></trkseg></trk></gpx>");
        when(sourceReview.assessForImport(FILE_ID)).thenReturn(new SourceReviewService.AssessedSource(null, document,
                new TrackDiagnosticEngine().diagnose(document), List.of()));
    }

    @Test
    void reviewCapacityLimitDoesNotBlockTheExistingImporter() {
        var diagnosis = new TrackDiagnosis("synthetic", "hash", 0, false, false, List.of(), List.of(
                new TrackFinding(TrackFinding.Stage.READ, TrackFinding.Reason.ASSESSMENT_LIMIT,
                        TrackFinding.Severity.WARNING, -1, null, null, 0, null, null, "Review limit", List.of())));
        var assessment = new SourceReviewService.AssessedSource(null, null, diagnosis, List.of());
        when(sourceReview.assessForImport(FILE_ID)).thenReturn(assessment);
        when(gpsStoreService.readAndSave(any(), eq(null), any(TimingCollector.class)))
                .thenReturn(List.of(loadResult(GpsTrack.LOAD_STATUS.SUCCESS)));
        assertThatCode(() -> worker.processCreateOrChange("GPS", FILE_ID, false)).doesNotThrowAnyException();
        verify(gpsStoreService).readAndSave(any(), eq(null), any(TimingCollector.class));
        verify(sourceReview).requireCurrentRevision(assessment);
        verify(sourceReview).importCompleted(eq(assessment), any());
    }

    @Test
    void aLargeConvertedSourceUsesTheBackgroundAllowanceAfterAssessmentIsLimited() throws Exception {
        var file = indexerRepository.findById(FILE_ID).orElseThrow();
        Path source = sourceDirectory.resolve("synthetic.fit");
        Files.writeString(source, "synthetic FIT");
        file.setFullPath(source.toString());
        file.setSize(Files.size(source));
        file.setLastModifiedDate(new Date(Files.getLastModifiedTime(source).toMillis()));
        file.setName("synthetic.fit");
        var diagnosis = new TrackDiagnosis("synthetic", "hash", 0, false, false, List.of(), List.of(
                new TrackFinding(TrackFinding.Stage.READ, TrackFinding.Reason.ASSESSMENT_LIMIT,
                        TrackFinding.Severity.WARNING, -1, null, null, 0, null, null, "Review limit", List.of())));
        when(sourceReview.assessForImport(FILE_ID)).thenReturn(new SourceReviewService.AssessedSource(null, null, diagnosis, List.of()));
        when(converterService.convertToGpxForImport(sourceDirectory.resolve("synthetic.fit"), SupportedTrackFormat.FIT)).thenReturn("<gpx/>");
        when(gpsStoreService.readAndSave(any(), eq("<gpx/>"), any(TimingCollector.class)))
                .thenReturn(List.of(loadResult(GpsTrack.LOAD_STATUS.SUCCESS)));

        worker.processCreateOrChange("GPS", FILE_ID, false);

        verify(converterService).convertToGpxForImport(sourceDirectory.resolve("synthetic.fit"), SupportedTrackFormat.FIT);
        verify(converterService, never()).convertToGpx(any(), any());
    }

    @Test
    void failedGpsLoadIsReportedToTheFileIndexer() {
        when(gpsStoreService.saveLoadResults(any(), anyList(), any(TimingCollector.class)))
                .thenReturn(List.of(loadResult(GpsTrack.LOAD_STATUS.FAILED)));

        assertThatThrownBy(() -> worker.processCreateOrChange("GPS", FILE_ID, false))
                .isInstanceOf(GpxProcessingWorker.GpxImportFailedException.class)
                .hasMessage("GPS import finished with status FAILED");
    }

    @Test
    void sourceHashConflictRequestsReplayRatherThanRecordingAProcessingFailure() {
        when(gpsStoreService.saveLoadResults(any(), anyList(), any(TimingCollector.class)))
                .thenReturn(List.of(loadResult(GpsTrack.LOAD_STATUS.SUCCESS)));
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "synthetic source hash changed"))
                .when(sourceReview).requireCurrentRevision(any());
        assertThatThrownBy(() -> worker.processCreateOrChange("GPS", FILE_ID, true))
                .isInstanceOf(com.x8ing.mtl.server.mtlserver.indexer.SourceFileChangedException.class);
        verify(sourceReview, never()).importFailed(any());
    }

    @Test
    void missingLoadResultIsReportedToTheFileIndexer() {
        when(gpsStoreService.saveLoadResults(any(), anyList(), any(TimingCollector.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> worker.processCreateOrChange("GPS", FILE_ID, false))
                .isInstanceOf(GpxProcessingWorker.GpxImportFailedException.class)
                .hasMessage("GPS import produced no result");
    }

    @Test
    void successfulAndEmptyGpsLoadsCompleteNormally() {
        when(gpsStoreService.saveLoadResults(any(), anyList(), any(TimingCollector.class)))
                .thenReturn(List.of(
                        loadResult(GpsTrack.LOAD_STATUS.SUCCESS),
                        loadResult(GpsTrack.LOAD_STATUS.EMPTY_FILE)));

        assertThatCode(() -> worker.processCreateOrChange("GPS", FILE_ID, false))
                .doesNotThrowAnyException();
        verify(sourceReview).assessForImport(FILE_ID);
        verify(sourceReview, never()).assess(anyLong());
    }

    @Test
    void changedPathDeletesExistingTracksOnlyAfterSuccessfulReimport() {
        GpsTrack previousTrack = track(23L, GpsTrack.LOAD_STATUS.SUCCESS);
        GPXReader.LoadResult replacement = loadResult(GpsTrack.LOAD_STATUS.SUCCESS);
        replacement.gpsTrack.setId(24L);
        when(gpsTrackRepository.findByIndexedFile(any())).thenReturn(List.of(previousTrack));
        when(gpsStoreService.saveLoadResults(any(), anyList(), any(TimingCollector.class)))
                .thenReturn(List.of(replacement));

        worker.processCreateOrChange("GPS", FILE_ID, true);

        var inOrder = inOrder(gpsStoreService, gpsTrackRepository, eventPublisher);
        inOrder.verify(gpsStoreService).saveLoadResults(any(), anyList(), any(TimingCollector.class));
        inOrder.verify(gpsTrackRepository).saveAll(List.of(replacement.gpsTrack));
        inOrder.verify(eventPublisher).publishEvent(
                new GpxTrackReplacementEvent(java.util.Map.of(23L, 24L)));
        inOrder.verify(gpsStoreService).deleteWithAllDependencies(previousTrack);
    }

    @Test
    void replacementQueuesEnergyAfterPreservingUserClassification() {
        GpsTrack previous = track(23L, GpsTrack.LOAD_STATUS.SUCCESS);
        previous.setActivityType(GpsTrack.ACTIVITY_TYPE.HIKING);
        previous.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        GPXReader.LoadResult replacement = loadResult(GpsTrack.LOAD_STATUS.SUCCESS);
        replacement.gpsTrack.setId(24L);
        when(gpsTrackRepository.findByIndexedFile(any())).thenReturn(List.of(previous));
        when(gpsStoreService.saveLoadResults(any(), anyList(), any(TimingCollector.class)))
                .thenReturn(List.of(replacement));

        worker.processCreateOrChange("GPS", FILE_ID, true);

        assertThat(replacement.gpsTrack.getActivityType()).isEqualTo(GpsTrack.ACTIVITY_TYPE.HIKING);
        verify(energyQueue).request(24L);
    }

    @Test
    void failedReplacementKeepsPreviousTracks() {
        GpsTrack previousTrack = track(23L, GpsTrack.LOAD_STATUS.SUCCESS);
        when(gpsTrackRepository.findByIndexedFile(any())).thenReturn(List.of(previousTrack));
        when(gpsStoreService.saveLoadResults(any(), anyList(), any(TimingCollector.class)))
                .thenReturn(List.of(loadResult(GpsTrack.LOAD_STATUS.FAILED)));

        assertThatThrownBy(() -> worker.processCreateOrChange("GPS", FILE_ID, true))
                .isInstanceOf(GpxProcessingWorker.GpxImportFailedException.class);

        verify(gpsStoreService, never()).deleteWithAllDependencies(any(GpsTrack.class));
    }

    @Test
    void replacementTransfersOnlyUserOwnedActivityClassificationAndExclusions() {
        GpsTrack previous = track(23L, GpsTrack.LOAD_STATUS.SUCCESS);
        previous.setSourceSegmentIndex(2);
        previous.setActivityType(GpsTrack.ACTIVITY_TYPE.HIKING);
        previous.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        previous.setActivityTypeSourceDetails("Changed in UI");
        var estimate = new com.x8ing.mtl.server.mtlserver.energy.MetabolicEnergy();
        estimate.setBodyWeightKg(82.0);
        estimate.setActiveKcal(123.0);
        previous.setMetabolicEnergy(estimate);
        previous.setHighlightExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.GPS_NOISE);
        previous.setStatisticsExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
        GpsTrack replacement = track(24L, GpsTrack.LOAD_STATUS.SUCCESS);
        replacement.setSourceSegmentIndex(2);

        GpxProcessingWorker.transferUserOwnedFields(List.of(previous), List.of(replacement));

        assertThat(replacement.getActivityType()).isEqualTo(GpsTrack.ACTIVITY_TYPE.HIKING);
        assertThat(replacement.getActivityTypeSource()).isEqualTo(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        assertThat(replacement.getActivityTypeSourceDetails()).isEqualTo("Changed in UI");
        assertThat(replacement.getMetabolicEnergy().getBodyWeightKg()).isEqualTo(82);
        assertThat(replacement.getMetabolicEnergy().getActiveKcal()).isNull();
        assertThat(replacement.getMetabolicEnergy()).isNotSameAs(estimate);
        assertThat(replacement.getHighlightExclusionReason()).isEqualTo(GpsTrack.STATISTICS_EXCLUSION_REASON.GPS_NOISE);
        assertThat(replacement.getStatisticsExclusionReason()).isEqualTo(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
    }

    @Test
    void replacementKeepsBodyWeightForAutomaticallyClassifiedTracks() {
        var previous = track(23L, GpsTrack.LOAD_STATUS.SUCCESS);
        previous.setSourceSegmentIndex(2);
        previous.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.AUTO_GUESS);
        var weight = new com.x8ing.mtl.server.mtlserver.energy.MetabolicEnergy();
        weight.setBodyWeightKg(82.0);
        previous.setMetabolicEnergy(weight);
        var replacement = track(24L, GpsTrack.LOAD_STATUS.SUCCESS);
        replacement.setSourceSegmentIndex(2);
        GpxProcessingWorker.transferUserOwnedFields(List.of(previous), List.of(replacement));
        assertThat(replacement.getMetabolicEnergy().getBodyWeightKg()).isEqualTo(82);
        assertThat(replacement.getActivityType()).isNull();
    }

    @Test
    void anEarlierSplitCannotStealTheNextRecordingsSettingsOrHealthAssociation() {
        var a = recording(1, 1, 0, 0, 1000);
        var b = recording(2, 2, 1, 2000, 2200);
        a.setStatisticsExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.GPS_NOISE);
        b.setStatisticsExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
        b.setActivityType(GpsTrack.ACTIVITY_TYPE.HIKING);
        b.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        var a1 = recording(11, 1, 0, 0, 300);
        var a2 = recording(12, 2, 0, 700, 1000);
        var b1 = recording(13, 3, 1, 2000, 2200);

        reimport(List.of(a, b), List.of(a1, a2, b1));

        assertThat(a1.getStatisticsExclusionReason()).isEqualTo(GpsTrack.STATISTICS_EXCLUSION_REASON.GPS_NOISE);
        assertThat(a2.getStatisticsExclusionReason()).isEqualTo(GpsTrack.STATISTICS_EXCLUSION_REASON.GPS_NOISE);
        assertThat(a2.getActivityTypeSource()).isNotEqualTo(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        assertThat(b1.getStatisticsExclusionReason()).isEqualTo(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
        assertThat(b1.getActivityType()).isEqualTo(GpsTrack.ACTIVITY_TYPE.HIKING);
        // Both A fragments inherit its settings, but its single Health link cannot be assigned to either one safely.
        verify(eventPublisher).publishEvent(new GpxTrackReplacementEvent(Map.of(2L, 13L)));
    }

    @Test
    void insertedFragmentsInsideTheSameSectionMatchByTimeInsteadOfOutputOrder() {
        var a = recording(1, 1, 0, 0, 100);
        var b = recording(2, 2, 0, 200, 300);
        b.setStatisticsExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
        var added = recording(10, 1, 0, -200, -100);
        var a1 = recording(11, 2, 0, 0, 100);
        var b1 = recording(12, 3, 0, 200, 300);

        reimport(List.of(b, a), List.of(added, a1, b1));

        assertThat(added.getStatisticsExclusionReason()).isNull();
        assertThat(a1.getStatisticsExclusionReason()).isNull();
        assertThat(b1.getStatisticsExclusionReason()).isEqualTo(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
        verify(eventPublisher).publishEvent(new GpxTrackReplacementEvent(Map.of(1L, 11L, 2L, 12L)));
    }

    @Test
    void unknownSectionsCanMatchUniqueRecordingTimes() {
        var a = recording(1, 1, null, 0, 100);
        var b = recording(2, 2, null, 200, 300);
        var a1 = recording(11, 2, 0, 0, 100);
        var b1 = recording(12, 3, 1, 200, 300);
        reimport(List.of(a, b), List.of(b1, a1));
        verify(eventPublisher).publishEvent(new GpxTrackReplacementEvent(Map.of(1L, 11L, 2L, 12L)));
    }

    @Test
    void ambiguousUntimedTracksDoNotTransferByPosition() {
        var a = track(1L, GpsTrack.LOAD_STATUS.SUCCESS);
        var b = track(2L, GpsTrack.LOAD_STATUS.SUCCESS);
        b.setStatisticsExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
        var a1 = track(11L, GpsTrack.LOAD_STATUS.SUCCESS);
        var b1 = track(12L, GpsTrack.LOAD_STATUS.SUCCESS);
        reimport(List.of(a, b), List.of(a1, b1));
        assertThat(b1.getStatisticsExclusionReason()).isNull();
        verify(eventPublisher).publishEvent(new GpxTrackReplacementEvent(Map.of()));
    }

    @Test
    void mergedFragmentsDoNotInheritOneArbitrarilyChosenPredecessor() {
        var a = recording(1, 1, 0, 0, 100);
        var b = recording(2, 2, 0, 200, 300);
        a.setStatisticsExclusionReason(GpsTrack.STATISTICS_EXCLUSION_REASON.OTHER);
        var merged = recording(11, 1, 0, 0, 300);
        reimport(List.of(a, b), List.of(merged));
        assertThat(merged.getStatisticsExclusionReason()).isNull();
        verify(eventPublisher).publishEvent(new GpxTrackReplacementEvent(Map.of()));
    }

    private void reimport(List<GpsTrack> previous, List<GpsTrack> replacements) {
        when(gpsTrackRepository.findByIndexedFile(any())).thenReturn(previous);
        when(gpsStoreService.saveLoadResults(any(), anyList(), any(TimingCollector.class)))
                .thenReturn(replacements.stream().map(track -> {
                    var result = new GPXReader.LoadResult();
                    result.gpsTrack = track;
                    return result;
                }).toList());
        worker.processCreateOrChange("GPS", FILE_ID, true);
    }

    private static GpsTrack recording(long id, int fragment, Integer section, long first, long last) {
        var track = track(id, GpsTrack.LOAD_STATUS.SUCCESS);
        track.setSourceSegmentIndex(fragment);
        track.setSourceRecordingSection(section);
        track.setStartDate(new Date(first * 1000));
        track.setEndDate(new Date(last * 1000));
        return track;
    }

    private static GPXReader.LoadResult loadResult(GpsTrack.LOAD_STATUS status) {
        GpsTrack track = track(null, status);
        GPXReader.LoadResult result = new GPXReader.LoadResult();
        result.gpsTrack = track;
        return result;
    }

    private static GpsTrack track(Long id, GpsTrack.LOAD_STATUS status) {
        GpsTrack track = new GpsTrack();
        track.setId(id);
        track.setLoadStatus(status);
        return track;
    }
}
