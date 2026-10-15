package com.x8ing.mtl.server.gpsquality.diagnostics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.*;

/**
 * Compares recorded evidence with processing results without inferring or repairing observations.
 */
public final class TrackProcessingDiagnosticEngine {
    private static final int FRAGMENT_WARNING_MINIMUM = 8;
    private static final int FRAGMENTS_PER_SECTION_WARNING = 4;
    private static final double TIMESTAMP_TOLERANCE_SECONDS = 2;
    private static final double DURATION_TOLERANCE_RATIO = 0.01;
    private static final double DISTANCE_WARNING_RATIO = 2;
    private static final double DISTANCE_WARNING_EXCESS_METRES = 1000;
    private static final List<String> TIME_CAPABILITIES = List.of("Duration", "Speed", "Power", "Replay", "Sensor alignment");

    public TrackDiagnosis diagnose(TrackDiagnosis source, List<ProcessedRecording> processed) {
        List<TrackFinding> findings = new ArrayList<>(source.findings().stream().filter(f -> f.stage() != Stage.PROCESSING).toList());
        List<ProcessedRecording> current = processed.stream()
                .filter(p -> p.sourceHash() == null || p.sourceHash().equals(source.sourceHash())).toList();
        if (!source.sections().isEmpty() && !current.isEmpty()) {
            int originalSections = source.sections().size();
            if (current.size() >= FRAGMENT_WARNING_MINIMUM && current.size() >= (long) originalSections * FRAGMENTS_PER_SECTION_WARNING)
                findings.add(new TrackFinding(Stage.PROCESSING, Reason.EXCESSIVE_FRAGMENTATION, Severity.INFORMATION, -1,
                        null, null, current.size(), (double) current.size() / originalSections, "tracks per source section",
                        "Processing produced " + current.size() + " tracks from " + originalSections
                        + " original recording section(s). Inspect the boundaries and source findings; this count alone does not establish corruption.", List.of("Track grouping")));

            // Unknown legacy mappings can only be compared with the complete source, never an invented section.
            var unknown = current.stream().filter(p -> p.sectionIndex() == null
                                                       || source.sections().stream().noneMatch(s -> s.section().sectionIndex() == p.sectionIndex())).toList();
            compare(findings, -1, bounds(source.sections()), unknown);
            for (var section : source.sections()) {
                int index = section.section().sectionIndex();
                compare(findings, index, bounds(List.of(section)), current.stream()
                        .filter(p -> p.sectionIndex() != null && p.sectionIndex() == index).toList());
            }
        }
        return withFindings(source, findings);
    }

    public static TrackDiagnosis withFindings(TrackDiagnosis source, List<TrackFinding> findings) {
        var sections = source.sections().stream().map(s -> new TrackDiagnosis.SectionDiagnosis(s.section(),
                s.timedPointCount(), s.backwardJumpCount(), s.missingTimeCount(), s.largestBackwardJumpSeconds(),
                s.earliestTimestamp(), s.latestTimestamp(), s.geometryLengthMetres(), findings.stream().anyMatch(f ->
                TrackDiagnosticEngine.requiresReview(f) && (f.sectionIndex() < 0 || f.sectionIndex() == s.section().sectionIndex())),
                s.estimatedTiming())).toList();
        return new TrackDiagnosis(source.algorithmVersion(), source.sourceHash(), source.pointCount(),
                findings.stream().anyMatch(f -> TrackDiagnosticEngine.requiresReview(f)), source.estimatedTiming(), sections, List.copyOf(findings));
    }

    private static void compare(List<TrackFinding> findings, int section, Bounds source, List<ProcessedRecording> tracks) {
        int inconsistent = 0, outsideSource = 0, reversed = 0, invalidMoving = 0;
        double largestExcessSeconds = 0, processedLength = 0;
        for (var track : tracks) {
            double excess = 0;
            if (source.first() != null && source.last() != null) {
                for (Instant timestamp : new Instant[]{track.startTimestamp(), track.endTimestamp()}) {
                    if (timestamp == null) continue;
                    excess = Math.max(excess, Math.max(TrackDiagnosticEngine.seconds(timestamp, source.first()),
                            TrackDiagnosticEngine.seconds(source.last(), timestamp)));
                }
                if (excess > TIMESTAMP_TOLERANCE_SECONDS) outsideSource++;
                double elapsed = TrackDiagnosticEngine.seconds(track.startTimestamp(), track.endTimestamp());
                if (Double.isFinite(elapsed)) {
                    if (elapsed < -TIMESTAMP_TOLERANCE_SECONDS) {
                        reversed++;
                        excess = Math.max(excess, -elapsed);
                    }
                    if (finite(track.movingDurationSeconds())) {
                        double tolerance = Math.max(TIMESTAMP_TOLERANCE_SECONDS, Math.abs(elapsed) * DURATION_TOLERANCE_RATIO);
                        if (track.movingDurationSeconds() < -TIMESTAMP_TOLERANCE_SECONDS) {
                            invalidMoving++;
                            excess = Math.max(excess, -track.movingDurationSeconds());
                        } else if (track.movingDurationSeconds() > Math.max(0, elapsed) + tolerance) {
                            invalidMoving++;
                            excess = Math.max(excess, track.movingDurationSeconds() - Math.max(0, elapsed));
                        }
                    }
                }
            }
            if (excess > TIMESTAMP_TOLERANCE_SECONDS) {
                inconsistent++;
                largestExcessSeconds = Math.max(largestExcessSeconds, excess);
            }
            if (finite(track.distanceMetres())) processedLength += Math.max(0, track.distanceMetres());
        }
        List<String> timeProblems = new ArrayList<>();
        if (outsideSource > 0) timeProblems.add("Processed dates fall outside the source timestamps in " + outsideSource
                                                + " track(s). The source range is " + source.first() + " to " + source.last() + ".");
        if (reversed > 0) timeProblems.add("Processed start dates follow their end dates in " + reversed + " track(s).");
        if (invalidMoving > 0) timeProblems.add("Processed moving duration is negative or exceeds elapsed time in " + invalidMoving + " track(s).");
        if (inconsistent > 0) findings.add(new TrackFinding(Stage.PROCESSING, Reason.INCONSISTENT_TIME_DOMAIN,
                Severity.REVIEW, section, null, null, inconsistent, largestExcessSeconds, "seconds",
                String.join(" ", timeProblems) + " Source observations and processing results disagree.", TIME_CAPABILITIES));
        if (source.length() > 0 && processedLength > source.length() * DISTANCE_WARNING_RATIO
            && processedLength - source.length() > DISTANCE_WARNING_EXCESS_METRES)
            findings.add(new TrackFinding(Stage.PROCESSING, Reason.INCONSISTENT_METRIC_TOTALS, Severity.REVIEW,
                    section, null, null, tracks.size(), processedLength - source.length(), "metres",
                    "The combined processed distance substantially exceeds the source path length. Check duplicate fragments and processing results.", List.of("Distance", "Statistics")));
    }

    private record Bounds(Instant first, Instant last, double length) {
    }

    private static Bounds bounds(List<TrackDiagnosis.SectionDiagnosis> sections) {
        Instant first = null, last = null;
        double length = 0;
        for (var section : sections) {
            if (section.earliestTimestamp() != null && (first == null || section.earliestTimestamp().isBefore(first))) first = section.earliestTimestamp();
            if (section.latestTimestamp() != null && (last == null || section.latestTimestamp().isAfter(last))) last = section.latestTimestamp();
            length += section.geometryLengthMetres();
        }
        return new Bounds(first, last, length);
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
