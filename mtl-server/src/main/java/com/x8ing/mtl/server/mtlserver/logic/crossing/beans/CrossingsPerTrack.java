package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAttemptSummary;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonPropertyOrder({
        "gpsTrack",
        "crossings"
})
public class CrossingsPerTrack {

    public GpsTrack gpsTrack;

    List<Crossing> crossings = new ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String sourceRevision;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String zoneRevision;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<SegmentAttemptSummary> attemptSummaries = List.of();

    public CrossingsPerTrack(GpsTrack gpsTrack, List<Crossing> crossings) {
        this.gpsTrack = gpsTrack;
        this.crossings = crossings;
    }

}
