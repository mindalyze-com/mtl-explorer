package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notes describing noteworthy events inside a segment (previous trigger point →
 * this trigger point). Computed on-the-fly from detected track events that
 * overlap the two crossing timestamps.
 *
 * <p>Currently reports <b>stops</b>: persisted stop events produced during
 * import by the dense GPS stop detector. This lets the client annotate
 * segments where the racer paused and stats would otherwise be misleading
 * (e.g. "2 stops · 2m 40s").
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonPropertyOrder({
        "stopCount",
        "totalStoppedSec",
        "longestStopSec"
})
public class SegmentNotes {

    /**
     * Number of stops detected inside the segment.
     */
    public int stopCount;

    /**
     * Total time in seconds spent stopped during this segment.
     */
    public double totalStoppedSec;

    /**
     * Duration of the single longest stop in seconds.
     */
    public double longestStopSec;
}
