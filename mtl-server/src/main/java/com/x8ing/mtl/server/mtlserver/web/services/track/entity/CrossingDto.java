package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateSummary;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.Crossing;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.TriggerPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "triggerPoint",
        "gpsTrackDataPoint",
        "gpsTrackId",
        "distanceToTriggerPointInMeter",
        "trackPointPosition",
        "visitNumber",
        "timingStatusSinceLastTriggerPoint",
        "recordingGapSinceLastTriggerPoint",
        "timeInSecSinceLastTriggerPoint",
        "distanceInMeterSinceLastTriggerPoint",
        "avgSpeedSinceLastTriggerPoint",
        "segmentNotesSinceLastTriggerPoint",
        "heartRateSinceLastTriggerPoint"
})
public class CrossingDto {

    private TriggerPoint triggerPoint;
    private GpsTrackDataPointDto gpsTrackDataPoint;
    private Long gpsTrackId;
    private double distanceToTriggerPointInMeter;
    @Schema(description = "Fractional position in the canonical point-index sequence. The point ID refers to a nearby sample.")
    private Double trackPointPosition;
    private Integer visitNumber;
    private SegmentTimingStatus timingStatusSinceLastTriggerPoint;
    @Schema(description = "Recorded path coverage is incomplete; endpoint elapsed time can still be available.")
    private boolean recordingGapSinceLastTriggerPoint;
    @Schema(nullable = true, description = "Elapsed seconds, including stops; unavailable when timing status is not AVAILABLE.")
    private Double timeInSecSinceLastTriggerPoint;
    @Schema(nullable = true)
    private Double distanceInMeterSinceLastTriggerPoint;
    @Schema(nullable = true)
    private Double avgSpeedSinceLastTriggerPoint;
    private SegmentNotes segmentNotesSinceLastTriggerPoint;
    private SegmentHeartRateSummary heartRateSinceLastTriggerPoint;

    public static CrossingDto from(Crossing crossing) {
        if (crossing == null) {
            return null;
        }
        CrossingDto dto = new CrossingDto();
        dto.triggerPoint = crossing.triggerPoint;
        dto.gpsTrackDataPoint = GpsTrackDataPointDto.from(crossing.gpsTrackDataPoint);
        dto.gpsTrackId = crossing.gpsTrackId;
        dto.distanceToTriggerPointInMeter = crossing.distanceToTriggerPointInMeter;
        dto.trackPointPosition = crossing.trackPointPosition;
        dto.visitNumber = crossing.visitNumber;
        dto.timingStatusSinceLastTriggerPoint = crossing.timingStatusSinceLastTriggerPoint;
        dto.recordingGapSinceLastTriggerPoint = crossing.recordingGapSinceLastTriggerPoint;
        dto.timeInSecSinceLastTriggerPoint = crossing.timeInSecSinceLastTriggerPoint;
        dto.distanceInMeterSinceLastTriggerPoint = crossing.distanceInMeterSinceLastTriggerPoint;
        dto.avgSpeedSinceLastTriggerPoint = crossing.avgSpeedSinceLastTriggerPoint;
        dto.segmentNotesSinceLastTriggerPoint = crossing.segmentNotesSinceLastTriggerPoint;
        return dto;
    }
}
