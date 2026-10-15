package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackObservation;
import com.x8ing.mtl.server.gpsquality.diagnostics.GpsReviewThresholds;
import com.x8ing.mtl.server.mtlserver.metrics.MetricConstants;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

import java.util.*;

import static com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.*;

/**
 * Records actual import decisions and final corrections; never runs another outlier detector.
 */
public final class GpsProcessingAssessment {
    public static final String VERSION = "gps-processing-evidence-v5";
    private static final double REVIEW_HORIZONTAL_CORRECTION_METRES = 30;
    private static final double REVIEW_VERTICAL_CORRECTION_METRES = 20;
    private static final List<String> POSITION_CAPABILITIES = List.of("Map", "Distance");
    private static final List<String> ELEVATION_CAPABILITIES = List.of("Ascent", "Descent", "Energy");
    private static final Set<String> REVIEW_ACTIONS = Set.of("POSITION_REJECTED", "ELEVATION_REJECTED",
            "POSITION_DISCONTINUITY", "LARGE_POSITION_CORRECTION", "LARGE_ELEVATION_CORRECTION", "WEAK_SOURCE_FIX", "WEAK_VERTICAL_FIX", "TIMING_UNRELIABLE");

    private GpsProcessingAssessment() {
    }

    public static Map<String, String> processingSettings(Map<String, String> smoothingSettings) {
        Map<String, String> settings = new LinkedHashMap<>(smoothingSettings);
        settings.put("assessment", VERSION);
        settings.putAll(GPXReader.cleaningSettings());
        settings.putAll(RetainedPositionContinuity.settings());
        settings.putAll(BreakStopTreatment.cleaningSettings());
        settings.putAll(ElevationObservationFilter.cleaningSettings());
        settings.putAll(GpsObservationEvidence.qualitySettings());
        settings.put("review.horizontalCorrectionMetres", Double.toString(REVIEW_HORIZONTAL_CORRECTION_METRES));
        settings.put("review.verticalCorrectionMetres", Double.toString(REVIEW_VERTICAL_CORRECTION_METRES));
        settings.put("review.singleCorrectionMetres", Double.toString(GpsReviewThresholds.CORRECTION_METRES));
        settings.put("review.distributedCorrectionMetres", Double.toString(GpsReviewThresholds.DISTRIBUTED_CORRECTION_METRES));
        settings.put("review.affectedRatio", Double.toString(GpsReviewThresholds.AFFECTED_RATIO));
        settings.put("review.minimumAffectedPoints", Integer.toString(GpsReviewThresholds.MIN_AFFECTED_POINTS));
        settings.put("review.positionSpikeMetres", Double.toString(GpsReviewThresholds.POSITION_SPIKE_METRES));
        settings.put("review.elevationSpikeMetres", Double.toString(GpsReviewThresholds.ELEVATION_SPIKE_METRES));
        settings.put("review.weakFixRatio", Double.toString(GpsReviewThresholds.WEAK_FIX_RATIO));
        settings.put("review.weakFixSeconds", Double.toString(GpsReviewThresholds.WEAK_FIX_SECONDS));
        settings.put("review.backwardSeconds", Double.toString(GpsReviewThresholds.BACKWARD_SECONDS));
        settings.put("review.gapSeconds", Double.toString(GpsReviewThresholds.GAP_SECONDS));
        settings.put("review.gapDisplacementMetres", Double.toString(GpsReviewThresholds.GAP_DISPLACEMENT_METRES));
        settings.put("review.gapDistanceRatio", Double.toString(GpsReviewThresholds.GAP_DISTANCE_RATIO));
        return Map.copyOf(settings);
    }

