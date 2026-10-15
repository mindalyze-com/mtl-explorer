package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpsReviewThresholds;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackObservation;

import java.util.*;

import static com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Severity.*;
import static com.x8ing.mtl.server.gpsquality.diagnostics.GpsReviewThresholds.*;

/** Classifies recorded import actions with their original section and observation coverage. */
final class GpsProcessingReviewPolicy {
    private record Key(int section, String reason) {
    }
    private record Counts(int observations, int positions, int elevations) {
    }

    private GpsProcessingReviewPolicy() {
    }

    static List<GpsProcessingIssue> classify(List<GpsProcessingIssue> issues, List<TrackObservation> points) {
        Map<Integer, List<TrackObservation>> sections = new HashMap<>();
        for (var point : points) sections.computeIfAbsent(point.sectionIndex(), ignored -> new ArrayList<>()).add(point);
        Map<Integer, Counts> counts = new HashMap<>();
        sections.forEach((section, observations) -> counts.put(section, new Counts(observations.size(),
                (int) observations.stream().filter(TrackObservation::hasPosition).count(),
                (int) observations.stream().filter(p -> p.elevationMetres() != null).count())));
        Map<Integer, Boolean> weakReview = new HashMap<>();
        Map<Key, Set<Integer>> affected = new HashMap<>();
        for (var issue : issues) {
            if (issue.rawPointIndex() == null || issue.finding().firstPointIndex() == null) continue;
            boolean distributed = !issue.reason().startsWith("LARGE_")
                    || (issue.reason().equals("LARGE_POSITION_CORRECTION") ? value(issue.horizontalMetres()) : value(issue.verticalMetres()))
                       >= DISTRIBUTED_CORRECTION_METRES;
            if (distributed) affected.computeIfAbsent(new Key(issue.finding().sectionIndex(), issue.reason()), ignored -> new HashSet<>())
                    .add(issue.finding().firstPointIndex());
        }
        List<GpsProcessingIssue> result = new ArrayList<>(issues.size());
        for (var issue : issues) {
            if (issue.aggregateMetricImpact()) continue;
            TrackFinding f = issue.finding();
            var section = sections.getOrDefault(f.sectionIndex(), List.of());
            var indices = affected.getOrDefault(new Key(f.sectionIndex(), issue.reason()), Set.of());
            var coverage = counts.getOrDefault(f.sectionIndex(), new Counts(0, 0, 0));
            boolean review = switch (issue.reason()) {
                case "POSITION_DISCONTINUITY" -> true;
                case "TIMING_UNRELIABLE" -> true;
                case "POSITION_REJECTED" -> value(f.magnitude()) >= POSITION_SPIKE_METRES
                        || substantialCoverage(indices.size(), coverage.observations()) && !section.isEmpty();
                case "ELEVATION_REJECTED" -> value(f.magnitude()) >= ELEVATION_SPIKE_METRES
                        || substantialCoverage(indices.size(), coverage.elevations()) && !section.isEmpty();
                case "LARGE_POSITION_CORRECTION" -> value(issue.horizontalMetres()) >= CORRECTION_METRES
                        || substantialCoverage(indices.size(), coverage.positions()) && !section.isEmpty();
                case "LARGE_ELEVATION_CORRECTION" -> value(issue.verticalMetres()) >= CORRECTION_METRES
                        || substantialCoverage(indices.size(), coverage.elevations()) && !section.isEmpty();
                case "WEAK_SOURCE_FIX", "WEAK_VERTICAL_FIX" -> weakReview.computeIfAbsent(f.sectionIndex(),
                        index -> weakFixReview(section, issues, index));
                default -> false;
            };
            var classified = new TrackFinding(f.stage(), f.reason(), review ? REVIEW : INFORMATION, f.sectionIndex(),
                    f.firstPointIndex(), f.lastPointIndex(), f.affectedCount(), f.magnitude(), f.unit(), f.message(), f.affectedCapabilities());
            result.add(new GpsProcessingIssue(issue.rawPointIndex(), issue.canonicalPointIndex(), issue.reason(), issue.rule(),
                    issue.horizontalMetres(), issue.verticalMetres(), issue.durationSeconds(), classified));
        }
        return List.copyOf(result);
    }

    private static boolean weakFixReview(List<TrackObservation> section, List<GpsProcessingIssue> issues, int sectionIndex) {
        if (section.isEmpty()) return false;
        Set<Integer> weak = new HashSet<>();
        for (var issue : issues)
            if (issue.finding().sectionIndex() == sectionIndex && (issue.reason().equals("WEAK_SOURCE_FIX") || issue.reason().equals("WEAK_VERTICAL_FIX")))
                weak.add(issue.finding().firstPointIndex());
        long positions = section.stream().filter(TrackObservation::hasPosition).count();
        if (positions > 0 && weak.size() >= positions * WEAK_FIX_RATIO) return true;
        TrackObservation start = null, previous = null;
        for (var point : section) {
            if (!weak.contains(point.pointIndex()) || point.timestamp() == null || !point.hasPosition()) {
                start = previous = null;
                continue;
            }
            double dt = previous == null ? Double.NaN : TrackDiagnosticEngine.seconds(previous.timestamp(), point.timestamp());
            if (start == null || !Double.isFinite(dt) || dt < 0 || dt > SUPPORTED_INTERVAL_SECONDS) start = point;
            if (TrackDiagnosticEngine.seconds(start.timestamp(), point.timestamp()) >= WEAK_FIX_SECONDS) return true;
            previous = point;
        }
        return false;
    }

    private static double value(Double value) {
        return value == null || !Double.isFinite(value) ? 0 : value;
    }
}
