package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;

public record MetricRecordRow(
        String dataType,
        String representation,
        Instant recordTime,
        Instant endTime,
        String rawJson
) {
}
