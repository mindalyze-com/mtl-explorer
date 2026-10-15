package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.energy.MetabolicEnergy;
import com.x8ing.mtl.server.mtlserver.indexer.IndexedFileLookup;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileChangedException;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileRevision;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileSkippedException;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileProblemException;
import com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueIngestService;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceImportFailure;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceImportPlan;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceOperationLocks;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewService;
import com.x8ing.mtl.server.mtlserver.utils.TimingCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 * Dedicated worker bean for GPX file processing.
 * <p>
 * Source assessment and conversion run without a transaction. Track replacement
 * commits atomically in a separate transaction after preparation completes.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@JsonPropertyOrder({
        "indexerRepository",
        "gpsTrackRepository",
        "gpsStoreService",
        "converterService",
        "eventPublisher"
})
public class GpxProcessingWorker {

    private final IndexerRepository indexerRepository;
    private final GpsTrackRepository gpsTrackRepository;
    private final GPXStoreService gpsStoreService;
    private final TrackFileConverterService converterService;
    private final ApplicationEventPublisher eventPublisher;
    private final EnergyRecalculationQueue energyQueue;
    private final SourceReviewService sourceReview;
    private final TransactionTemplate transactionTemplate;
    private final SourceIssueIngestService sourceIssues;

    /**
     * Process a new or changed GPX file.
     *
     * @param index   the index name (e.g., "GPS")
     * @param fileId  the indexed file ID
     * @param changed true if an existing file is being replaced, false if it is new
     */
    @Transactional(propagation = Propagation.NEVER)
    public void processCreateOrChange(String index, long fileId, boolean changed) {
        try (var lease = SourceOperationLocks.forOperation(fileId)) {
            processLocked(index, fileId, changed);
        }
    }

    private void processLocked(String index, long fileId, boolean changed) {
        TimingCollector timing = new TimingCollector();
        IndexedFile f = IndexedFileLookup.findOrLog(indexerRepository, fileId, log, "GPX process");
        if (f == null) {
            return;
        }
        SourceFileRevision revision = SourceFileRevision.read(f);
        if (revision.size() == 0) {
            sourceReview.assessForImport(fileId);
            revision.requireUnchanged();
            throw new SourceFileSkippedException();
        }
        SourceReviewService.AssessedSource assessment;
        try {
            assessment = sourceReview.assessForImport(fileId);
        } catch (RuntimeException failure) {
            revision.requireUnchanged();
            throw failure;
        }
        revision.requireUnchanged();
        if (assessment.document() == null && !SourceReviewService.assessmentLimited(assessment.diagnosis()))
            throw new SourceFileProblemException("Source could not be parsed; the diagnosis is available in Review.", null);
        if (assessment.document() != null && assessment.document().points().isEmpty())
            throw new SourceFileProblemException("Source has no usable GPS observations; the diagnosis is available in Review.", null);
        try {
            String convertedGpx = convertUnassessed(f, assessment, timing);
            revision.requireUnchanged();
            transactionTemplate.executeWithoutResult(status -> {
                IndexedFile current = IndexedFileLookup.findOrLog(indexerRepository, fileId, log, "GPX import");
                if (current == null || !Objects.equals(current.getFullPath(), f.getFullPath()))
                    throw new SourceFileChangedException("Source location changed during GPS processing: " + f.getName());
                SourceFileRevision.read(current);
                revision.requireUnchanged();
                importAssessed(current, assessment, convertedGpx, changed, timing);
                revision.requireUnchanged();
            });
        } catch (RuntimeException failure) {
            if (failure instanceof SourceFileChangedException) throw failure;
            revision.requireUnchanged();
            log.error("GPS source processing failed: sourceId={} type={} message={}", fileId,
                    failure.getClass().getSimpleName(), failure.getMessage(), failure);
            // The save transaction has rolled back before recording this independent diagnosis.
            sourceReview.importFailed(new SourceImportFailure(fileId, assessment.diagnosis().sourceHash(),
                    failure.getClass().getSimpleName(), failure instanceof GpxImportFailedException ? failure.getMessage()
                    : "The previous committed tracks were retained. See the server log for the processing error."));
            throw failure;
        }
    }

