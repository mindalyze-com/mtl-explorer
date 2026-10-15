package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.RecordRow.TemporalKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@JsonPropertyOrder({"id", "dataType", "representation", "recordTime", "temporalKind", "civilDate", "civilTime", "originalTime", "measurement", "valueSummary", "dataSourceId", "sourceName", "sourcePlatform", "routeState", "routeError", "associatedTrackId", "canonicalTrackId", "payload"})
public record GHRecordResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String dataType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String representation,
        Instant recordTime,
        TemporalKind temporalKind,
        String civilDate,
        String civilTime,
        String originalTime,
        GHRecordValue measurement,
        String valueSummary,
        Long dataSourceId,
        String sourceName,
        String sourcePlatform,
        String routeState,
        String routeError,
        Long associatedTrackId,
        Long canonicalTrackId,
        Map<String, Object> payload
) {
    public GHRecordResponse(String id, String dataType, String representation, Instant recordTime,
                            TemporalKind temporalKind, String civilDate, String civilTime, String originalTime,
                            String valueSummary, Long dataSourceId, String sourceName, String sourcePlatform,
                            String routeState, String routeError, Long associatedTrackId, Long canonicalTrackId,
                            Map<String, Object> payload) {
        this(id, dataType, representation, recordTime, temporalKind, civilDate, civilTime, originalTime, null,
                valueSummary, dataSourceId, sourceName, sourcePlatform, routeState, routeError, associatedTrackId,
                canonicalTrackId, payload);
    }

    public GHRecordResponse(String id, String dataType, String representation, Instant recordTime,
                            String originalTime, String valueSummary, Long dataSourceId, String sourceName,
                            String sourcePlatform, String routeState, Long associatedTrackId,
                            Long canonicalTrackId, Map<String, Object> payload) {
        this(id, dataType, representation, recordTime, TemporalKind.PHYSICAL, null, null, originalTime,
                valueSummary, dataSourceId, sourceName, sourcePlatform, routeState, null,
                associatedTrackId, canonicalTrackId, payload);
    }

}
