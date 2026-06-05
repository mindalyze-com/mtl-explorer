package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.Crossing;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.TriggerPoint;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "triggerPoint",
        "gpsTrackDataPoint",
        "gpsTrackId",
        "distanceToTriggerPointInMeter",
        "timeInSecSinceLastTriggerPoint",
        "distanceInMeterSinceLastTriggerPoint",
        "avgSpeedSinceLastTriggerPoint",
        "segmentNotesSinceLastTriggerPoint"
})
public class CrossingDto {

    private TriggerPoint triggerPoint;
    private GpsTrackDataPointDto gpsTrackDataPoint;
    private Long gpsTrackId;
    private double distanceToTriggerPointInMeter;
    private double timeInSecSinceLastTriggerPoint;
    private double distanceInMeterSinceLastTriggerPoint;
    private double avgSpeedSinceLastTriggerPoint;
    private SegmentNotes segmentNotesSinceLastTriggerPoint;

    public static CrossingDto from(Crossing crossing) {
        if (crossing == null) {
            return null;
        }
        CrossingDto dto = new CrossingDto();
        dto.triggerPoint = crossing.triggerPoint;
        dto.gpsTrackDataPoint = GpsTrackDataPointDto.from(crossing.gpsTrackDataPoint);
        dto.gpsTrackId = crossing.gpsTrackId;
        dto.distanceToTriggerPointInMeter = crossing.distanceToTriggerPointInMeter;
        dto.timeInSecSinceLastTriggerPoint = crossing.timeInSecSinceLastTriggerPoint;
        dto.distanceInMeterSinceLastTriggerPoint = crossing.distanceInMeterSinceLastTriggerPoint;
        dto.avgSpeedSinceLastTriggerPoint = crossing.avgSpeedSinceLastTriggerPoint;
        dto.segmentNotesSinceLastTriggerPoint = crossing.segmentNotesSinceLastTriggerPoint;
        return dto;
    }
}