    private String convertUnassessed(IndexedFile f, SourceReviewService.AssessedSource assessment, TimingCollector timing) {
        if (assessment.document() != null) return null;
        SupportedTrackFormat format = SupportedTrackFormat.fromPath(Paths.get(f.getFullPath()));
        if (format == null || !format.needsConversion()) return null;
        try {
            return timing.time("track conversion", () -> converterService.convertToGpxForImport(Paths.get(f.getFullPath()), format));
        } catch (Exception e) {
            log.error("GPS conversion failed: sourceId={} type={} message={}", f.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Track conversion failed for " + f.getFullPath(), e);
        }
    }

    private void importAssessed(IndexedFile f, SourceReviewService.AssessedSource assessment, String convertedGpx,
                                boolean changed, TimingCollector timing) {
        List<GpsTrack> previousTracks = changed ? gpsTrackRepository.findByIndexedFile(f) : List.of();
        List<GPXReader.LoadResult> loadResults;
        if (assessment.document() != null) {
            loadResults = gpsStoreService.saveLoadResults(f, SourceImportPlan.load(f, assessment), timing);
        } else {
            loadResults = gpsStoreService.readAndSave(f, convertedGpx, timing);
        }
        verifyLoadResults(loadResults);
        try {
            sourceReview.requireCurrentRevision(assessment);
        } catch (ResponseStatusException failure) {
            if (failure.getStatusCode().equals(HttpStatus.CONFLICT)) {
                log.warn("GPS source hash changed before commit: sourceId={} type={} message={}",
                        f.getId(), failure.getClass().getSimpleName(), failure.getReason());
                throw new SourceFileChangedException("Source content changed during GPS processing: " + f.getName());
            }
            throw failure;
        }
        for (var loaded : loadResults) sourceReview.applyDecision(assessment, loaded.gpsTrack);
        sourceReview.importCompleted(assessment, loadResults);

        if (changed) {
            replacePreviousTracks(previousTracks, loadResults, timing);
        } else gpsTrackRepository.saveAll(loadResults.stream().map(result -> result.gpsTrack).toList());
    }

    /**
     * Process a deleted GPX file.
     *
     * @param index  the index name (e.g., "GPS")
     * @param fileId the indexed file ID
     */
    @Transactional
    public void processDelete(String index, long fileId) {
        try (var lease = SourceOperationLocks.forTransaction(fileId)) {
            IndexedFile f = IndexedFileLookup.findOrLog(indexerRepository, fileId, log, "GPX delete");
            if (f == null) return;
            SourceFileRevision.requireAbsent(f);
            gpsStoreService.deleteTracksForFile(f);
            SourceFileRevision.requireAbsent(f);
            sourceIssues.removed(fileId);
        }
    }

    private static void verifyLoadResults(List<GPXReader.LoadResult> loadResults) {
        if (loadResults == null || loadResults.isEmpty()) {
            throw new GpxImportFailedException("GPS import produced no result");
        }
        boolean failed = loadResults.stream()
                .anyMatch(result -> result == null
                                    || result.gpsTrack == null
                                    || GpsTrack.LOAD_STATUS.FAILED.equals(result.gpsTrack.getLoadStatus()));
        if (failed) {
            throw new GpxImportFailedException("GPS import finished with status FAILED");
        }
    }

    private void replacePreviousTracks(
            List<GpsTrack> previousTracks,
            List<GPXReader.LoadResult> loadResults,
            TimingCollector timing) {
        List<GpsTrack> replacementTracks = loadResults.stream()
                .filter(Objects::nonNull)
                .map(result -> result.gpsTrack)
                .filter(Objects::nonNull)
                .toList();

        List<TrackReplacement> matches = matchReplacementTracks(previousTracks, replacementTracks);
        transferUserOwnedFields(matches);
        gpsTrackRepository.saveAll(replacementTracks);
        replacementTracks.stream()
                .filter(track -> track.getId() != null && track.getActivityType() != null
                                 && GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET.equals(track.getActivityTypeSource()))
                .forEach(track -> energyQueue.request(track.getId()));
        var associationTransfers = matchReplacementTrackIds(matches);
        eventPublisher.publishEvent(new GpxTrackReplacementEvent(associationTransfers));
        log.info("GPS replacement matching completed: previousTracks={} replacementTracks={} matchedFragments={} associationTransfers={}",
                previousTracks.size(), replacementTracks.size(), matches.size(), associationTransfers.size());

        Set<Long> replacementIds = replacementTracks.stream()
                .map(GpsTrack::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        timing.timeUnchecked("delete old tracks", () -> previousTracks.stream()
                .filter(previous -> previous.getId() == null || !replacementIds.contains(previous.getId()))
                .forEach(gpsStoreService::deleteWithAllDependencies));
    }

    static void transferUserOwnedFields(List<GpsTrack> previousTracks, List<GpsTrack> replacementTracks) {
        transferUserOwnedFields(matchReplacementTracks(previousTracks, replacementTracks));
    }

    private static void transferUserOwnedFields(List<TrackReplacement> matches) {
        for (TrackReplacement match : matches) {
            GpsTrack previous = match.previous();
            GpsTrack replacement = match.replacement();
            if (GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET.equals(previous.getActivityTypeSource())) {
                replacement.setActivityType(previous.getActivityType());
                replacement.setActivityTypeSource(previous.getActivityTypeSource());
                replacement.setActivityTypeSourceDetails(previous.getActivityTypeSourceDetails());
            }
            if (previous.getMetabolicEnergy() != null
                && EnergyParameters.isValidRiderWeight(previous.getMetabolicEnergy().getBodyWeightKg())) {
                var pending = new MetabolicEnergy();
                pending.setBodyWeightKg(previous.getMetabolicEnergy().getBodyWeightKg());
                replacement.setMetabolicEnergy(pending);
            }
            replacement.setHighlightExclusionReason(previous.getHighlightExclusionReason());
            replacement.setStatisticsExclusionReason(previous.getStatisticsExclusionReason());
        }
    }

    private static java.util.Map<Long, Long> matchReplacementTrackIds(List<TrackReplacement> matches) {
        java.util.Map<Long, Long> replacements = new LinkedHashMap<>();
        for (TrackReplacement match : matches) {
            GpsTrack previous = match.previous();
            GpsTrack replacement = match.replacement();
            // A Health exercise has one track link. A split has no unambiguous replacement for that link.
            if (previous.getId() != null && replacement.getId() != null
                && matches.stream().filter(candidate -> candidate.previous() == previous).count() == 1) {
                replacements.put(previous.getId(), replacement.getId());
            }
        }
        return replacements;
    }

    private record TrackReplacement(GpsTrack previous, GpsTrack replacement) {
    }

    private static List<TrackReplacement> matchReplacementTracks(List<GpsTrack> previousTracks, List<GpsTrack> replacementTracks) {
        List<TrackReplacement> matches = new java.util.ArrayList<>();
        for (GpsTrack replacement : replacementTracks) {
            var candidates = previousTracks.stream().filter(previous -> compatibleSection(previous, replacement)).toList();
            var timed = candidates.stream().filter(previous -> containsRecordingTime(previous, replacement)).toList();
            GpsTrack previous = null;
            if (timed.size() == 1) previous = timed.getFirst();
            else if (timed.isEmpty() && candidates.size() == 1) {
                GpsTrack candidate = candidates.getFirst();
                // A sole source recording can retain settings when its timestamps are corrected.
                // Never infer identity from output fragment numbers or list order.
                boolean sameKnownSection = replacement.getSourceRecordingSection() != null
                                           && Objects.equals(candidate.getSourceRecordingSection(), replacement.getSourceRecordingSection());
                if ((sameKnownSection || previousTracks.size() == 1 && replacementTracks.size() == 1)
                    && replacementTracks.stream().filter(track -> compatibleSection(candidate, track)).count() == 1)
                    previous = candidate;
            }
            if (previous != null) matches.add(new TrackReplacement(previous, replacement));
        }
        return matches;
    }

    private static boolean compatibleSection(GpsTrack previous, GpsTrack replacement) {
        return previous.getSourceRecordingSection() == null || replacement.getSourceRecordingSection() == null
               || Objects.equals(previous.getSourceRecordingSection(), replacement.getSourceRecordingSection());
    }

    private static boolean containsRecordingTime(GpsTrack previous, GpsTrack replacement) {
        return !previous.isTimingUnreliable() && !replacement.isTimingUnreliable()
               && !previous.isTimingEstimated() && !replacement.isTimingEstimated()
               && previous.getStartDate() != null && previous.getEndDate() != null
               && replacement.getStartDate() != null && replacement.getEndDate() != null
               && previous.getStartDate().before(previous.getEndDate())
               && replacement.getStartDate().before(replacement.getEndDate())
               && !replacement.getStartDate().before(previous.getStartDate())
               && !replacement.getEndDate().after(previous.getEndDate());
    }

    static final class GpxImportFailedException extends RuntimeException {
        GpxImportFailedException(String message) {
            super(message);
        }
    }
}
