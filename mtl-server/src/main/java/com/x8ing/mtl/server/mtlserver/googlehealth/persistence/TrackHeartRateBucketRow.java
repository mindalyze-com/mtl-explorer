package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;

public record TrackHeartRateBucketRow(
        Instant timestamp,
        double averageBpm,
        int minimumBpm,
        int maximumBpm,
        long sampleCount
) {
}
