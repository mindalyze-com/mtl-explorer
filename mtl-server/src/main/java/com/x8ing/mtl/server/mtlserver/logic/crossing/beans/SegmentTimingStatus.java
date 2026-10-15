package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether the preceding traversal supports an elapsed-time measurement.
 */
@Schema(enumAsRef = true)
public enum SegmentTimingStatus {
    AVAILABLE,
    NO_PREVIOUS_CROSSING,
    MISSING_TIME,
    RECORDING_GAP,
    AMBIGUOUS_DWELL,
    INVALID_TIME
}
