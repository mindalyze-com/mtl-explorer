package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaMatchRow;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaPageResult;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaQueryRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.PersistedTrackMediaRow;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaPositionOrigin;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrackMediaServiceTest {

    private static final long TRACK_ID = 42L;

    private final TrackMediaQueryRepository queryRepository = mock(TrackMediaQueryRepository.class);
    private final GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
    private final TrackMediaService service = new TrackMediaService(queryRepository, gpsTrackRepository);

    @Test
    void interpolatesMissingGpsPositionAndKeepsItExplicitlyEstimated() {
        Date capturedAt = date("2026-08-17T10:00:05Z");
        TrackMediaMatchRow row = new TrackMediaMatchRow(
                7L,
                "clip.MOV",
                "Camera",
                "Model",
                date("2026-08-17T09:00:05Z"),
                capturedAt,
                3600,
                TrackMediaDto.TIME_SOURCE.EXIF_DATE_TAKEN,
                null,
                null,
                null,
                null,
                null,
                point("2026-08-17T10:00:00Z", 10, 47.0, 8.0, 0.0, 0.0),
                point("2026-08-17T10:00:10Z", 11, 48.0, 10.0, 100.0, 10.0));
        when(gpsTrackRepository.existsById(TRACK_ID)).thenReturn(true);
        when(queryRepository.findByTrackId(TRACK_ID, 3600, 100, 0))
                .thenReturn(new TrackMediaPageResult<>(List.of(row), 1));

        TrackMediaDto dto = service.findByTrackId(TRACK_ID, 3600, 0, 100).items().getFirst();

        assertThat(dto.getOriginalLat()).isNull();
        assertThat(dto.getOriginalLng()).isNull();
        assertThat(dto.getRouteLat()).isEqualTo(47.5);
        assertThat(dto.getRouteLng()).isEqualTo(9.0);
        assertThat(dto.getDistanceInMeterSinceStart()).isEqualTo(50.0);
        assertThat(dto.getDurationSinceStartSeconds()).isEqualTo(5.0);
        assertThat(dto.getTrackPointIndex()).isEqualTo(10);
        assertThat(dto.getTrackPointTimeDeltaSeconds()).isEqualTo(5.0);
        assertThat(dto.getPositionOrigin()).isEqualTo(TrackMediaDto.POSITION_ORIGIN.TRACK_INTERPOLATED);
        assertThat(dto.getEstimatedPosition()).isTrue();
        assertThat(dto.getPreview()).isTrue();
        assertThat(dto.getMediaKind()).isEqualTo(TrackMediaDto.MEDIA_KIND.VIDEO);
        assertThat(dto.getAppliedCameraOffsetSeconds()).isEqualTo(3600);
    }

    @Test
    void keepsUserAssignedPositionDuringCameraOffsetPreview() {
        TrackMediaMatchRow row = new TrackMediaMatchRow(
                9L,
                "manual-preview.jpg",
                null,
                null,
                date("2026-08-17T09:00:05Z"),
                date("2026-08-17T10:00:05Z"),
                3600,
                TrackMediaDto.TIME_SOURCE.EXIF_DATE_TAKEN,
                null,
                null,
                46.9,
                7.9,
                "Trailhead",
                point("2026-08-17T10:00:00Z", 10, 47.0, 8.0, 0.0, 0.0),
                point("2026-08-17T10:00:10Z", 11, 48.0, 10.0, 100.0, 10.0));
        when(gpsTrackRepository.existsById(TRACK_ID)).thenReturn(true);
        when(queryRepository.findByTrackId(TRACK_ID, 3600, 100, 0))
                .thenReturn(new TrackMediaPageResult<>(List.of(row), 1));

        TrackMediaDto dto = service.findByTrackId(TRACK_ID, 3600, 0, 100).items().getFirst();

        assertThat(dto.getResolvedLat()).isEqualTo(46.9);
        assertThat(dto.getResolvedLng()).isEqualTo(7.9);
        assertThat(dto.getManualLat()).isEqualTo(46.9);
        assertThat(dto.getManualLng()).isEqualTo(7.9);
        assertThat(dto.getManualNote()).isEqualTo("Trailhead");
        assertThat(dto.getRouteLat()).isEqualTo(47.5);
        assertThat(dto.getPositionOrigin()).isEqualTo(TrackMediaDto.POSITION_ORIGIN.USER_ASSIGNED);
        assertThat(dto.getEstimatedPosition()).isFalse();
        assertThat(dto.getPreview()).isTrue();
    }

    @Test
    void exposesExifAndRouteCoordinatesSeparatelyForGpsMedia() {
        Date capturedAt = date("2026-08-17T10:00:00Z");
        PersistedTrackMediaRow row = new PersistedTrackMediaRow(
                8L,
                "photo.jpg",
                null,
                null,
                capturedAt,
                capturedAt,
                0,
                TrackMediaDto.TIME_SOURCE.EXIF_GPS,
                47.4,
                8.5,
                47.3,
                8.4,
                47.4,
                8.5,
                null,
                null,
                null,
                MediaPositionOrigin.EXIF_EMBEDDED,
                20.0,
                20.0,
                20,
                0.0,
                false,
                1);
        when(gpsTrackRepository.existsById(TRACK_ID)).thenReturn(true);
        when(queryRepository.findPersistedByTrackId(TRACK_ID, 100, 0))
                .thenReturn(new TrackMediaPageResult<>(List.of(row), 1));

        TrackMediaDto dto = service.findByTrackId(TRACK_ID, 0, 0, 100).items().getFirst();

        assertThat(dto.getOriginalLat()).isEqualTo(47.4);
        assertThat(dto.getOriginalLng()).isEqualTo(8.5);
        assertThat(dto.getRouteLat()).isEqualTo(47.3);
        assertThat(dto.getRouteLng()).isEqualTo(8.4);
        assertThat(dto.getPositionOrigin()).isEqualTo(TrackMediaDto.POSITION_ORIGIN.EXIF_EMBEDDED);
        assertThat(dto.getEstimatedPosition()).isFalse();
        assertThat(dto.getPreview()).isFalse();
        assertThat(dto.getTimeSource()).isEqualTo(TrackMediaDto.TIME_SOURCE.EXIF_GPS);
        assertThat(dto.getAppliedCameraOffsetSeconds()).isZero();
    }

    @Test
    void exposesManualResolvedPositionWithoutOverwritingExifOrRouteEvidence() {
        PersistedTrackMediaRow row = new PersistedTrackMediaRow(
                9L,
                "manual.jpg",
                null,
                null,
                date("2026-08-17T10:00:00Z"),
                date("2026-08-17T10:00:00Z"),
                0,
                TrackMediaDto.TIME_SOURCE.EXIF_GPS,
                47.4,
                8.5,
                47.3,
                8.4,
                46.9,
                7.9,
                46.9,
                7.9,
                "Trailhead",
                MediaPositionOrigin.USER_ASSIGNED,
                20.0,
                20.0,
                20,
                0.0,
                true,
                2);
        when(gpsTrackRepository.existsById(TRACK_ID)).thenReturn(true);
        when(queryRepository.findPersistedByTrackId(TRACK_ID, 100, 0))
                .thenReturn(new TrackMediaPageResult<>(List.of(row), 1));

        TrackMediaDto dto = service.findByTrackId(TRACK_ID, 0, 0, 100).items().getFirst();

        assertThat(dto.getOriginalLat()).isEqualTo(47.4);
        assertThat(dto.getRouteLat()).isEqualTo(47.3);
        assertThat(dto.getResolvedLat()).isEqualTo(46.9);
        assertThat(dto.getManualLat()).isEqualTo(46.9);
        assertThat(dto.getManualNote()).isEqualTo("Trailhead");
        assertThat(dto.getPositionOrigin()).isEqualTo(TrackMediaDto.POSITION_ORIGIN.USER_ASSIGNED);
        assertThat(dto.getEstimatedPosition()).isFalse();
        assertThat(dto.getAmbiguousMatch()).isTrue();
        assertThat(dto.getAlternativeMatchCount()).isEqualTo(2);
    }

    @Test
    void rejectsCameraOffsetOutsideOneDay() {
        assertThatThrownBy(() -> service.findByTrackId(
                TRACK_ID, TrackMediaService.MAX_CAMERA_OFFSET_SECONDS + 1, 0, 100))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(queryRepository, gpsTrackRepository);
    }

    @Test
    void returnsNotFoundForUnknownTrack() {
        when(gpsTrackRepository.existsById(TRACK_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.findByTrackId(TRACK_ID, 0, 0, 100))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(queryRepository);
    }

    @Test
    void returnsPageMetadataAndUsesTheRequestedOffset() {
        when(gpsTrackRepository.existsById(TRACK_ID)).thenReturn(true);
        when(queryRepository.findPersistedByTrackId(TRACK_ID, 25, 50))
                .thenReturn(new TrackMediaPageResult<>(List.of(), 61));

        var result = service.findByTrackId(TRACK_ID, 0, 2, 25);

        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(25);
        assertThat(result.totalItems()).isEqualTo(61);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidPageParameters() {
        assertThatThrownBy(() -> service.findByTrackId(TRACK_ID, 0, -1, 100))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.findByTrackId(TRACK_ID, 0, 0, TrackMediaService.MAX_PAGE_SIZE + 1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(queryRepository, gpsTrackRepository);
    }

    private static TrackMediaMatchRow.TrackPoint point(
            String timestamp,
            int index,
            double lat,
            double lng,
            double distance,
            double duration
    ) {
        return new TrackMediaMatchRow.TrackPoint(
                date(timestamp), index, lat, lng, 500.0, distance, duration);
    }

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }
}
