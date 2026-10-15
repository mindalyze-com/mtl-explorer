package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonPropertyOrder({
        "sampleCount",
        "averageBpm",
        "movingAverageBpm",
        "movingSampleCount",
        "minimumBpm",
        "maximumBpm",
        "firstSampleTimestamp",
        "lastSampleTimestamp",
        "points"
})
public record TrackHeartRateSummary(
        long sampleCount,
        double averageBpm,
        @Schema(description = "Mean of recorded samples during GPS movement (at least 0.5 km/h), excluding detected stops and GPS intervals longer than 60 seconds. Absent when no samples qualify.")
        Double movingAverageBpm,
        long movingSampleCount,
        @Schema(description = "Lowest individual recorded sample within the track time range, including stops.")
        int minimumBpm,
        @Schema(description = "Highest individual recorded sample within the track time range, including stops.")
        int maximumBpm,
        Instant firstSampleTimestamp,
        Instant lastSampleTimestamp,
        List<TrackHeartRatePoint> points
) {
}
