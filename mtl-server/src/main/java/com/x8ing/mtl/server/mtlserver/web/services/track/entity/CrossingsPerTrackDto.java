package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingsPerTrack;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonPropertyOrder({
        "gpsTrack",
        "crossings"
})
public class CrossingsPerTrackDto {

    private GpsTrack gpsTrack;
    private List<CrossingDto> crossings = new ArrayList<>();

    public static CrossingsPerTrackDto from(CrossingsPerTrack crossingsPerTrack) {
        if (crossingsPerTrack == null) {
            return null;
        }
        CrossingsPerTrackDto dto = new CrossingsPerTrackDto();
        dto.gpsTrack = crossingsPerTrack.getGpsTrack();
        if (crossingsPerTrack.getCrossings() != null) {
            dto.crossings = crossingsPerTrack.getCrossings().stream()
                    .map(CrossingDto::from)
                    .toList();
        }
        return dto;
    }
}
