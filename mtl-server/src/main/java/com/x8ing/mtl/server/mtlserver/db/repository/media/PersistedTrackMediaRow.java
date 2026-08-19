package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaPositionOrigin;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto;

import java.util.Date;

public record PersistedTrackMediaRow(
        Long id,
        String fileName,
        String cameraMake,
        String cameraModel,
        Date capturedAt,
        Date adjustedCapturedAt,
        int appliedCameraOffsetSeconds,
        TrackMediaDto.TIME_SOURCE timeSource,
        Double exifLat,
        Double exifLng,
        Double routeLat,
        Double routeLng,
        Double resolvedLat,
        Double resolvedLng,
        Double manualLat,
        Double manualLng,
        String manualNote,
        MediaPositionOrigin positionOrigin,
        Double distanceInMeterSinceStart,
        Double durationSinceStartSeconds,
        Integer trackPointIndex,
        Double trackPointTimeDeltaSeconds,
        boolean ambiguousMatch,
        int alternativeMatchCount
) {
}
