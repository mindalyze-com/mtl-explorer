package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Clipped stationary time and visible break events inside the preceding traversal.
 * Short pauses contribute to stopped time without becoming visible break events.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonPropertyOrder({
        "stopCount",
        "totalStoppedSec",
        "longestStopSec",
        "coverageComplete"
})
public class SegmentNotes {

    /**
     * Number of detected visible break events overlapping the segment.
     */
    public int stopCount;

    /**
     * Total time in seconds spent stopped during this segment.
     */
    public double totalStoppedSec;

    /**
     * Duration of the longest visible break's overlap with the segment, in seconds.
     */
    public double longestStopSec;

    /**
     * Whether motion and path support cover the whole traversal; false makes these observed lower bounds.
     */
    public boolean coverageComplete = true;

    public SegmentNotes(int stopCount, double totalStoppedSec, double longestStopSec) {
        this(stopCount, totalStoppedSec, longestStopSec, true);
    }
}
