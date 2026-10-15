package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.time.LocalDate;

@JsonPropertyOrder({"id", "kind", "dataType", "representation", "recordTime", "temporalKind", "civilDate", "civilTime", "originalTime", "valueSummary", "dataSourceId", "sourceName", "sourcePlatform", "routeState", "routeError", "gpsTrackId", "rawJson", "endTime", "valueJson"})
public record RecordRow(
        long id,
        RecordKind kind,
        String dataType,
        String representation,
        Instant recordTime,
        TemporalKind temporalKind,
        LocalDate civilDate,
        String civilTime,
        String originalTime,
        String valueSummary,
        Long dataSourceId,
        String sourceName,
        String sourcePlatform,
        String routeState,
        String routeError,
        Long gpsTrackId,
        String rawJson,
        Instant endTime,
        String valueJson
) {
    public RecordRow(long id, RecordKind kind, String dataType, String representation, Instant recordTime,
                     TemporalKind temporalKind, LocalDate civilDate, String civilTime, String originalTime,
                     String valueSummary, Long dataSourceId, String sourceName, String sourcePlatform,
                     String routeState, String routeError, Long gpsTrackId, String rawJson) {
        this(id, kind, dataType, representation, recordTime, temporalKind, civilDate, civilTime, originalTime,
                valueSummary, dataSourceId, sourceName, sourcePlatform, routeState, routeError, gpsTrackId, rawJson, null, null);
    }

    public RecordRow(long id, RecordKind kind, String dataType, String representation,
                     Instant recordTime, String originalTime, String valueSummary, Long dataSourceId,
                     String sourceName, String sourcePlatform, String routeState, Long gpsTrackId, String rawJson) {
        this(id, kind, dataType, representation, recordTime, TemporalKind.PHYSICAL, null, null,
                originalTime, valueSummary, dataSourceId, sourceName, sourcePlatform, routeState, null, gpsTrackId, rawJson);
    }

    public enum TemporalKind {PHYSICAL, CIVIL, CALENDAR_DATE}

    public enum RecordKind {
        DATA_POINT,
        HEART_RATE_SAMPLE
    }

    public String recordId() {
        return (kind == RecordKind.DATA_POINT ? "point:" : "heart:") + id;
    }
}
