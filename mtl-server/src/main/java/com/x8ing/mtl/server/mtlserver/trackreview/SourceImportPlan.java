package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosis;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;

import java.util.List;

/**
 * Application adapter for source timing eligibility and the shared importer.
 */
public final class SourceImportPlan {
    private static final int MIN_TIMED_POINTS_FOR_INTERVAL = 2;

    private SourceImportPlan() {
    }

    public static boolean requiresContainment(TrackDiagnosis diagnosis) {
        return diagnosis.sections().stream().anyMatch(s -> sourceTimingUnreliable(diagnosis, s.section().sectionIndex()));
    }

    public static boolean unreliableTiming(TrackDiagnosis diagnosis, int section) {
        boolean noMeasuredInterval = diagnosis.sections().stream().filter(s -> s.section().sectionIndex() == section)
                .anyMatch(s -> s.timedPointCount() < MIN_TIMED_POINTS_FOR_INTERVAL
                               || s.earliestTimestamp() == null || s.latestTimestamp() == null
                               || !s.earliestTimestamp().isBefore(s.latestTimestamp()));
        return noMeasuredInterval || sourceTimingUnreliable(diagnosis, section) || diagnosis.findings().stream().anyMatch(f ->
                (f.sectionIndex() < 0 || f.sectionIndex() == section) && f.reason() == TrackFinding.Reason.INCONSISTENT_TIME_DOMAIN
                && f.severity() == TrackFinding.Severity.REVIEW);
    }

    private static boolean sourceTimingUnreliable(TrackDiagnosis diagnosis, int section) {
        return diagnosis.findings().stream().anyMatch(f -> f.sectionIndex() == section
                && f.severity() == TrackFinding.Severity.REVIEW && switch (f.reason()) {
                    case TIME_ESTIMATED, TIMING_UNRELIABLE -> true;
                    default -> false;
                });
    }

    public static List<GPXReader.LoadResult> load(IndexedFile file, SourceReviewService.AssessedSource assessed) {
        // All imports use the reader's clock containment and cleaning pipeline.
        // The assessed representation also avoids converting a non-GPX source twice.
        return new GPXReader().importGpxXml(file, assessed.document().sourceXml());
    }

}
