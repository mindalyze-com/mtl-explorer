package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;

public record HeartRateBucket(
        Instant time,
        double average,
        int minimum,
        int maximum
) {
}