    public static List<GpsProcessingIssue> record(GPXReader.LoadResult result) {
        List<GpsProcessingIssue> issues = new ArrayList<>();
        LineString raw = result.trackRAW, canonical = result.trackCleaned;
        if (raw == null) return List.of();
        Map<Integer, Integer> rawByCanonicalIndex = new HashMap<>();
        for (int i = 0; i < result.observationEvidence.size(); i++) {
            Integer index = result.observationEvidence.get(i).canonicalPointIndex();
            if (index != null) rawByCanonicalIndex.put(index, i);
        }
        for (int i = 0; i < result.observationEvidence.size() && i < raw.getNumPoints(); i++) {
            GpsObservationEvidence observation = result.observationEvidence.get(i);
            Integer index = observation.canonicalPointIndex();
            Coordinate original = raw.getCoordinateN(i);
            Coordinate corrected = index != null && canonical != null && index >= 0 && index < canonical.getNumPoints()
                    ? canonical.getCoordinateN(index) : null;
            Double horizontal = corrected == null ? null : GPXReader.getDistanceBetweenTwoWGS84(original, corrected);
            Double vertical = corrected == null || !Double.isFinite(original.getZ()) || !Double.isFinite(corrected.getZ())
                    ? null : Math.abs(original.getZ() - corrected.getZ());
            for (String reason : observation.decisions()) {
                Stage stage = Stage.TIMING;
                Reason findingReason = Reason.TIME_MISSING;
                Severity severity = Severity.INFORMATION;
                String message = "Import retained an observation without a usable timestamp.";
                String rule = result.cleaningRules.getOrDefault(observation.sourcePointIndex(), "distance-probation");
                Double impact = horizontal, altitude = vertical;
                List<String> capabilities = List.of("Duration", "Speed");
                switch (reason) {
                    case "POSITION_DISCONTINUITY" -> {
                        Integer previousIndex = index == null ? null : rawByCanonicalIndex.get(index - 1);
                        if (previousIndex == null) throw new IllegalStateException("Disconnected position has no preceding observation");
                        Coordinate previous = raw.getCoordinateN(previousIndex);
                        double distance = GPXReader.getDistanceBetweenTwoWGS84(previous, original);
                        double duration = original.getM() - previous.getM();
                        TrackFinding finding = new TrackFinding(Stage.POSITION, Reason.POSITION_JUMP, Severity.WARNING,
                                observation.sourceRecordingSection(), result.observationEvidence.get(previousIndex).sourcePointIndex(),
                                observation.sourcePointIndex(), 1, distance, "metres",
                                String.format(Locale.ROOT, "The source moves %.0f metres in %.1f seconds (%.0f km/h) across an unsupported GPS transition. "
                                                           + "Import retained both positions as separate spans; connecting distance and speed are unknown.",
                                        distance, duration, distance / duration * MetricConstants.MPS_TO_KMH),
                                List.of("Map", "Distance", "Speed"));
                        issues.add(new GpsProcessingIssue(i, index, reason,
                                result.cleaningRules.getOrDefault(observation.sourcePointIndex(), "distance-probation-relocation"),
                                distance, null, duration, finding));
                        continue;
                    }
                    case "POSITION_REJECTED" -> {
                        stage = Stage.POSITION;
                        findingReason = Reason.POSITION_JUMP;
                        severity = Severity.WARNING;
                        impact = neighbourDifference(raw, result.observationEvidence, i, false);
                        String explanation = switch (rule) {
                            case "isolated-position-return" -> "an isolated jump with a supported return";
                            case "invalid-zero-position" -> "an unusable zero coordinate";
                            default -> "an unsupported position jump";
                        };
                        message = "Import removed " + explanation + " from the canonical path. The original observation is retained in RAW.";
                        capabilities = POSITION_CAPABILITIES;
                    }
                    case "ELEVATION_REJECTED" -> {
                        stage = Stage.ELEVATION;
                        findingReason = Reason.ELEVATION_SPIKE;
                        severity = Severity.WARNING;
                        altitude = neighbourDifference(raw, result.observationEvidence, i, true);
                        rule = "bounded-elevation-excursion";
                        message = "Import cleared this altitude after the elevation filter rejected a bounded excursion. Position and timestamp are retained; altitude is unknown.";
                        capabilities = ELEVATION_CAPABILITIES;
                    }
                    case "ELEVATION_MISSING" -> {
                        stage = Stage.ELEVATION;
                        findingReason = Reason.ELEVATION_MISSING;
                        message = "The source observation has no usable altitude.";
                        capabilities = ELEVATION_CAPABILITIES;
                    }
                    case "TIMING_UNRELIABLE" -> {
                        findingReason = Reason.TIMING_UNRELIABLE;
                        message = "Import retained original timestamps in RAW and disabled unsupported canonical timing.";
                    }
                    case "TIME_EXCURSION" -> {
                        findingReason = Reason.TIME_EXCURSION;
                        message = "Import retained an isolated clock spike in RAW and excluded only its canonical timestamp.";
                        rule = "isolated-forward-clock-spike";
                    }
                    case "STOP_COLLAPSED" -> {
                        continue;
                    }
                    case "TIME_MISSING" -> {
                    }
                    default -> throw new IllegalStateException("Unrecorded GPS import decision: " + reason);
                }
                Double magnitude = stage == Stage.ELEVATION ? altitude : stage == Stage.POSITION ? impact : null;
                issues.add(issue(i, observation, reason, rule, impact, altitude, stage, findingReason, severity,
                        magnitude, message, capabilities));
            }
            if (horizontal != null && horizontal >= REVIEW_HORIZONTAL_CORRECTION_METRES)
                issues.add(issue(i, observation, "LARGE_POSITION_CORRECTION", "canonical-smoothing", horizontal, vertical,
                        Stage.POSITION, Reason.POSITION_CORRECTION, Severity.WARNING, horizontal,
                        "Import smoothing changed this retained position by at least " + REVIEW_HORIZONTAL_CORRECTION_METRES
                        + " metres. Compare RAW with the canonical path.", POSITION_CAPABILITIES));
            if (vertical != null && vertical >= REVIEW_VERTICAL_CORRECTION_METRES)
                issues.add(issue(i, observation, "LARGE_ELEVATION_CORRECTION", "canonical-smoothing", horizontal, vertical,
                        Stage.ELEVATION, Reason.ELEVATION_CORRECTION, Severity.WARNING, vertical,
                        "Import smoothing changed this retained altitude by at least " + REVIEW_VERTICAL_CORRECTION_METRES
                        + " metres. Compare RAW with the canonical altitude.", ELEVATION_CAPABILITIES));
            if (observation.weakHorizontalFix())
                issues.add(issue(i, observation, "WEAK_SOURCE_FIX", "source-fix-quality", horizontal, vertical,
                        Stage.POSITION, Reason.WEAK_SOURCE_FIX, Severity.WARNING, null,
                        "Import used a weak reported GPS fix with increased uncertainty. The observation was retained when supported by the path.", POSITION_CAPABILITIES));
            if (observation.weakVerticalFix() && Double.isFinite(original.getZ()))
                issues.add(issue(i, observation, "WEAK_VERTICAL_FIX", "source-fix-quality", horizontal, vertical,
                        Stage.ELEVATION, Reason.WEAK_SOURCE_FIX, Severity.WARNING, null,
                        "Import used altitude from a weak reported GPS fix with increased vertical uncertainty.", ELEVATION_CAPABILITIES));
        }
        List<TrackObservation> points = new ArrayList<>();
        for (int i = 0; i < result.observationEvidence.size() && i < raw.getNumPoints(); i++) {
            var evidence = result.observationEvidence.get(i);
            var coordinate = raw.getCoordinateN(i);
            points.add(new TrackObservation(evidence.sourcePointIndex(), evidence.sourceRecordingSection(), coordinate.getY(), coordinate.getX(),
                    Double.isFinite(coordinate.getZ()) ? coordinate.getZ() : null,
                    Double.isFinite(coordinate.getM()) ? java.time.Instant.ofEpochMilli(Math.round(coordinate.getM() * 1000)) : null, null, false));
        }
        return GpsProcessingReviewPolicy.classify(issues, points);
    }

