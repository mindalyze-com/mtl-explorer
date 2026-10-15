package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;

/**
 * Half-open sample interval for dates without a usable daily rollup.
 */
public record HeartRateTimeRange(Instant start, Instant end) {
}
