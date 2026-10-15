package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({
        "timestamp",
        "elapsedSeconds",
        "averageBpm",
        "minimumBpm",
        "maximumBpm",
        "sampleCount"
})
public record TrackHeartRatePoint(
        Instant timestamp,
        double elapsedSeconds,
        double averageBpm,
        int minimumBpm,
        int maximumBpm,
        long sampleCount
) {
}
