package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({
        "recordCount",
        "earliestRecord",
        "latestRecord"
})
public record GHMirrorSummary(
        long recordCount,
        Instant earliestRecord,
        Instant latestRecord
) {
    public static GHMirrorSummary empty() {
        return new GHMirrorSummary(0L, null, null);
    }
}
