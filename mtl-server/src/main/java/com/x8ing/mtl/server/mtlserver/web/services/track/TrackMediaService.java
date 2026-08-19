package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.PersistedTrackMediaRow;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaMatchRow;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaPageResult;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaQueryRepository;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaPageDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrackMediaService {

    private static final double MILLIS_PER_SECOND = 1_000.0;
    public static final int MAX_CAMERA_OFFSET_SECONDS = 24 * 60 * 60;
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 200;

    private final TrackMediaQueryRepository queryRepository;
    private final GpsTrackRepository gpsTrackRepository;

    public TrackMediaService(TrackMediaQueryRepository queryRepository, GpsTrackRepository gpsTrackRepository) {
        this.queryRepository = queryRepository;
        this.gpsTrackRepository = gpsTrackRepository;
    }

    public TrackMediaPageDto findByTrackId(
            long trackId,
            int cameraOffsetSeconds,
            int page,
            int pageSize) {
        if (Math.abs((long) cameraOffsetSeconds) > MAX_CAMERA_OFFSET_SECONDS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cameraOffsetSeconds must be between -86400 and 86400");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!gpsTrackRepository.existsById(trackId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found");
        }
        long offset = Math.multiplyExact((long) page, pageSize);
        TrackMediaPageResult<TrackMediaDto> result = cameraOffsetSeconds == 0
                ? mapPage(queryRepository.findPersistedByTrackId(trackId, pageSize, offset), TrackMediaService::toDto)
                : mapPage(queryRepository.findByTrackId(trackId, cameraOffsetSeconds, pageSize, offset), TrackMediaService::toPreviewDto);
        long totalPages = result.totalElements() == 0
                ? 0
                : ((result.totalElements() - 1) / pageSize) + 1;
        return new TrackMediaPageDto(result.items(), page, pageSize, result.totalElements(), totalPages);
    }

    private static <T> TrackMediaPageResult<TrackMediaDto> mapPage(
            TrackMediaPageResult<T> page,
            java.util.function.Function<T, TrackMediaDto> mapper) {
        return new TrackMediaPageResult<>(page.items().stream().map(mapper).toList(), page.totalElements());
    }

    static TrackMediaDto toPreviewDto(TrackMediaMatchRow row) {
        TrackMediaMatchRow.TrackPoint before = row.before();
        TrackMediaMatchRow.TrackPoint after = row.after();
        TrackMediaMatchRow.TrackPoint nearest = nearest(row.adjustedCapturedAt(), before, after);
        double fraction = interpolationFraction(row.adjustedCapturedAt(), before, after);

        Double routeLat = interpolate(before == null ? null : before.lat(), after == null ? null : after.lat(), fraction, nearest == null ? null : nearest.lat());
        Double routeLng = interpolate(before == null ? null : before.lng(), after == null ? null : after.lng(), fraction, nearest == null ? null : nearest.lng());
        Double distance = interpolate(before == null ? null : before.distanceInMeterSinceStart(), after == null ? null : after.distanceInMeterSinceStart(), fraction, nearest == null ? null : nearest.distanceInMeterSinceStart());
        Double duration = interpolate(before == null ? null : before.durationSinceStartSeconds(), after == null ? null : after.durationSinceStartSeconds(), fraction, nearest == null ? null : nearest.durationSinceStartSeconds());

        boolean hasExifLocation = row.exifLat() != null && row.exifLng() != null;
        boolean hasManualLocation = row.manualLat() != null && row.manualLng() != null;
        TrackMediaDto dto = new TrackMediaDto();
        dto.setId(row.id());
        dto.setFileName(row.fileName());
        dto.setMediaKind(MediaKindResolver.resolve(row.fileName()));
        dto.setCapturedAt(row.capturedAt());
        dto.setAdjustedCapturedAt(row.adjustedCapturedAt());
        dto.setAppliedCameraOffsetSeconds(row.appliedCameraOffsetSeconds());
        dto.setTimeSource(row.timeSource());
        dto.setCameraMake(row.cameraMake());
        dto.setCameraModel(row.cameraModel());
        dto.setOriginalLat(hasExifLocation ? row.exifLat() : null);
        dto.setOriginalLng(hasExifLocation ? row.exifLng() : null);
        dto.setRouteLat(routeLat);
        dto.setRouteLng(routeLng);
        dto.setResolvedLat(hasManualLocation ? row.manualLat() : hasExifLocation ? row.exifLat() : routeLat);
        dto.setResolvedLng(hasManualLocation ? row.manualLng() : hasExifLocation ? row.exifLng() : routeLng);
        dto.setManualLat(hasManualLocation ? row.manualLat() : null);
        dto.setManualLng(hasManualLocation ? row.manualLng() : null);
        dto.setManualNote(hasManualLocation ? row.manualNote() : null);
        dto.setDistanceInMeterSinceStart(distance);
        dto.setDurationSinceStartSeconds(duration);
        dto.setTrackPointIndex(nearest == null ? null : nearest.pointIndex());
        dto.setTrackPointTimeDeltaSeconds(nearest == null ? null
                : Math.abs(row.adjustedCapturedAt().getTime() - nearest.timestamp().getTime()) / MILLIS_PER_SECOND);
        dto.setPositionOrigin(hasManualLocation
                ? TrackMediaDto.POSITION_ORIGIN.USER_ASSIGNED
                : hasExifLocation
                    ? TrackMediaDto.POSITION_ORIGIN.EXIF_EMBEDDED
                    : TrackMediaDto.POSITION_ORIGIN.TRACK_INTERPOLATED);
        dto.setEstimatedPosition(!hasManualLocation && !hasExifLocation);
        dto.setPreview(true);
        return dto;
    }

    static TrackMediaDto toDto(PersistedTrackMediaRow row) {
        TrackMediaDto dto = new TrackMediaDto();
        dto.setId(row.id());
        dto.setFileName(row.fileName());
        dto.setMediaKind(MediaKindResolver.resolve(row.fileName()));
        dto.setCapturedAt(row.capturedAt());
        dto.setAdjustedCapturedAt(row.adjustedCapturedAt());
        dto.setAppliedCameraOffsetSeconds(row.appliedCameraOffsetSeconds());
        dto.setTimeSource(row.timeSource());
        dto.setCameraMake(row.cameraMake());
        dto.setCameraModel(row.cameraModel());
        dto.setOriginalLat(row.exifLat());
        dto.setOriginalLng(row.exifLng());
        dto.setRouteLat(row.routeLat());
        dto.setRouteLng(row.routeLng());
        dto.setResolvedLat(row.resolvedLat());
        dto.setResolvedLng(row.resolvedLng());
        dto.setManualLat(row.manualLat());
        dto.setManualLng(row.manualLng());
        dto.setManualNote(row.manualNote());
        dto.setDistanceInMeterSinceStart(row.distanceInMeterSinceStart());
        dto.setDurationSinceStartSeconds(row.durationSinceStartSeconds());
        dto.setTrackPointIndex(row.trackPointIndex());
        dto.setTrackPointTimeDeltaSeconds(row.trackPointTimeDeltaSeconds());
        if (row.positionOrigin() != null) {
            dto.setPositionOrigin(TrackMediaDto.POSITION_ORIGIN.valueOf(row.positionOrigin().name()));
        }
        dto.setEstimatedPosition(row.positionOrigin() == com.x8ing.mtl.server.mtlserver.db.entity.media.MediaPositionOrigin.TRACK_INTERPOLATED);
        dto.setAmbiguousMatch(row.ambiguousMatch());
        dto.setAlternativeMatchCount(row.alternativeMatchCount());
        dto.setPreview(false);
        return dto;
    }

    private static TrackMediaMatchRow.TrackPoint nearest(
            java.util.Date captureTime,
            TrackMediaMatchRow.TrackPoint before,
            TrackMediaMatchRow.TrackPoint after
    ) {
        if (before == null) return after;
        if (after == null) return before;
        long beforeDelta = Math.abs(captureTime.getTime() - before.timestamp().getTime());
        long afterDelta = Math.abs(after.timestamp().getTime() - captureTime.getTime());
        return beforeDelta <= afterDelta ? before : after;
    }

    private static double interpolationFraction(
            java.util.Date captureTime,
            TrackMediaMatchRow.TrackPoint before,
            TrackMediaMatchRow.TrackPoint after
    ) {
        if (before == null || after == null) return 0.0;
        long durationMillis = after.timestamp().getTime() - before.timestamp().getTime();
        if (durationMillis <= 0) return 0.0;
        double fraction = (double) (captureTime.getTime() - before.timestamp().getTime()) / durationMillis;
        return Math.max(0.0, Math.min(1.0, fraction));
    }

    private static Double interpolate(Double before, Double after, double fraction, Double fallback) {
        if (before != null && after != null) {
            return before + (after - before) * fraction;
        }
        if (before != null) return before;
        if (after != null) return after;
        return fallback;
    }

}
