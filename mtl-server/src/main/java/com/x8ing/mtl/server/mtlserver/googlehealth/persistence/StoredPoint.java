package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StoredPoint(
        String dataType,
        Representation representation,
        String identityKey,
        String fingerprint,
        String googleResourceName,
        Long dataSourceId,
        Instant physicalStartTime,
        Instant physicalEndTime,
        String originalStartTime,
        String originalEndTime,
        LocalDateTime civilStartTime,
        LocalDateTime civilEndTime,
        String utcOffsetStart,
        String utcOffsetEnd,
        LocalDate civilDate,
        String dataSourceFamily,
        String rawJson
) {
    public StoredPoint withDataSourceId(Long sourceId) {
        return new StoredPoint(dataType, representation, identityKey, fingerprint, googleResourceName, sourceId,
                physicalStartTime, physicalEndTime, originalStartTime, originalEndTime, civilStartTime, civilEndTime,
                utcOffsetStart, utcOffsetEnd, civilDate, dataSourceFamily, rawJson);
    }

    public enum Representation {
        RAW,
        RECONCILED_INTERVAL,
        DAILY_ROLLUP
    }
}
