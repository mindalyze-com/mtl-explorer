package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"dataType", "representation", "recordCount", "earliestRecord", "latestRecord", "temporalKind",
        "earliestCivilDate", "latestCivilDate", "earliestCivilTime", "latestCivilTime", "lastSuccessAt", "lastError"})
public record GHTypeSummary(
        String dataType,
        String representation,
        long recordCount,
        Instant earliestRecord,
        Instant latestRecord,
        RecordRow.TemporalKind temporalKind,
        String earliestCivilDate,
        String latestCivilDate,
        String earliestCivilTime,
        String latestCivilTime,
        Instant lastSuccessAt,
        String lastError
) {
    public GHTypeSummary(String dataType, String representation, long recordCount, Instant earliestRecord,
                         Instant latestRecord, Instant lastSuccessAt, String lastError) {
        this(dataType, representation, recordCount, earliestRecord, latestRecord,
                RecordRow.TemporalKind.PHYSICAL, null, null, null, null, lastSuccessAt, lastError);
    }

}
