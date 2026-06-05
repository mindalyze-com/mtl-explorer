package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsResponse;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingsPerTrack;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.Segment;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.TriggerPoint;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonPropertyOrder({
        "crossings",
        "segmentsStats",
        "triggerPoints",
        "tracksPerZone"
})
public class CrossingPointsResponseDto {

    private Map<Long, CrossingsPerTrackDto> crossings = new LinkedHashMap<>();
    private List<Segment> segmentsStats = new ArrayList<>();
    private List<TriggerPoint> triggerPoints = new ArrayList<>();
    private Map<String, Integer> tracksPerZone = new LinkedHashMap<>();

    public static CrossingPointsResponseDto from(CrossingPointsResponse response) {
        CrossingPointsResponseDto dto = new CrossingPointsResponseDto();
        if (response == null) {
            return dto;
        }
        if (response.crossings != null) {
            for (Map.Entry<Long, CrossingsPerTrack> entry : response.crossings.entrySet()) {
                dto.crossings.put(entry.getKey(), CrossingsPerTrackDto.from(entry.getValue()));
            }
        }
        if (response.segmentsStats != null) {
            dto.segmentsStats = response.segmentsStats;
        }
        if (response.triggerPoints != null) {
            dto.triggerPoints = response.triggerPoints;
        }
        if (response.tracksPerZone != null) {
            dto.tracksPerZone = response.tracksPerZone;
        }
        return dto;
    }
}
