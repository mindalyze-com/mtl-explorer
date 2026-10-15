package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXStoreService;
import com.x8ing.mtl.server.mtlserver.jobs.media.correlation.MediaCorrelationJob;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerKickoffService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false"
})
@Transactional
@Rollback
@Import(NoScheduledJobs.class)
class MediaCorrelationDatabaseIntegrationTest {

    private static final String START_TIME = "2026-08-17 10:00:00";
    private static final String MEDIA_TIME = "2026-08-17 10:00:30";
    private static final String END_TIME = "2026-08-17 10:01:00";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MediaCorrelationRepository correlationRepository;

    @Autowired
    private GpsTrackRepository gpsTrackRepository;

    @Autowired
    private GPXStoreService gpxStoreService;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @MockitoBean
    private MapServerKickoffService mapKickoff;

    @Test
    void unsupportedTimingRemovesRouteDerivedLocationsImmediatelyAndCanBeReenabled() {
        long trackId = insertTrack(insertIndexedFile("GPS", "synthetic-unsupported-time.gpx"));
        long dataId = insertCanonicalTrackData(trackId);
        insertCanonicalPoints(dataId);
        long mediaId = insertMedia(insertIndexedFile("MEDIA", "synthetic-untagged-photo.jpg"));
        jdbcTemplate.update("UPDATE media_file SET exif_gps_location=NULL WHERE id=?", mediaId);
        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        assertThat(origin(mediaId)).isEqualTo("TRACK_INTERPOLATED");

        jdbcTemplate.update("UPDATE gps_track SET timing_unreliable=true WHERE id=?", trackId);
        assertThat(count("SELECT count(*) FROM media_track_correlation WHERE media_id=?", mediaId)).isZero();
        assertThat(count("SELECT count(*) FROM media_resolved_location WHERE media_id=?", mediaId)).isZero();
        assertThat(count("SELECT count(*) FROM media_correlation_media_work WHERE media_id=?", mediaId)).isEqualTo(1);
        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        assertThat(count("SELECT count(*) FROM media_resolved_location WHERE media_id=?", mediaId)).isZero();
        var query = new TrackMediaQueryRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate));
        assertThat(query.findByTrackId(trackId, 0, 20, 0).items()).isEmpty();

        jdbcTemplate.update("UPDATE gps_track SET timing_unreliable=false WHERE id=?", trackId);
        assertThat(count("SELECT count(*) FROM media_correlation_track_work WHERE track_id=?", trackId)).isEqualTo(1);
        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        assertThat(origin(mediaId)).isEqualTo("TRACK_INTERPOLATED");
    }

    @Test
    void emptySimplifiedPathFallsBackToCanonicalGeometryCollection() {
        long trackId = insertTrack(insertIndexedFile("GPS", "synthetic-isolated-fix.gpx"));
        long dataId = insertCanonicalTrackData(trackId);
        insertCanonicalPoints(dataId);
        jdbcTemplate.update("""
                UPDATE gps_track_data SET observed_path=ST_GeomFromText(
                    'GEOMETRYCOLLECTION(POINT(8.05 47.05),LINESTRING(8.0 47.0,8.1 47.1))', 4326)
                WHERE id=?
                """, dataId);
        jdbcTemplate.update("""
                INSERT INTO gps_track_data(gps_track_id, precision_in_meter, track_type, track, observed_path)
                VALUES (?, 10, 'SIMPLIFIED_SHAPE', ST_GeomFromText('LINESTRING(8.0 47.0,8.1 47.1)',4326),
                    ST_GeomFromText('MULTILINESTRING EMPTY',4326))
                """, trackId);
        long mediaId = insertMedia(insertIndexedFile("MEDIA", "synthetic-isolated-fix-photo.jpg"));
        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT exif_route_distance_meters FROM media_track_correlation WHERE media_id=? AND track_id=?
                """, Double.class, mediaId, trackId)).isZero();
    }

    @Test
    void unsupportedTrackDoesNotRemoveAnEmbeddedPhotoLocation() {
        long trackId = insertTrack(insertIndexedFile("GPS", "synthetic-unsupported-exif-track.gpx"));
        long dataId = insertCanonicalTrackData(trackId);
        insertCanonicalPoints(dataId);
        long mediaId = insertMedia(insertIndexedFile("MEDIA", "synthetic-supported-exif-photo.jpg"));
        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        jdbcTemplate.update("UPDATE gps_track SET timing_unreliable=true WHERE id=?", trackId);
        assertThat(count("SELECT count(*) FROM media_track_correlation WHERE media_id=?", mediaId)).isZero();
        assertThat(origin(mediaId)).isEqualTo("EXIF_EMBEDDED");
        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        assertThat(origin(mediaId)).isEqualTo("EXIF_EMBEDDED");
    }

    @Test
    void deletingCorrelatedTrackRequeuesMediaAndPreservesExifFallback() {
        long trackFileId = insertIndexedFile("GPS", "synthetic-correlation-track.gpx");
        long mediaFileId = insertIndexedFile("MEDIA", "synthetic-correlation-photo.jpg");
        long trackId = insertTrack(trackFileId);
        long trackDataId = insertCanonicalTrackData(trackId);
        insertCanonicalPoints(trackDataId);
        long mediaId = insertMedia(mediaFileId);

        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);

        assertThat(count("SELECT COUNT(*) FROM media_track_correlation WHERE media_id = ? AND track_id = ?", mediaId, trackId))
                .isEqualTo(1);
        assertThat(origin(mediaId)).isEqualTo("EXIF_EMBEDDED");

        var track = gpsTrackRepository.findById(trackId).orElseThrow();
        gpxStoreService.deleteWithAllDependencies(track);
        gpsTrackRepository.flush();

        assertThat(count("SELECT COUNT(*) FROM gps_track WHERE id = ?", trackId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM media_track_correlation WHERE media_id = ?", mediaId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM media_correlation_media_work WHERE media_id = ?", mediaId)).isEqualTo(1);

        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);

        assertThat(count("SELECT COUNT(*) FROM media_correlation_media_work WHERE media_id = ?", mediaId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM media_track_correlation WHERE media_id = ?", mediaId)).isZero();
        assertThat(origin(mediaId)).isEqualTo("EXIF_EMBEDDED");
    }

    @Test
    void unknownIntervalDoesNotInventAPhotoLocation() {
        long trackId = insertTrack(insertIndexedFile("GPS", "synthetic-gap-track.gpx"));
        long dataId = insertCanonicalTrackData(trackId);
        insertCanonicalPoints(dataId);
        jdbcTemplate.update("UPDATE gps_track_data_points SET interval_status='UNKNOWN' WHERE gps_track_data_id=? AND point_index=1", dataId);
        long mediaId = insertMedia(insertIndexedFile("MEDIA", "synthetic-gap-photo.jpg"));

        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);

        assertThat(count("SELECT COUNT(*) FROM media_track_correlation WHERE media_id=?", mediaId)).isZero();
        assertThat(origin(mediaId)).isEqualTo("EXIF_EMBEDDED");
        var query = new TrackMediaQueryRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate));
        assertThat(query.findByTrackId(trackId, 0, 20, 0).items()).isEmpty();
    }

    @Test
    void selectsValidRouteAfterRejectingPreferredGapAndRestoresItAfterManualClear() {
        insertCandidate("synthetic-preferred-gap.gpx", "UNKNOWN");
        long validTrack = insertCandidate("synthetic-valid-route.gpx", "OBSERVED");
        long mediaId = insertCameraVideo("synthetic-camera-video.mov");

        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);

        assertThat(jdbcTemplate.queryForList("SELECT track_id FROM media_track_correlation WHERE media_id=? AND selected",
                Long.class, mediaId)).containsExactly(validTrack);
        assertThat(count("SELECT alternative_count FROM media_track_correlation WHERE media_id=?", mediaId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT ambiguous FROM media_track_correlation WHERE media_id=?",
                Boolean.class, mediaId)).isFalse();
        assertThat(origin(mediaId)).isEqualTo("TRACK_INTERPOLATED");
        String route = jdbcTemplate.queryForObject(
                "SELECT ST_AsEWKT(route_location) FROM media_track_correlation WHERE media_id=?", String.class, mediaId);
        var query = new TrackMediaQueryRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate));
        assertThat(query.findPersistedByTrackId(validTrack, 20, 0).items())
                .extracting(PersistedTrackMediaRow::id).containsExactly(mediaId);

        jdbcTemplate.update("""
                INSERT INTO media_manual_location(media_id, location, note)
                VALUES (?, ST_SetSRID(ST_MakePoint(9, 48), 4326), 'Synthetic assignment')
                """, mediaId);
        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        assertThat(origin(mediaId)).isEqualTo("USER_ASSIGNED");
        jdbcTemplate.update("DELETE FROM media_manual_location WHERE media_id=?", mediaId);
        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        assertThat(origin(mediaId)).isEqualTo("TRACK_INTERPOLATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_AsEWKT(location) FROM media_resolved_location WHERE media_id=?", String.class, mediaId))
                .isEqualTo(route);
    }

    @Test
    void ranksAndCountsOnlyUsableOverlappingRoutes() {
        insertCandidate("synthetic-overlap-gap.gpx", "UNKNOWN");
        long firstValid = insertCandidate("synthetic-overlap-first.gpx", "OBSERVED");
        insertCandidate("synthetic-overlap-second.gpx", "STOPPED");
        long mediaId = insertCameraVideo("synthetic-overlap-video.mov");

        correlationRepository.rebuildMedia(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);

        assertThat(jdbcTemplate.queryForList("SELECT track_id FROM media_track_correlation WHERE media_id=? AND selected",
                Long.class, mediaId)).containsExactly(firstValid);
        assertThat(jdbcTemplate.queryForList(
                "SELECT alternative_count FROM media_track_correlation WHERE media_id=?", Integer.class, mediaId))
                .containsExactly(2, 2);
        assertThat(count("SELECT count(*) FROM media_track_correlation WHERE media_id=? AND ambiguous", mediaId))
                .isEqualTo(2);
        assertThat(origin(mediaId)).isEqualTo("TRACK_INTERPOLATED");
    }

    @ParameterizedTest
    @CsvSource({"OBSERVED,900", "STOPPED,-900"})
    void cameraOffsetPreviewSkipsPreferredUnsupportedOverlap(String intervalStatus, int offsetSeconds) {
        long gapTrack = insertCandidate("synthetic-preview-preferred-gap.gpx", "UNKNOWN");
        long validTrack = insertCandidate("synthetic-preview-supported.gpx", intervalStatus);
        long mediaId = insertCameraVideo("synthetic-preview-camera.jpg");
        jdbcTemplate.update("""
                UPDATE media_file SET exif_date_image_taken=?::timestamp - make_interval(secs => ?) WHERE id=?
                """, MEDIA_TIME, offsetSeconds, mediaId);
        String originalCapture = jdbcTemplate.queryForObject(
                "SELECT exif_date_image_taken::text FROM media_file WHERE id=?", String.class, mediaId);
        var query = new TrackMediaQueryRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate));

        var preview = query.findByTrackId(validTrack, offsetSeconds, 20, 0);

        assertThat(preview.totalElements()).isEqualTo(1);
        assertThat(preview.items()).extracting(TrackMediaMatchRow::id).containsExactly(mediaId);
        var row = preview.items().getFirst();
        assertThat(row.appliedCameraOffsetSeconds()).isEqualTo(offsetSeconds);
        assertThat(row.before().pointIndex()).isZero();
        assertThat(row.after().pointIndex()).isEqualTo(1);
        assertThat(row.adjustedCapturedAt().getTime() - row.capturedAt().getTime()).isEqualTo(offsetSeconds * 1000L);
        assertThat(query.findByTrackId(gapTrack, offsetSeconds, 20, 0).items()).isEmpty();
        assertThat(query.findByTrackId(validTrack, offsetSeconds, 20, 20).totalElements()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT exif_date_image_taken::text FROM media_file WHERE id=?", String.class, mediaId)).isEqualTo(originalCapture);
        assertThat(count("SELECT count(*) FROM media_time_correction WHERE media_id=?", mediaId)).isZero();
        assertThat(count("SELECT count(*) FROM media_track_correlation WHERE media_id=?", mediaId)).isZero();
    }

    @Test
    void cameraOffsetPreviewRetainsManualPositionWithUnsupportedOverlap() {
        insertCandidate("synthetic-manual-preview-gap.gpx", "UNKNOWN");
        long validTrack = insertCandidate("synthetic-manual-preview-supported.gpx", "OBSERVED");
        long mediaId = insertCameraVideo("synthetic-manual-preview-camera.jpg");
        jdbcTemplate.update("UPDATE media_file SET exif_date_image_taken=?::timestamp - interval '15 minutes' WHERE id=?",
                MEDIA_TIME, mediaId);
        jdbcTemplate.update("""
                INSERT INTO media_manual_location(media_id, location, note)
                VALUES (?, ST_SetSRID(ST_MakePoint(9, 48), 4326), 'Synthetic preview assignment')
                """, mediaId);
        String originalAssignment = jdbcTemplate.queryForObject(
                "SELECT row_to_json(assignment)::text FROM media_manual_location assignment WHERE media_id=?",
                String.class, mediaId);
        var query = new TrackMediaQueryRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate));

        var preview = query.findByTrackId(validTrack, 900, 20, 0);

        assertThat(preview.items()).extracting(TrackMediaMatchRow::id).containsExactly(mediaId);
        var row = preview.items().getFirst();
        assertThat(row.manualLat()).isEqualTo(48);
        assertThat(row.manualLng()).isEqualTo(9);
        assertThat(row.manualNote()).isEqualTo("Synthetic preview assignment");
        assertThat(row.appliedCameraOffsetSeconds()).isEqualTo(900);
        assertThat(row.before().pointIndex()).isZero();
        assertThat(row.after().pointIndex()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT row_to_json(assignment)::text FROM media_manual_location assignment WHERE media_id=?",
                String.class, mediaId)).isEqualTo(originalAssignment);
        assertThat(count("SELECT count(*) FROM media_time_correction WHERE media_id=?", mediaId)).isZero();
    }

    @Test
    void cameraOffsetPreviewRetainsExactRecordedTimestampBesideUnknownInterval() {
        long trackId = insertCandidate("synthetic-preview-exact-gap-boundary.gpx", "UNKNOWN");
        long mediaId = insertCameraVideo("synthetic-preview-exact-camera.jpg");
        jdbcTemplate.update("UPDATE media_file SET exif_date_image_taken=?::timestamp - interval '15 minutes' WHERE id=?",
                START_TIME, mediaId);
        var query = new TrackMediaQueryRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate));

        var preview = query.findByTrackId(trackId, 900, 20, 0);

        assertThat(preview.items()).extracting(TrackMediaMatchRow::id).containsExactly(mediaId);
        assertThat(preview.items().getFirst().before().pointIndex()).isZero();
        assertThat(preview.items().getFirst().after().pointIndex()).isZero();
    }

    private long insertCandidate(String name, String intervalStatus) {
        long trackId = insertTrack(insertIndexedFile("GPS", name));
        long dataId = insertCanonicalTrackData(trackId);
        insertCanonicalPoints(dataId);
        jdbcTemplate.update("UPDATE gps_track_data_points SET interval_status=? WHERE gps_track_data_id=? AND point_index=1",
                intervalStatus, dataId);
        return trackId;
    }

    private long insertCameraVideo(String name) {
        long mediaId = insertMedia(insertIndexedFile("MEDIA", name));
        jdbcTemplate.update("""
                UPDATE media_file SET exif_gps_location=NULL, exif_gps_date=NULL,
                    exif_date_image_taken=?::timestamp WHERE id=?
                """, MEDIA_TIME, mediaId);
        return mediaId;
    }

    private long insertIndexedFile(String index, String name) {
        return requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO indexed_file("index", name, path, full_path, indexer_status)
                VALUES (?, ?, '/tmp/mtl-synthetic-review', '/tmp/mtl-synthetic-review/' || ?, 'COMPLETED_WITH_SUCCESS')
                RETURNING id
                """, Long.class, index, name, name));
    }

    private long insertTrack(long fileId) {
        return requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO gps_track(
                    file_id, track_name, start_date, end_date,
                    load_status, duplicate_status, track_source
                )
                VALUES (?, 'Synthetic correlation deletion', ?::timestamp, ?::timestamp, 'SUCCESS', 'UNIQUE', 'IMPORTED')
                RETURNING id
                """, Long.class, fileId, START_TIME, END_TIME));
    }

    private long insertCanonicalTrackData(long trackId) {
        return requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO gps_track_data(gps_track_id, precision_in_meter, track_type, track, observed_path)
                VALUES (
                    ?, 0, 'RAW_OUTLIER_CLEANED',
                    ST_GeomFromText('LINESTRING(8.0 47.0, 8.1 47.1)', 4326),
                    ST_GeomFromText('MULTILINESTRING((8.0 47.0, 8.1 47.1))', 4326)
                )
                RETURNING id
                """, Long.class, trackId));
    }

    private void insertCanonicalPoints(long trackDataId) {
        jdbcTemplate.update("""
                INSERT INTO gps_track_data_points(
                    gps_track_data_id, moving_window_in_sec, point_index, point_index_max,
                    point_timestamp, point_long_lat, distance_in_meter_since_start, duration_since_start, interval_status
                )
                VALUES
                    (?, 0, 0, 1, ?::timestamp, ST_SetSRID(ST_MakePoint(8.0, 47.0), 4326), 0, 0, 'START'),
                    (?, 0, 1, 1, ?::timestamp, ST_SetSRID(ST_MakePoint(8.1, 47.1), 4326), 1000, 60, 'OBSERVED')
                """, trackDataId, START_TIME, trackDataId, END_TIME);
    }

    private long insertMedia(long fileId) {
        return requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO media_file(file_id, exif_gps_location, exif_gps_date, camera_make, camera_model)
                VALUES (?, ST_SetSRID(ST_MakePoint(8.05, 47.05), 4326), ?::timestamp, 'Synthetic', 'Review')
                RETURNING id
                """, Long.class, fileId, MEDIA_TIME));
    }

    private long count(String sql, Object... args) {
        return requiredLong(jdbcTemplate.queryForObject(sql, Long.class, args));
    }

    private String origin(long mediaId) {
        return jdbcTemplate.queryForObject(
                "SELECT position_origin FROM media_resolved_location WHERE media_id = ?",
                String.class,
                mediaId);
    }

    private static long requiredLong(Long value) {
        if (value == null) {
            throw new IllegalStateException("Expected generated database id");
        }
        return value;
    }
}