    /**
     * Source checks already describe absent values and unsupported clocks. Only add recorded cleaning/quality actions.
     */
    public static List<TrackFinding> reviewFindings(List<GpsProcessingIssue> issues) {
        record Group(Stage stage, Reason reason, Severity severity, int section, String message, String unit) {
        }
        Map<Group, TrackFinding> grouped = new LinkedHashMap<>();
        for (var issue : issues) {
            if (issue.aggregateMetricImpact() || !REVIEW_ACTIONS.contains(issue.reason())) continue;
            TrackFinding finding = issue.finding();
            Group key = new Group(finding.stage(), finding.reason(), finding.severity(), finding.sectionIndex(), finding.message(), finding.unit());
            grouped.merge(key, finding, (before, next) -> new TrackFinding(before.stage(), before.reason(), before.severity(), before.sectionIndex(),
                    minimumIndex(before.firstPointIndex(), next.firstPointIndex()), maximumIndex(before.lastPointIndex(), next.lastPointIndex()),
                    before.affectedCount() + next.affectedCount(), maximumMagnitude(before.magnitude(), next.magnitude()),
                    before.unit(), before.message(), before.affectedCapabilities()));
        }
        return List.copyOf(grouped.values());
    }

    public static List<TrackFinding> reviewFindings(List<GpsProcessingIssue> issues, List<TrackObservation> originalPoints) {
        return reviewFindings(GpsProcessingReviewPolicy.classify(issues, originalPoints));
    }

    private static Double maximumMagnitude(Double first, Double second) {
        if (first == null) return second;
        if (second == null) return first;
        return Math.max(first, second);
    }

    private static Integer minimumIndex(Integer first, Integer second) {
        return first == null ? second : second == null ? first : Math.min(first, second);
    }

    private static Integer maximumIndex(Integer first, Integer second) {
        return first == null ? second : second == null ? first : Math.max(first, second);
    }

    private static GpsProcessingIssue issue(int rawIndex, GpsObservationEvidence observation, String reason, String rule,
                                            Double horizontal, Double vertical, Stage stage, Reason findingReason,
                                            Severity severity, Double magnitude, String message, List<String> capabilities) {
        int sourceIndex = observation.sourcePointIndex();
        TrackFinding finding = new TrackFinding(stage, findingReason, severity, observation.sourceRecordingSection(),
                sourceIndex, sourceIndex, 1, magnitude, magnitude == null ? null : "metres", message, capabilities);
        return new GpsProcessingIssue(rawIndex, observation.canonicalPointIndex(), reason, rule, horizontal, vertical, null, finding);
    }

    private static Double neighbourDifference(LineString raw, List<GpsObservationEvidence> evidence, int index, boolean vertical) {
        Coordinate point = raw.getCoordinateN(index);
        if (vertical && !Double.isFinite(point.getZ())) return null;
        double difference = 0;
        for (int neighbour : new int[]{index - 1, index + 1}) {
            if (neighbour < 0 || neighbour >= raw.getNumPoints() || neighbour >= evidence.size()
                || evidence.get(neighbour).recordingSection() != evidence.get(index).recordingSection()) continue;
            Coordinate adjacent = raw.getCoordinateN(neighbour);
            if (vertical && !Double.isFinite(adjacent.getZ())) continue;
            difference = Math.max(difference, vertical ? Math.abs(point.getZ() - adjacent.getZ())
                    : GPXReader.getDistanceBetweenTwoWGS84(point, adjacent));
        }
        return difference;
    }
}
