package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@JsonPropertyOrder({
        "triggerPoint",
        "gpsTrackDataPoint",
        "gpsTrackId",
        "distanceToTriggerPointInMeter",
        "trackPointPosition",
        "timingStatusSinceLastTriggerPoint",
        "recordingGapSinceLastTriggerPoint",
        "timeInSecSinceLastTriggerPoint",
        "distanceInMeterSinceLastTriggerPoint",
        "avgSpeedSinceLastTriggerPoint",
        "segmentNotesSinceLastTriggerPoint"
})
public class Crossing {

    public TriggerPoint triggerPoint;
    public GpsTrackDataPoint gpsTrackDataPoint;
    public Long gpsTrackId;
    public double distanceToTriggerPointInMeter;
    /**
     * Fractional position in the canonical point-index sequence; the point ID is only an anchor.
     */
    public Double trackPointPosition;
    @JsonIgnore
    public Integer visitNumber;
    @JsonIgnore
    public int recordingSectionIndex;
    @JsonIgnore
    public boolean timingAmbiguous;
    public SegmentTimingStatus timingStatusSinceLastTriggerPoint = SegmentTimingStatus.NO_PREVIOUS_CROSSING;
    public boolean recordingGapSinceLastTriggerPoint;
    public Double timeInSecSinceLastTriggerPoint;
    public Double distanceInMeterSinceLastTriggerPoint;
    public Double avgSpeedSinceLastTriggerPoint;

    /**
     * Notes about the segment that ends at this crossing (previous crossing on
     * the same track → this crossing). {@code null} on the first crossing of a
     * track (no preceding segment). Currently reports stops detected inside
     * the segment so the client can warn when segment stats are biased by a
     * pause.
     */
    public SegmentNotes segmentNotesSinceLastTriggerPoint;

    public Crossing(TriggerPoint triggerPoint, GpsTrackDataPoint point, Long trackId,
                    double distanceToTrigger, double time, double distance, double speed, SegmentNotes notes) {
        this.triggerPoint = triggerPoint;
        this.gpsTrackDataPoint = point;
        this.gpsTrackId = trackId;
        this.distanceToTriggerPointInMeter = distanceToTrigger;
        this.timeInSecSinceLastTriggerPoint = time;
        this.distanceInMeterSinceLastTriggerPoint = distance;
        this.avgSpeedSinceLastTriggerPoint = speed;
        this.segmentNotesSinceLastTriggerPoint = notes;
    }
}
