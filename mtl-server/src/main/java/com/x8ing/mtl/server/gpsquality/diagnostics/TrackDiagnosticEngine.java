package com.x8ing.mtl.server.gpsquality.diagnostics;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.*;

/**
 * Source structure and clock checks. GPS cleaning findings belong to the import pipeline.
 */
public final class TrackDiagnosticEngine {
    public static final String VERSION = "track-diagnostics-v6";
    public static final double LARGE_CLOCK_JUMP_SECONDS = RecordingTimingAnalysis.LARGE_CLOCK_JUMP_SECONDS;
    private static final double MIN_RECORDING_GAP_SECONDS = 300;
    private static final double RECORDING_GAP_INTERVAL_RATIO = 10;
    private static final double EARTH_RADIUS_METRES = 6_371_008.8;
    private static final List<String> TIME_CAPABILITIES = List.of("Duration", "Speed", "Power", "Replay", "Sensor alignment");

    public TrackDiagnosis diagnose(GpxDocument document) {
        List<TrackFinding> findings = new ArrayList<>();
        List<TrackDiagnosis.SectionDiagnosis> sections = new ArrayList<>();
        for (TrackSection section : document.sections()) {
            List<TrackObservation> points = document.points().subList(section.firstPointIndex(), section.firstPointIndex() + section.pointCount());
            var clock = RecordingTimingAnalysis.analyze(() -> points.stream().map(TrackObservation::timestamp).iterator());
            Set<Integer> isolatedTimes = new HashSet<>(clock.isolatedForwardSpikeIndexes());
            double[] intervals = java.util.stream.IntStream.range(1, points.size())
                    .filter(i -> !isolatedTimes.contains(i - 1) && !isolatedTimes.contains(i))
                    .mapToDouble(i -> seconds(points.get(i - 1).timestamp(), points.get(i).timestamp()))
                    .filter(value -> Double.isFinite(value) && value > 0).sorted().toArray();
            double gapThreshold = Math.max(MIN_RECORDING_GAP_SECONDS,
                    intervals.length == 0 ? MIN_RECORDING_GAP_SECONDS : intervals[(intervals.length - 1) / 2] * RECORDING_GAP_INTERVAL_RATIO);
            int firstFinding = findings.size(), timed = 0, backwards = 0, duplicates = 0, invalidPositions = 0, missingElevation = 0;
            int firstBackward = clock.firstBackwardIndex() < 0 ? -1 : points.get(clock.firstBackwardIndex()).pointIndex();
            double maxBackward = clock.maxBackwardSeconds(), length = 0, maxGap = 0;
            double supportedLength = 0, gapDisplacement = 0, maxGapDisplacement = 0;
            int gaps = 0, firstGap = -1, lastGap = -1;
            int firstMissing = -1, lastMissing = -1, firstDuplicate = -1, lastDuplicate = -1;
            int lastBackward = clock.lastBackwardIndex() < 0 ? -1 : points.get(clock.lastBackwardIndex()).pointIndex();
            backwards = clock.backwardCount();
            boolean estimated = false;
            Instant earliest = null, latest = null;
            for (int i = 0; i < points.size(); i++) {
                TrackObservation point = points.get(i);
                estimated |= point.estimatedTime();
                if (!point.hasPosition()) invalidPositions++;
                if (point.elevationMetres() == null) missingElevation++;
                if (point.timestamp() == null) {
                    if (firstMissing < 0) firstMissing = point.pointIndex();
                    lastMissing = point.pointIndex();
                }
                if (point.timestamp() != null) {
                    timed++;
                    if (earliest == null || point.timestamp().isBefore(earliest)) earliest = point.timestamp();
                    if (latest == null || point.timestamp().isAfter(latest)) latest = point.timestamp();
                }
                if (i == 0) continue;
                TrackObservation previous = points.get(i - 1);
                double dt = seconds(previous.timestamp(), point.timestamp());
                if (isolatedTimes.contains(i - 1) || isolatedTimes.contains(i)) dt = Double.NaN;
                if (dt == 0) {
                    duplicates++;
                    if (firstDuplicate < 0) firstDuplicate = point.pointIndex();
                    lastDuplicate = point.pointIndex();
                }
                double distance = distanceMetres(previous, point);
                if (Double.isFinite(dt) && (dt > gapThreshold || dt >= GpsReviewThresholds.GAP_SECONDS)) {
                    gaps++;
                    maxGap = Math.max(maxGap, dt);
                    if (firstGap < 0) firstGap = previous.pointIndex();
                    lastGap = point.pointIndex();
                    if (Double.isFinite(distance)) {
                        gapDisplacement += distance;
                        maxGapDisplacement = Math.max(maxGapDisplacement, distance);
                    }
                }
                if (Double.isFinite(distance)) length += distance;
                if (Double.isFinite(distance) && dt > 0 && dt <= GpsReviewThresholds.SUPPORTED_INTERVAL_SECONDS)
                    supportedLength += distance;
            }
            int missing = points.size() - timed;
            if (missing > 0)
                add(findings, section, Stage.TIMING, Reason.TIME_MISSING,
                        !"ROUTE".equals(section.kind()) && (timed == 0 || GpsReviewThresholds.substantialCoverage(missing, points.size()))
                                ? Severity.REVIEW : Severity.INFORMATION,
                        firstMissing, missing, null, null, timed == 0 ? "No recorded timestamps; geometry can still be reviewed." : missing + " observations have no usable timestamp.", TIME_CAPABILITIES);
            if (duplicates > 0) add(findings, section, Stage.TIMING, Reason.TIME_DUPLICATE, Severity.INFORMATION,
                    firstDuplicate, duplicates, null, null, duplicates + " observations repeat the preceding timestamp.", TIME_CAPABILITIES);
            if (backwards > 0) add(findings, section, Stage.TIMING, Reason.TIME_BACKWARD,
                    clock.backwardRequiresReview() ? Severity.REVIEW : Severity.INFORMATION,
                    firstBackward, backwards, maxBackward, "seconds", clock.backwardMessage(), TIME_CAPABILITIES);
            if (clock.excursionCount() > 0) add(findings, section, Stage.TIMING, Reason.TIME_EXCURSION, Severity.REVIEW,
                    points.get(clock.firstExcursionIndex()).pointIndex(), clock.excursionCount(), clock.maxExcursionSeconds(), "seconds",
                    "Isolated date jumps conflict with the surrounding recording timeline.", TIME_CAPABILITIES);
            if (clock.needsContainment()) add(findings, section, Stage.TIMING, Reason.TIMING_UNRELIABLE, Severity.REVIEW,
                    null, points.size(), null, null,
                    "A systemic clock problem makes section timing unsupported. Import retains original timestamps in RAW and disables derived timing.", TIME_CAPABILITIES);
            if (estimated) add(findings, section, Stage.TIMING, Reason.TIME_ESTIMATED, Severity.REVIEW,
                    null, (int) points.stream().filter(TrackObservation::estimatedTime).count(), null, null,
                    "Timing is marked as generated or altered; it does not establish historical speed or pauses.", TIME_CAPABILITIES);
            if (invalidPositions > 0) add(findings, section, Stage.POSITION, Reason.POSITION_INVALID,
                    invalidPositions == points.size() || GpsReviewThresholds.substantialCoverage(invalidPositions, points.size())
                            ? Severity.REVIEW : Severity.INFORMATION,
                    null, invalidPositions, null, null, invalidPositions + " observations have invalid or missing coordinates.", List.of("Map", "Distance"));
            boolean gapReview = maxGap >= GpsReviewThresholds.GAP_SECONDS
                                || maxGapDisplacement >= GpsReviewThresholds.GAP_DISPLACEMENT_METRES
                                || gapDisplacement >= GpsReviewThresholds.GAP_DISPLACEMENT_METRES
                                   && gapDisplacement >= supportedLength * GpsReviewThresholds.GAP_DISTANCE_RATIO;
            if (gaps > 0) findings.add(new TrackFinding(Stage.TIMING, Reason.RECORDING_GAP, gapReview ? Severity.REVIEW : Severity.INFORMATION,
                    section.sectionIndex(), firstGap, lastGap, gaps, maxGap, "seconds",
                    gaps + " unusually long interval(s) contain no recorded observations. Points may be missing, or recording may have been paused. The file cannot establish the path travelled during these gaps.",
                    List.of("Map", "Distance", "Duration", "Speed")));
            if (section.sectionIndex() > 0 && section.pointCount() > 0)
                add(findings, section, Stage.POSITION, Reason.RECORDING_BREAK, Severity.INFORMATION, section.firstPointIndex(), 1, null, null,
                        "A new source section starts here. The file records no connecting path from the preceding section; this may be an intentional recording break.", List.of("Map", "Distance"));
            if (missingElevation > 0) add(findings, section, Stage.ELEVATION, Reason.ELEVATION_MISSING, Severity.INFORMATION,
                    null, missingElevation, null, null, missingElevation + " observations have no elevation.", List.of("Ascent", "Descent", "Energy"));
            Map<String, List<GpxDocument.ParseProblem>> problems = new LinkedHashMap<>();
            for (var problem : document.problems())
                if (problem.pointIndex() >= section.firstPointIndex() && problem.pointIndex() < section.firstPointIndex() + section.pointCount())
                    problems.computeIfAbsent(problem.field(), field -> new ArrayList<>()).add(problem);
            boolean timeReview = !"ROUTE".equals(section.kind())
                    && (timed == 0 || GpsReviewThresholds.substantialCoverage(missing, points.size()));
            boolean positionReview = invalidPositions == points.size()
                    || GpsReviewThresholds.substantialCoverage(invalidPositions, points.size());
            problems.forEach((field, occurrences) -> {
                var first = occurrences.getFirst();
                boolean material = "time".equals(field) ? timeReview
                        : ("latitude".equals(field) || "longitude".equals(field))
                          && positionReview;
                findings.add(new TrackFinding(Stage.PARSING, Reason.INVALID_VALUE, material ? Severity.REVIEW : Severity.INFORMATION, section.sectionIndex(),
                        first.pointIndex(), occurrences.getLast().pointIndex(), occurrences.size(), null, null,
                        field + ": " + first.message(), List.of(field)));
            });
            for (int f = firstFinding; f < findings.size(); f++) {
                TrackFinding finding = findings.get(f);
                Integer last = switch (finding.reason()) {
                    case TIME_MISSING -> lastMissing;
                    case TIME_DUPLICATE -> lastDuplicate;
                    case TIME_BACKWARD -> lastBackward;
                    default -> finding.lastPointIndex();
                };
                if (!Objects.equals(last, finding.lastPointIndex())) findings.set(f, new TrackFinding(finding.stage(), finding.reason(), finding.severity(),
                        finding.sectionIndex(), finding.firstPointIndex(), last, finding.affectedCount(), finding.magnitude(), finding.unit(), finding.message(), finding.affectedCapabilities()));
            }
            boolean review = findings.subList(firstFinding, findings.size()).stream().anyMatch(f -> requiresReview(f));
            sections.add(new TrackDiagnosis.SectionDiagnosis(section, timed, backwards, missing, maxBackward, earliest, latest, length, review, estimated));
        }
        if (document.points().isEmpty()) findings.add(new TrackFinding(Stage.PARSING, Reason.NO_OBSERVATIONS, Severity.REVIEW,
                -1, null, null, 0, null, null, "The source has no track or route observations.", List.of("Map", "Statistics")));
        return new TrackDiagnosis(VERSION, document.sourceHash(), document.points().size(), findings.stream().anyMatch(f -> requiresReview(f)),
                sections.stream().anyMatch(TrackDiagnosis.SectionDiagnosis::estimatedTiming), List.copyOf(sections), List.copyOf(findings));
    }

    public static boolean requiresReview(TrackFinding finding) {
        return finding.severity() == Severity.REVIEW && finding.reason() != Reason.ASSESSMENT_LIMIT;
    }

    private static void add(List<TrackFinding> findings, TrackSection section, Stage stage, Reason reason, Severity severity,
                            Integer first, int count, Double magnitude, String unit, String message, List<String> capabilities) {
        findings.add(new TrackFinding(stage, reason, severity, section.sectionIndex(), first,
                first == null ? null : first, count, magnitude, unit, message, capabilities));
    }

    public static double seconds(Instant from, Instant to) {
        if (from == null || to == null) return Double.NaN;
        Duration duration = Duration.between(from, to);
        return duration.getSeconds() + duration.getNano() / 1_000_000_000.0;
    }

    public static double distanceMetres(TrackObservation from, TrackObservation to) {
        if (!from.hasPosition() || !to.hasPosition()) return Double.NaN;
        double lat1 = Math.toRadians(from.latitude()), lat2 = Math.toRadians(to.latitude());
        double latitude = lat2 - lat1, longitude = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.pow(Math.sin(latitude / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(longitude / 2), 2);
        return EARTH_RADIUS_METRES * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
