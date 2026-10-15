package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({
        "trackId",
        "available",
        "source",
        "trackStartTimestamp",
        "trackEndTimestamp",
        "heartRate"
})
public record TrackHealthResponse(
        long trackId,
        boolean available,
        Source source,
        Instant trackStartTimestamp,
        Instant trackEndTimestamp,
        TrackHeartRateSummary heartRate
) {
    public enum Source {
        GOOGLE_HEALTH
    }
}
