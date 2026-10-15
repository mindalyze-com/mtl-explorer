package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;

public record ExercisePoint(
        long id,
        String resourceName,
        Instant startTime,
        Instant endTime,
        String fingerprint,
        String rawJson,
        String tcxRelativePath,
        RouteState routeState,
        Long indexedFileId,
        Long gpsTrackId
) {
    public enum RouteState {
        NOT_APPLICABLE,
        NO_LOCATION_SCOPE,
        NO_ROUTE,
        PENDING,
        IMPORTED,
        FAILED
    }
}
