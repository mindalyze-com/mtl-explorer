package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXStoreService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.jobs.media.correlation.MediaCorrelationJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class MediaCorrelationDatabaseIntegrationTest {

    private static final String START_TIME = "2026-08-17 10:00:00";
    private static final String MEDIA_TIME = "2026-08-17 10:05:00";
    private static final String END_TIME = "2026-08-17 10:10:00";

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
                INSERT INTO gps_track_data(gps_track_id, precision_in_meter, track_type, track)
                VALUES (
                    ?, 0, 'RAW_OUTLIER_CLEANED',
                    ST_GeomFromText('LINESTRING(8.0 47.0, 8.1 47.1)', 4326)
                )
                RETURNING id
                """, Long.class, trackId));
    }

    private void insertCanonicalPoints(long trackDataId) {
        jdbcTemplate.update("""
                INSERT INTO gps_track_data_points(
                    gps_track_data_id, moving_window_in_sec, point_index, point_index_max,
                    point_timestamp, point_long_lat, distance_in_meter_since_start, duration_since_start
                )
                VALUES
                    (?, 0, 0, 1, ?::timestamp, ST_SetSRID(ST_MakePoint(8.0, 47.0), 4326), 0, 0),
                    (?, 0, 1, 1, ?::timestamp, ST_SetSRID(ST_MakePoint(8.1, 47.1), 4326), 1000, 600)
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
