package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false"
})
@Transactional
@Rollback
class DemoPhotoTrackRetentionIntegrationTest {

    private static final String TRACK_START = "2299-01-01 10:00:00";
    private static final String PHOTO_TIME = "2299-01-01 10:05:00";
    private static final String TRACK_END = "2299-01-01 10:10:00";
    private static final String WRONG_TRACK_START = "2299-01-02 10:00:00";
    private static final String WRONG_TRACK_END = "2299-01-02 10:10:00";
    private static final String STANDALONE_PHOTO_TIME = "2299-01-03 10:05:00";
    private static final Date SUSPICIOUS_DATE_CUTOFF = Date.from(Instant.parse("1971-01-01T00:00:00Z"));
    private static final double MAX_PHOTO_DISTANCE_METERS = 50.0;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GpsTrackRepository repository;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @Test
    void restoresAndRetainsOnlySpatialAndTemporalPhotoMatches() {
        long matchedTrack = insertTrack("matched", "LINESTRING(8.0 47.0, 8.01 47.01)");
        long distantTrack = insertTrack("distant", "LINESTRING(9.0 48.0, 9.01 48.01)");
        long wrongTimeTrack = insertTrack(
                "wrong-time",
                "LINESTRING(8.0 47.0, 8.01 47.01)",
                WRONG_TRACK_START,
                WRONG_TRACK_END);
        insertMedia("matched-photo.jpg", PHOTO_TIME, 8.0, 47.0);
        insertMedia("standalone-photo.jpg", STANDALONE_PHOTO_TIME, 8.0, 47.0);

        assertThat(repository.reEnablePhotoMatchedExcludedTracks(
                SUSPICIOUS_DATE_CUTOFF,
                MAX_PHOTO_DISTANCE_METERS)).isEqualTo(1);
        assertThat(status(matchedTrack)).isEqualTo("NOT_CHECKED_YET");
        assertThat(status(distantTrack)).isEqualTo("EXCLUDED");
        assertThat(status(wrongTimeTrack)).isEqualTo("EXCLUDED");

        setStatus(matchedTrack, "UNIQUE");
        setStatus(distantTrack, "UNIQUE");
        setStatus(wrongTimeTrack, "UNIQUE");
        int targetCount = Math.toIntExact(repository.countGoodTracks() - 2);

        assertThat(repository.excludeGoodTracksExceedingOffset(
                targetCount,
                MAX_PHOTO_DISTANCE_METERS)).isEqualTo(2);
        assertThat(status(matchedTrack)).isEqualTo("UNIQUE");
        assertThat(status(distantTrack)).isEqualTo("EXCLUDED");
        assertThat(status(wrongTimeTrack)).isEqualTo("EXCLUDED");
    }

    private long insertTrack(String suffix, String lineString) {
        return insertTrack(suffix, lineString, TRACK_START, TRACK_END);
    }

    private long insertTrack(String suffix, String lineString, String start, String end) {
        long indexedFileId = insertIndexedFile("GPS", "synthetic-demo-" + suffix + ".gpx");
        long trackId = requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO gps_track(
                    file_id, track_name, start_date, end_date,
                    load_status, duplicate_status, track_source
                )
                VALUES (?, ?, ?::timestamp, ?::timestamp, 'SUCCESS', 'EXCLUDED', 'IMPORTED')
                RETURNING id
                """, Long.class, indexedFileId, "Synthetic demo " + suffix, start, end));
        jdbcTemplate.update("""
                INSERT INTO gps_track_data(gps_track_id, precision_in_meter, track_type, track)
                VALUES (?, 0, 'RAW_OUTLIER_CLEANED', ST_GeomFromText(?, 4326))
                """, trackId, lineString);
        return trackId;
    }

    private void insertMedia(String name, String capturedAt, double longitude, double latitude) {
        long indexedFileId = insertIndexedFile("MEDIA", name);
        jdbcTemplate.update("""
                INSERT INTO media_file(
                    file_id, exif_gps_location_long, exif_gps_location_lat,
                    exif_gps_location, exif_gps_date, exif_date_image_taken
                )
                VALUES (
                    ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326),
                    ?::timestamp, ?::timestamp
                )
                """,
                indexedFileId,
                longitude,
                latitude,
                longitude,
                latitude,
                capturedAt,
                capturedAt);
    }

    private long insertIndexedFile(String index, String name) {
        return requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO indexed_file("index", name, path, full_path, indexer_status)
                VALUES (?, ?, '/tmp/mtl-synthetic-demo', '/tmp/mtl-synthetic-demo/' || ?,
                        'COMPLETED_WITH_SUCCESS')
                RETURNING id
                """, Long.class, index, name, name));
    }

    private String status(long trackId) {
        return jdbcTemplate.queryForObject(
                "SELECT duplicate_status FROM gps_track WHERE id = ?",
                String.class,
                trackId);
    }

    private void setStatus(long trackId, String status) {
        jdbcTemplate.update(
                "UPDATE gps_track SET duplicate_status = ? WHERE id = ?",
                status,
                trackId);
    }

    private static long requiredLong(Long value) {
        if (value == null) throw new IllegalStateException("Expected generated database id");
        return value;
    }
}
