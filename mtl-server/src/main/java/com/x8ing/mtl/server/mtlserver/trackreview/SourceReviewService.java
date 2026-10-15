package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.*;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.gpx.GpsProcessingAssessment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewRepository.*;
import static com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceReviewService {
    private static final int MAX_PREVIEW_POINTS = 4000;
    public static final int MAX_QUEUE_PAGE = 100;
    public static final int MAX_POINT_PAGE = 500;
    private static final List<String> TIME_CAPABILITIES = List.of("Duration", "Speed", "Power", "Replay", "Sensor alignment");
    private final TrackSourceAccess access;
    private final SourceReviewRepository repository;
    private final TransactionTemplate transactionTemplate;

    public record AssessedSource(TrackSourceAccess.Source source, GpxDocument document,
                                 TrackDiagnosis diagnosis, List<SourceSectionReview> sections) {
    }

    public void requireCurrentRevision(AssessedSource assessed) {
        access.requireUnchanged(access.readForAssessment(assessed.source().file().getId()), assessed.source().hash());
    }

    public void applyDecision(AssessedSource assessed, GpsTrack track) {
        track.setSourceContentHash(assessed.source().hash());
        if (track.getSourceRecordingSection() == null && assessed.sections().size() == 1 && assessed.sections().getFirst().sectionIndex() >= 0)
            track.setSourceRecordingSection(assessed.sections().getFirst().sectionIndex());
        var settings = SourceReviewPolicy.forTrack(assessed.diagnosis(), assessed.sections(), track.getSourceRecordingSection(),
                false, track.isTimingEstimated(), track.isTimingUnreliable());
        track.setReviewStatus(settings.state().name());
        track.setReviewExcluded(settings.statisticsExcluded());
        track.setReviewMapExcluded(settings.mapExcluded());
        track.setTimingEstimated(settings.estimated());
        track.setTimingUnreliable(settings.unreliable());
    }

    /**
     * Persist failures independently of a later importer transaction; sources without track rows remain reviewable.
     */
    @Transactional(propagation = Propagation.NEVER)
    public AssessedSource assess(long sourceId) {
        try (var lease = SourceOperationLocks.forOperation(sourceId)) {
            return assessLocked(sourceId, false);
        }
    }

    @Transactional(propagation = Propagation.NEVER)
    public AssessedSource assessForImport(long sourceId) {
        try (var lease = SourceOperationLocks.forOperation(sourceId)) {
            return assessLocked(sourceId, true);
        }
    }

    private AssessedSource assessLocked(long sourceId, boolean backgroundImport) {
        long started = System.currentTimeMillis();
        log.info("Source diagnosis started: sourceId={}", sourceId);
        TrackSourceAccess.Source source = null;
        TrackFinding.Stage stage = TrackFinding.Stage.READ;
        GpxDocument document = null;
        TrackDiagnosis diagnosis;
        try {
            source = access.readForAssessment(sourceId);
            if (source.bytes() != null && source.bytes().length == 0) {
                diagnosis = new TrackDiagnosis(TrackDiagnosticEngine.VERSION, source.hash(), 0, true, false, List.of(),
                        List.of(new TrackFinding(TrackFinding.Stage.READ, TrackFinding.Reason.EMPTY_FILE, TrackFinding.Severity.REVIEW,
                        -1, null, null, 0, null, null, "The GPS source is empty. Any earlier imported data is retained. It will be processed when its contents change.", List.of())));
            } else {
                if (source.bytes() == null) throw new GpxDocument.ReviewLimitExceeded("Source exceeds the 64 MiB review limit.");
                stage = TrackFinding.Stage.CONVERSION;
                byte[] bytes = backgroundImport ? access.gpxForImport(source) : access.gpx(source);
                stage = TrackFinding.Stage.PARSING;
                document = GpxDocument.parse(bytes);
                TrackDiagnosis converted = new TrackDiagnosticEngine().diagnose(document);
                // Revision identity always belongs to the original source, including converted formats.
                diagnosis = new TrackDiagnosis(converted.algorithmVersion(), source.hash(), converted.pointCount(),
                        converted.needsReview(), converted.estimatedTiming(), converted.sections(), converted.findings());
            }
        } catch (ResponseStatusException | GpxDocument.ParseFailure | GpxDocument.ReviewLimitExceeded e) {
            if (e instanceof ResponseStatusException status && (status.getStatusCode().value() == 404 || status.getStatusCode().value() == 403)) throw e;
            String message = e instanceof ResponseStatusException status ? status.getReason() : e.getMessage();
            log.warn("Source diagnosis failed: sourceId={} stage={} type={} message={}", sourceId, stage, e.getClass().getSimpleName(), message);
            log.debug("Source diagnosis failure", e);
            boolean limited = e instanceof GpxDocument.ReviewLimitExceeded
                              || e instanceof ResponseStatusException status && status.getStatusCode().value() == 413;
            if (limited && source != null) {
                // Normal import rereads the file; do not retain its byte buffer throughout that work.
                source = new TrackSourceAccess.Source(source.file(), source.path(), null, source.hash(), source.format());
            }
            TrackFinding.Reason reason = limited ? TrackFinding.Reason.ASSESSMENT_LIMIT : switch (stage) {
                case READ -> TrackFinding.Reason.SOURCE_UNREADABLE;
                case CONVERSION -> TrackFinding.Reason.CONVERSION_FAILED;
                default -> TrackFinding.Reason.PARSE_FAILED;
            };
            if (limited)
                message += " Quality assessment is incomplete. Normal import can continue; this limit alone does not exclude tracks or establish that they are healthy. Download the source for external review.";
            diagnosis = new TrackDiagnosis(TrackDiagnosticEngine.VERSION, source == null ? "unreadable" : source.hash(), 0, !limited, false,
                    List.of(), List.of(new TrackFinding(stage, reason, limited ? TrackFinding.Severity.WARNING : TrackFinding.Severity.REVIEW,
                    -1, null, null, 0, null, null,
                    message == null ? "The source could not be assessed." : message, limited ? List.of() : List.of("Map", "Statistics"))));
        }
        TrackSourceAccess.Source assessedSource = source;
        GpxDocument assessedDocument = document;
        TrackDiagnosis sourceDiagnosis = diagnosis;
        return transactionTemplate.execute(status -> {
            if (assessedSource != null) access.requireUnchanged(access.readForAssessment(sourceId), assessedSource.hash());
            return persistAssessment(sourceId, assessedSource, assessedDocument, sourceDiagnosis, started);
        });
    }

    private AssessedSource persistAssessment(long sourceId, TrackSourceAccess.Source source, GpxDocument document,
                                             TrackDiagnosis diagnosis, long started) {
        String hash = source == null ? "unreadable" : source.hash();
        if (assessmentLimited(diagnosis)) {
            var previous = repository.find(sourceId).filter(a -> a.sourceHash().equals(hash));
            if (previous.isPresent() && !previous.get().diagnosis().sections().isEmpty()) {
                TrackDiagnosis known = previous.get().diagnosis();
                var findings = new ArrayList<>(diagnosis.findings());
                known.findings().stream().filter(f -> importedTimingFinding(f)
                                                      || f.reason() == TrackFinding.Reason.PROCESSING_FAILED).forEach(findings::add);
                diagnosis = TrackProcessingDiagnosticEngine.withFindings(new TrackDiagnosis(diagnosis.algorithmVersion(),
                        hash, known.pointCount(), known.needsReview(), known.estimatedTiming(), known.sections(), findings), findings);
            }
        }
        if (document != null) {
            diagnosis = new TrackProcessingDiagnosticEngine().diagnose(diagnosis, repository.processedRecordings(sourceId));
            var previous = repository.find(sourceId).filter(a -> a.sourceHash().equals(hash));
            if (previous.isPresent()) {
                List<TrackFinding> findings = new ArrayList<>(diagnosis.findings());
                previous.get().diagnosis().findings().stream().filter(f -> f.reason() == TrackFinding.Reason.PROCESSING_FAILED).forEach(findings::add);
                diagnosis = TrackProcessingDiagnosticEngine.withFindings(diagnosis, findings);
            }
        }
        if (document != null || assessmentLimited(diagnosis)) {
            List<TrackFinding> findings = new ArrayList<>(diagnosis.findings());
            var issues = repository.processingIssues(sourceId, hash);
            addProcessingFindings(findings, document == null ? GpsProcessingAssessment.reviewFindings(issues)
                    : GpsProcessingAssessment.reviewFindings(issues, document.points()));
            if (!repository.hasProcessingEvidence(sourceId, hash)) findings.add(new TrackFinding(TrackFinding.Stage.PROCESSING,
                    TrackFinding.Reason.PROCESSING_EVIDENCE_UNAVAILABLE, TrackFinding.Severity.INFORMATION, -1,
                    null, null, 0, null, null,
                    "Cleaning evidence is unavailable for this source revision. Import the current source to assess its canonical path.", List.of()));
            diagnosis = TrackProcessingDiagnosticEngine.withFindings(diagnosis, findings);
        }
        if (repository.hasStaleDerivedRevision(sourceId, hash)) diagnosis = withStaleImportFinding(diagnosis);
        Assessment assessment = new Assessment(sourceId, hash, diagnosis, Instant.now());
        List<SourceSectionReview> sections = sections(assessment);
        repository.save(sourceId, hash, diagnosis, combinedState(sections));
        repository.applyToTracks(sourceId, sections);
        log.info("Source diagnosis completed: sourceId={} points={} findings={} state={} durationMs={}", sourceId, diagnosis.pointCount(), diagnosis.findings().size(), combinedState(sections), System.currentTimeMillis() - started);
        return new AssessedSource(source, document, diagnosis, sections);
    }

    /**
     * Updates diagnosis and use settings before the watcher commits imported tracks.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void importCompleted(AssessedSource assessed, List<GPXReader.LoadResult> loaded) {
        List<GpsTrack> tracks = loaded.stream().map(result -> result.gpsTrack).toList();
        var evidence = tracks.stream().filter(t -> t.getLoadStatus() == GpsTrack.LOAD_STATUS.SUCCESS)
                .map(t -> new ProcessedRecording(t.getSourceContentHash(), t.getSourceRecordingSection(),
                        t.getStartDate() == null ? null : t.getStartDate().toInstant(),
                        t.getEndDate() == null ? null : t.getEndDate().toInstant(),
                        t.getTrackDurationInMotionSecs(), t.getTrackLengthInMeter())).toList();
        TrackDiagnosis diagnosis = assessmentLimited(assessed.diagnosis())
                ? importedProvenance(assessed.diagnosis(), loaded)
                : new TrackProcessingDiagnosticEngine().diagnose(withoutImportFindings(assessed.diagnosis()), evidence);
        List<TrackFinding> findings = new ArrayList<>(diagnosis.findings());
        var issues = loaded.stream().flatMap(r -> r.processingIssues.stream()).toList();
        addProcessingFindings(findings, assessed.document() == null ? GpsProcessingAssessment.reviewFindings(issues)
                : GpsProcessingAssessment.reviewFindings(issues, assessed.document().points()));
        diagnosis = TrackProcessingDiagnosticEngine.withFindings(diagnosis, findings);
        long sourceId = assessed.source().file().getId();
        var assessment = new Assessment(sourceId, assessed.source().hash(), diagnosis, Instant.now());
        var sections = sections(assessment);
        var completed = new AssessedSource(assessed.source(), assessed.document(), diagnosis, sections);
        tracks.forEach(t -> applyDecision(completed, t));
        repository.save(sourceId, assessed.source().hash(), diagnosis, combinedState(sections));
        log.info("Source processing diagnosis completed: sourceId={} derivedTracks={} findings={} state={}",
                sourceId, tracks.size(), diagnosis.findings().size(), combinedState(sections));
    }

    private static TrackDiagnosis withoutImportFindings(TrackDiagnosis diagnosis) {
        return TrackProcessingDiagnosticEngine.withFindings(diagnosis, diagnosis.findings().stream().filter(f -> switch (f.reason()) {
            case PROCESSING_FAILED, POSITION_JUMP, ELEVATION_SPIKE, POSITION_CORRECTION, ELEVATION_CORRECTION, WEAK_SOURCE_FIX,
                 PROCESSING_EVIDENCE_UNAVAILABLE, TIMING_UNRELIABLE -> false;
            default -> true;
        }).toList());
    }

    private static void addProcessingFindings(List<TrackFinding> findings, List<TrackFinding> processing) {
        for (var finding : processing) {
            if (finding.reason() == TrackFinding.Reason.TIMING_UNRELIABLE && findings.stream().anyMatch(existing ->
                    (existing.reason() == finding.reason() || existing.reason() == TrackFinding.Reason.TIME_ESTIMATED)
                    && existing.sectionIndex() == finding.sectionIndex())) continue;
            findings.add(finding);
        }
    }

    private static TrackDiagnosis importedProvenance(TrackDiagnosis incomplete, List<GPXReader.LoadResult> loaded) {
        Map<Integer, GPXReader.SourceRecordingEvidence> recordings = new LinkedHashMap<>();
        for (var result : loaded)
            if (result.sourceRecordingEvidence != null)
                recordings.put(result.sourceRecordingEvidence.sectionIndex(), result.sourceRecordingEvidence);
        if (recordings.isEmpty()) return new TrackProcessingDiagnosticEngine().diagnose(incomplete, List.of());
        List<TrackFinding> findings = new ArrayList<>(incomplete.findings().stream()
                .filter(f -> f.reason() == TrackFinding.Reason.ASSESSMENT_LIMIT).toList());
        List<TrackDiagnosis.SectionDiagnosis> sections = new ArrayList<>();
        int points = 0;
        for (var recording : recordings.values()) {
            boolean estimated = recording.estimatedPointCount() > 0;
            var clock = recording.timing();
            boolean needsReview = estimated || clock.backwardRequiresReview() || clock.excursionCount() > 0;
            if (clock.backwardCount() > 0) findings.add(new TrackFinding(TrackFinding.Stage.TIMING, TrackFinding.Reason.TIME_BACKWARD,
                    clock.backwardRequiresReview() ? TrackFinding.Severity.REVIEW : TrackFinding.Severity.INFORMATION,
                    recording.sectionIndex(), null, null, clock.backwardCount(), clock.maxBackwardSeconds(), "seconds",
                    clock.backwardMessage() + " Detailed source assessment is incomplete; the original observations are retained.",
                    TIME_CAPABILITIES));
            if (clock.excursionCount() > 0) findings.add(new TrackFinding(TrackFinding.Stage.TIMING, TrackFinding.Reason.TIME_EXCURSION,
                    TrackFinding.Severity.REVIEW, recording.sectionIndex(), recording.firstExcursionPointIndex(),
                    null, clock.excursionCount(), clock.maxExcursionSeconds(), "seconds",
                    "Import recognized date jumps that conflict with the surrounding timeline. Original timestamps are retained; unsupported derived times are unavailable.",
                    TIME_CAPABILITIES));
            if (clock.needsContainment()) findings.add(new TrackFinding(TrackFinding.Stage.TIMING, TrackFinding.Reason.TIMING_UNRELIABLE,
                    TrackFinding.Severity.REVIEW, recording.sectionIndex(), null, null, recording.pointCount(), null, null,
                    "Import disabled section timing for a systemic clock problem. Original timestamps are retained in RAW.", TIME_CAPABILITIES));
            if (estimated) findings.add(new TrackFinding(TrackFinding.Stage.TIMING, TrackFinding.Reason.TIME_ESTIMATED,
                    TrackFinding.Severity.REVIEW, recording.sectionIndex(), null, null, recording.estimatedPointCount(), null, null,
                    "Import recognized generated or altered timestamps. Detailed source assessment is incomplete; these timestamps do not establish historical speed or pauses.",
                    TIME_CAPABILITIES));
            sections.add(new TrackDiagnosis.SectionDiagnosis(new TrackSection(recording.sectionIndex(), recording.name(), "TRACK",
                    recording.firstPointIndex(), recording.pointCount()), recording.timedPointCount(), clock.backwardCount(),
                    recording.pointCount() - recording.timedPointCount(), clock.maxBackwardSeconds(), recording.firstTime(), recording.lastTime(),
                    0, needsReview, estimated));
            points += recording.pointCount();
        }
        boolean estimated = sections.stream().anyMatch(TrackDiagnosis.SectionDiagnosis::estimatedTiming);
        return new TrackDiagnosis(incomplete.algorithmVersion(), incomplete.sourceHash(), points,
                sections.stream().anyMatch(TrackDiagnosis.SectionDiagnosis::needsReview), estimated,
                List.copyOf(sections), List.copyOf(findings));
    }

    private static boolean importedTimingFinding(TrackFinding finding) {
        return switch (finding.reason()) {
            case TIME_ESTIMATED, TIME_BACKWARD, TIME_EXCURSION, TIMING_UNRELIABLE -> true;
            default -> false;
        };
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importFailed(SourceImportFailure failure) {
        try (var lease = SourceOperationLocks.forTransaction(failure.sourceId())) {
            var current = repository.find(failure.sourceId()).filter(a -> a.sourceHash().equals(failure.sourceHash()));
            if (current.isEmpty()) {
                log.info("Import failure diagnosis skipped for a superseded source revision: sourceId={}", failure.sourceId());
                return;
            }
            var assessment = current.get();
            List<TrackFinding> findings = new ArrayList<>(assessment.diagnosis().findings().stream()
                    .filter(f -> f.reason() != TrackFinding.Reason.PROCESSING_FAILED).toList());
            findings.add(new TrackFinding(TrackFinding.Stage.PROCESSING, TrackFinding.Reason.PROCESSING_FAILED,
                    TrackFinding.Severity.REVIEW, -1, null, null, 1, null, null,
                    "The source assessment completed, but importing its processed tracks failed (" + failure.failureType()
                    + "). " + failure.detail() + " Reassessment alone does not retry the import.", List.of("Statistics", "Track availability")));
            var diagnosis = TrackProcessingDiagnosticEngine.withFindings(assessment.diagnosis(), findings);
            var sections = sections(new Assessment(failure.sourceId(), failure.sourceHash(), diagnosis, Instant.now()));
            repository.save(failure.sourceId(), failure.sourceHash(), diagnosis, combinedState(sections));
            repository.applyToTracks(failure.sourceId(), sections);
            log.info("Import failure diagnosis recorded after rollback: sourceId={} type={}", failure.sourceId(), failure.failureType());
        } catch (RuntimeException error) {
            log.error("Import failure diagnosis could not be recorded: sourceId={} type={} message={}",
                    failure.sourceId(), error.getClass().getSimpleName(), error.getMessage(), error);
            throw error;
        }
    }

    private static TrackDiagnosis withStaleImportFinding(TrackDiagnosis diagnosis) {
        List<TrackFinding> findings = new ArrayList<>(diagnosis.findings());
        findings.add(new TrackFinding(TrackFinding.Stage.PROCESSING, TrackFinding.Reason.PROCESSED_SOURCE_CHANGED,
                TrackFinding.Severity.INFORMATION, -1, null, null, 0, null, null,
                "Derived tracks belong to a previous source version. Their timing remains unsupported until the current file is successfully reimported; reassessment or inclusion alone does not rebuild RAW data.",
                List.of("Statistics", "Speed", "Power", "Replay", "Sensor alignment")));
        return new TrackDiagnosis(diagnosis.algorithmVersion(), diagnosis.sourceHash(), diagnosis.pointCount(), diagnosis.needsReview(), diagnosis.estimatedTiming(), diagnosis.sections(), List.copyOf(findings));
    }

    @Transactional(readOnly = true)
    public TrackReviewQueue queue(ReviewState state, String search, TrackFinding.Reason reason,
                                  TrackFinding.Severity severity, Decision decision, int offset, int limit) {
        return repository.queue(state, search == null ? "" : search, reason, severity, decision,
                Math.max(0, offset), Math.min(MAX_QUEUE_PAGE, Math.max(1, limit)));
    }

    @Transactional(propagation = Propagation.NEVER)
    public SourceReviewDetail detail(long sourceId) {
        var stored = transactionTemplate.execute(status -> {
            IndexedFile file = access.indexedFile(sourceId);
            Assessment assessment = repository.find(sourceId).orElse(null);
            return new ReviewDetailSnapshot(file, assessment, repository.tracks(sourceId),
                    assessment == null ? List.of() : sections(assessment), repository.history(sourceId),
                    assessment != null && repository.hasStaleDerivedRevision(sourceId, assessment.sourceHash()));
        });
        IndexedFile file = stored.file();
        Assessment assessment = stored.assessment();
        List<ReviewedTrack> tracks = stored.tracks();
        List<TrackObservation> points = List.of();
        String unavailable = "Assess the current source to see its observations.";
        boolean canDownloadGpx = com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat.fromPath(java.nio.file.Path.of(file.getFullPath()))
                                 == com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat.GPX;
        if (assessment != null) {
            TrackFinding.Stage stage = TrackFinding.Stage.READ;
            try {
                var source = access.read(sourceId);
                if (source.hash().equals(assessment.sourceHash())) {
                    stage = TrackFinding.Stage.CONVERSION;
                    byte[] gpx = access.gpx(source);
                    canDownloadGpx = true;
                    stage = TrackFinding.Stage.PARSING;
                    GpxDocument doc = GpxDocument.parse(gpx);
                    points = previewPoints(doc);
                    unavailable = null;
                } else unavailable = "The source changed since assessment. Assess it again before making a decision or preview.";
            } catch (ResponseStatusException | GpxDocument.ParseFailure | GpxDocument.ReviewLimitExceeded e) {
                log.warn("Review detail source unavailable: sourceId={} type={} message={}", sourceId, e.getClass().getSimpleName(), e.getMessage());
                log.debug("Review detail source failure", e);
                unavailable = assessmentLimited(assessment.diagnosis())
                        ? "This source exceeds the review limits. Download it for external review and correction."
                        : switch (stage) {
                    case READ -> "The source file could not be read. Check its server location and access permissions.";
                    case CONVERSION ->
                            "The server could not convert this source to GPX. Download the original for external review or check the server's converter setup.";
                    default -> "The source could not be parsed. Download it and check its contents in another tool.";
                };
            }
        }
        List<SourceSectionReview> sections = stored.sections();
        TrackDiagnosis diagnosis = assessment == null ? null : assessment.diagnosis();
        SourceReviewItem item = reviewItem(sourceId, file.getName(), assessment == null ? null : assessment.sourceHash(),
                assessment == null ? ReviewState.NOT_ASSESSED : repository.hasPendingIssues(sourceId) ? ReviewState.NEEDS_REVIEW : combinedState(sections),
                tracks.size(), diagnosis, assessment == null ? null : assessment.assessedAt());
        return new SourceReviewDetail(item, diagnosis, sections, file.getFullPath(), unavailable, canDownloadGpx,
                points, diagnosis == null ? 0 : diagnosis.pointCount(), tracks, stored.history());
    }

    private record ReviewDetailSnapshot(IndexedFile file, Assessment assessment, List<ReviewedTrack> tracks,
                                        List<SourceSectionReview> sections, List<ReviewDecisionHistory> history,
                                        boolean staleRevision) {
    }

    @Transactional
    public void decide(long sourceId, ReviewDecisionRequest request) {
        try (var lease = SourceOperationLocks.forTransaction(sourceId)) {
            decideLocked(sourceId, request);
        }
    }

    private void decideLocked(long sourceId, ReviewDecisionRequest request) {
        if (request.decision() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an inclusion decision.");
        if (request.decision() == Decision.LATER) return;
        repository.lockSource(sourceId);
        Assessment assessment = repository.find(sourceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Assess the source before deciding."));
        if (!Objects.equals(assessment.sourceHash(), request.sourceHash()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The assessed source version changed. Refresh the review.");
        if (!"unreadable".equals(assessment.sourceHash())) access.requireUnchanged(access.readForAssessment(sourceId), request.sourceHash());
        if (request.decision() == Decision.INCLUDE && assessment.diagnosis().pointCount() == 0
            && !(assessmentLimited(assessment.diagnosis()) && !repository.tracks(sourceId).isEmpty()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A source without readable observations cannot be included. Supply a corrected file first.");
        List<SourceSectionReview> selected = sections(assessment).stream().filter(s -> request.sectionIndex() == null || s.sectionIndex() == request.sectionIndex()).toList();
        if (selected.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recording section not found.");
        if (request.sectionIndex() != null && repository.tracks(sourceId).stream().anyMatch(t -> t.sectionIndex() == null))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The imported tracks do not have separate source-section mappings. Choose use settings for the whole source.");
        for (SourceSectionReview section : selected) repository.decide(assessment, section.sectionIndex(), request.decision());
        var current = sections(assessment);
        repository.setState(sourceId, combinedState(current));
        repository.applyToTracks(sourceId, current);
        log.info("Source review decision saved: sourceId={} decision={} sections={} resultingState={}", sourceId, request.decision(), selected.size(), combinedState(current));
    }

    List<SourceSectionReview> sections(Assessment assessment) {
        Map<Integer, Decision> useDecisions = repository.useDecisions(assessment);
        if (assessment.diagnosis().sections().isEmpty()) return List.of(new SourceSectionReview(-1, "Source file",
                sectionState(assessmentIncomplete(assessment.diagnosis()), assessment.diagnosis().needsReview(), useDecisions.get(-1)),
                useDecisions.get(-1), assessment.diagnosis().needsReview(), false, 0, 0));
        return assessment.diagnosis().sections().stream().map(s -> new SourceSectionReview(s.section().sectionIndex(), s.section().name(),
                sectionState(assessmentIncomplete(assessment.diagnosis()), s.needsReview(), useDecisions.get(s.section().sectionIndex())),
                useDecisions.get(s.section().sectionIndex()), s.needsReview(), s.estimatedTiming(), s.section().firstPointIndex(), s.section().pointCount())).toList();
    }

    private static ReviewState sectionState(boolean incomplete, boolean needsReview, Decision useDecision) {
        if (incomplete && useDecision == null) return ReviewState.NOT_ASSESSED;
        return state(false, useDecision);
    }

    private static ReviewState state(boolean needsReview, Decision decision) {
        if (decision == Decision.EXCLUDE) return ReviewState.EXCLUDED;
        if (decision == Decision.EXCLUDE_MAP) return ReviewState.EXCLUDED_MAP;
        if (decision == Decision.EXCLUDE_STATISTICS) return ReviewState.EXCLUDED_STATISTICS;
        if (decision == Decision.INCLUDE) return ReviewState.INCLUDED;
        return needsReview ? ReviewState.NEEDS_REVIEW : ReviewState.INCLUDED;
    }

    private static boolean assessmentIncomplete(TrackDiagnosis diagnosis) {
        return assessmentLimited(diagnosis) || diagnosis.findings().stream()
                .anyMatch(f -> switch (f.reason()) {
                    case SOURCE_UNREADABLE, CONVERSION_FAILED, PARSE_FAILED, PROCESSING_FAILED,
                         PROCESSING_EVIDENCE_UNAVAILABLE -> true;
                    default -> false;
                });
    }

    public static boolean assessmentLimited(TrackDiagnosis diagnosis) {
        return diagnosis.findings().stream().anyMatch(f -> f.reason() == TrackFinding.Reason.ASSESSMENT_LIMIT);
    }

    public ReviewPointPage points(long sourceId, String hash, int from, int limit) {
        var source = access.read(sourceId);
        access.requireUnchanged(source, hash);
        var points = GpxDocument.parse(access.gpx(source)).points();
        int start = Math.min(points.size(), Math.max(0, from));
        return new ReviewPointPage(points.size(), start, points.subList(start, Math.min(points.size(), start + Math.max(1, Math.min(limit, MAX_POINT_PAGE)))));
    }

    private static List<TrackObservation> previewPoints(GpxDocument document) {
        if (document.points().size() <= MAX_PREVIEW_POINTS) return document.points();
        // Keep recording endpoints as well as a bounded, evenly spaced overview. Full observations have a paging endpoint.
        Set<Integer> indexes = new TreeSet<>();
        for (TrackSection section : document.sections())
            if (section.pointCount() > 0) {
                indexes.add(section.firstPointIndex());
                indexes.add(section.firstPointIndex() + section.pointCount() - 1);
            }
        for (int i = 0; i < MAX_PREVIEW_POINTS; i++) indexes.add((int) ((long) i * (document.points().size() - 1) / (MAX_PREVIEW_POINTS - 1)));
        return indexes.stream().map(document.points()::get).toList();
    }
}
