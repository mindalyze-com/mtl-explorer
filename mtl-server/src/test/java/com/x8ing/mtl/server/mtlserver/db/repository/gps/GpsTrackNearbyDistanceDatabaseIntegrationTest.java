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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H"
})
@Transactional
@Rollback
class GpsTrackNearbyDistanceDatabaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GpsTrackRepository repository;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @Test
    void returnsNearbyTracksNearestFirstWithDistanceInMeters() {
        long nearTrackId = insertTrack("synthetic-nearby-near.gpx", "LINESTRING(8.0 47.0, 8.01 47.01)");
        long farTrackId = insertTrack("synthetic-nearby-far.gpx", "LINESTRING(8.1 47.1, 8.11 47.11)");

        var nearby = repository.getTracksWithDistanceToPoint(
                8.0,
                47.0,
                20_000,
                new Long[]{farTrackId, nearTrackId});

        assertThat(nearby).extracting(row -> row.getTrackId()).containsExactly(nearTrackId, farTrackId);
        assertThat(nearby.getFirst().getDistanceMeters()).isBetween(0.0, 0.01);
        assertThat(nearby.getLast().getDistanceMeters()).isGreaterThan(10_000);

        var nearbyTrackIds = repository.getTracksWithinDistanceToPoint(
                8.0,
                47.0,
                20_000,
                new Long[]{farTrackId, nearTrackId});

        assertThat(nearbyTrackIds).containsExactly(nearTrackId, farTrackId);
    }

    private long insertTrack(String fileName, String routeWkt) {
        long fileId = requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO indexed_file("index", name, path, full_path, indexer_status)
                VALUES ('GPS', ?, '/tmp/mtl-synthetic-nearby', '/tmp/mtl-synthetic-nearby/' || ?, 'COMPLETED_WITH_SUCCESS')
                RETURNING id
                """, Long.class, fileName, fileName));
        long trackId = requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO gps_track(
                    file_id, track_name, start_date, end_date,
                    load_status, duplicate_status, track_source
                )
                VALUES (?, ?, '2199-01-01 10:00:00', '2199-01-01 11:00:00', 'SUCCESS', 'UNIQUE', 'IMPORTED')
                RETURNING id
                """, Long.class, fileId, fileName));
        jdbcTemplate.update("""
                INSERT INTO gps_track_data(gps_track_id, precision_in_meter, track_type, track)
                VALUES (?, 10, 'SIMPLIFIED_SHAPE', ST_GeomFromText(?, 4326))
                """, trackId, routeWkt);
        return trackId;
    }

    private static long requiredLong(Long value) {
        if (value == null) throw new IllegalStateException("Expected generated id");
        return value;
    }
}
