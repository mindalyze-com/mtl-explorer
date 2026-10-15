package com.x8ing.mtl.server.gpsquality.diagnostics;

import java.util.List;

/**
 * Evidence is kept separately from review policy and user decisions.
 */
public record TrackFinding(Stage stage, Reason reason, Severity severity, int sectionIndex,
                           Integer firstPointIndex, Integer lastPointIndex, int affectedCount,
                           Double magnitude, String unit, String message, List<String> affectedCapabilities) {
    public enum Stage {READ, CONVERSION, PARSING, TIMING, POSITION, ELEVATION, PROCESSING}

    public enum Severity {INFORMATION, WARNING, REVIEW}

    public enum Reason {
        EMPTY_FILE, SOURCE_UNREADABLE, CONVERSION_FAILED, PARSE_FAILED, ASSESSMENT_LIMIT, INVALID_VALUE, NO_OBSERVATIONS,
        TIME_MISSING, TIME_DUPLICATE, TIME_BACKWARD, TIME_EXCURSION, TIME_ESTIMATED, TIMING_UNRELIABLE,
        POSITION_INVALID, POSITION_JUMP, POSITION_CORRECTION, WEAK_SOURCE_FIX, RECORDING_GAP, RECORDING_BREAK, ELEVATION_MISSING, ELEVATION_SPIKE, ELEVATION_CORRECTION,
        EXCESSIVE_FRAGMENTATION, INCONSISTENT_TIME_DOMAIN, INCONSISTENT_METRIC_TOTALS, PROCESSING_FAILED, PROCESSING_EVIDENCE_UNAVAILABLE, PROCESSED_SOURCE_CHANGED
    }
}
