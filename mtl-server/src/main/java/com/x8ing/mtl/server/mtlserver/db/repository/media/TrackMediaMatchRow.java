package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto;

import java.util.Date;

public record TrackMediaMatchRow(
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
        Double manualLat,
        Double manualLng,
        String manualNote,
        TrackPoint before,
        TrackPoint after
) {
    public record TrackPoint(
            Date timestamp,
            Integer pointIndex,
            Double lat,
            Double lng,
            Double altitude,
            Double distanceInMeterSinceStart,
            Double durationSinceStartSeconds
    ) {
    }
}
