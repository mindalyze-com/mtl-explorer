package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notes describing noteworthy events inside a segment (previous trigger point →
 * this trigger point). Computed on-the-fly from the raw GPS track samples that
 * fall between the two crossing timestamps.
 *
 * <p>Currently reports <b>stops</b>: contiguous runs where the recorded speed
 * stays below a threshold for at least a minimum duration. This lets the
 * client annotate segments where the racer paused and stats would otherwise
 * be misleading (e.g. "2 stops · 2m 40s").
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
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
