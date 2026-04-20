package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class Crossing {

    public TriggerPoint triggerPoint;
    //public CoordinateXYZM coordinate;
    public GpsTrackDataPoint gpsTrackDataPoint;
    public Long gpsTrackId;
    public double distanceToTriggerPointInMeter;
    public double timeInSecSinceLastTriggerPoint;
    public double distanceInMeterSinceLastTriggerPoint;
    public double avgSpeedSinceLastTriggerPoint;
    //public Date time;

    /**
     * Notes about the segment that ends at this crossing (previous crossing on
     * the same track → this crossing). {@code null} on the first crossing of a
     * track (no preceding segment). Currently reports stops detected inside
     * the segment so the client can warn when segment stats are biased by a
     * pause.
     */
    public SegmentNotes segmentNotesSinceLastTriggerPoint;

}