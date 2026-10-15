package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"
})
class TrackHealthRepositoryDatabaseIntegrationTest {

    private static final Instant START = Instant.parse("2099-08-29T08:00:00Z");
    private static final Instant END = START.plusSeconds(120);

    @Autowired
    private TrackHealthRepository trackHealthRepository;

    @Autowired
    private MirrorRepository mirrorRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @Test
    @Transactional
    void summarizesAndBucketsSamplesInsideTheClosedTrackInterval() {
        mirrorRepository.disconnect();
        long sourceId = mirrorRepository.upsertDataSource(
                hex('a'), "FITBIT", "AUTOMATIC", "Fitbit", "Test watch", "{\"platform\":\"FITBIT\"}");
        List<HeartRateSample> samples = List.of(
                sample(sourceId, START, 80, 'b'),
                sample(sourceId, START.plusSeconds(30), 100, 'c'),
                sample(sourceId, START.plusSeconds(90), 140, 'd'),
                sample(sourceId, END, 160, 'e'),
                sample(sourceId, END.plusSeconds(1), 190, 'f'));
        mirrorRepository.upsertHeartRateSamples(samples, UUID.randomUUID());

        assertThat(trackHealthRepository.findHeartRateSummary(START, END))
                .contains(new TrackHeartRateSummaryRow(4, 120, 80, 160, START, END));
        assertThat(trackHealthRepository.findHeartRateBuckets(START, END, 2))
                .satisfiesExactly(
                        bucket -> {
                            assertThat(bucket.averageBpm()).isEqualTo(90);
                            assertThat(bucket.minimumBpm()).isEqualTo(80);
                            assertThat(bucket.maximumBpm()).isEqualTo(100);
                            assertThat(bucket.sampleCount()).isEqualTo(2);
                        },
                        bucket -> {
                            assertThat(bucket.averageBpm()).isEqualTo(150);
                            assertThat(bucket.minimumBpm()).isEqualTo(140);
                            assertThat(bucket.maximumBpm()).isEqualTo(160);
                            assertThat(bucket.sampleCount()).isEqualTo(2);
                        });
    }

    @Test
    @Transactional
    void movingAverageUsesCanonicalStateWithHalfOpenBoundariesDespiteConflictingSpeedsAndEvents() {
        mirrorRepository.disconnect();
        long trackId = jdbc.queryForObject("INSERT INTO gps_track DEFAULT VALUES RETURNING id", Long.class);
        long dataId = jdbc.queryForObject("""
                INSERT INTO gps_track_data (gps_track_id, track_type)
                VALUES (?, 'RAW_OUTLIER_CLEANED') RETURNING id
                """, Long.class, trackId);
        long otherTrackId = jdbc.queryForObject("INSERT INTO gps_track DEFAULT VALUES RETURNING id", Long.class);
        int[] times = {0, 10, 20, 30, 40, 50, 60, 180, 190, 200, 210, 220};
        for (int i = 0; i < times.length; i++) {
            jdbc.update("""
                            INSERT INTO gps_track_data_points
                                (gps_track_data_id, moving_window_in_sec, point_index, point_index_max, point_timestamp, speed_in_kmh_moving_window, motion_state, point_long_lat)
                            VALUES (?, 30, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(0, 0), 4326))
                            """, dataId, i, times.length - 1, times[i] == 200 ? null : Timestamp.from(START.plusSeconds(times[i])),
                    times[i] == 50 ? null : times[i] == 40 ? 0.0 : 10.0,
                    times[i] == 10 || times[i] == 30 || times[i] == 40 || times[i] == 220 ? "MOVING"
                            : times[i] == 20 ? "STOPPED" : "UNKNOWN");
        }
        insertEvent(trackId, "STOP", 10, 20);
        insertEvent(trackId, "STOP", 12, 18); // Overlapping breaks must not duplicate samples.
        insertEvent(trackId, "GPS_GAP", 185, 190);
        insertEvent(otherTrackId, "STOP", 0, 220); // Other tracks must not affect the result.
        long sourceId = mirrorRepository.upsertDataSource(
                hex('a'), "FITBIT", "AUTOMATIC", "Fitbit", "Synthetic watch", "{}");
        mirrorRepository.upsertHeartRateSamples(List.of(
                sample(sourceId, START, 100, '0'),
                sample(sourceId, START.plusSeconds(5), 120, '1'),
                sample(sourceId, START.plusSeconds(10), 80, '2'),
                sample(sourceId, START.plusSeconds(15), 60, '3'),
                sample(sourceId, START.plusSeconds(20), 90, '4'),
                sample(sourceId, START.plusSeconds(25), 160, '5'),
                sample(sourceId, START.plusSeconds(35), 180, '6'),
                sample(sourceId, START.plusSeconds(45), 190, '7'),
                sample(sourceId, START.plusSeconds(90), 200, '8'),
                sample(sourceId, START.plusSeconds(182), 140, '9'),
                sample(sourceId, START.plusSeconds(185), 170, 'b'),
                sample(sourceId, START.plusSeconds(205), 210, 'c'),
                sample(sourceId, START.plusSeconds(215), 150, 'd'),
                sample(sourceId, START.plusSeconds(221), 220, 'e')), UUID.randomUUID());

        assertThat(trackHealthRepository.findMovingHeartRateSummary(trackId, START, START.plusSeconds(220)))
                .isEqualTo(new TrackMovingHeartRateSummaryRow(6, 800.0 / 6));
        assertThat(trackHealthRepository.findHeartRateSummary(START, START.plusSeconds(220)))
                .hasValueSatisfying(summary -> {
                    assertThat(summary.minimumBpm()).isEqualTo(60);
                    assertThat(summary.maximumBpm()).isEqualTo(210);
                    assertThat(summary.sampleCount()).isEqualTo(13);
                });

        jdbc.update("UPDATE gps_track_data_points SET speed_in_kmh_moving_window = 0 WHERE gps_track_data_id = ?", dataId);
        assertThat(trackHealthRepository.findMovingHeartRateSummary(trackId, START, START.plusSeconds(220)))
                .isEqualTo(new TrackMovingHeartRateSummaryRow(6, 800.0 / 6));
        jdbc.update("UPDATE gps_track_data_points SET motion_state = 'UNKNOWN' WHERE gps_track_data_id = ?", dataId);
        assertThat(trackHealthRepository.findMovingHeartRateSummary(trackId, START, START.plusSeconds(220)))
                .isEqualTo(new TrackMovingHeartRateSummaryRow(0, null));
    }

    @Test
    @Transactional
    void movingAverageIsUnavailableWithoutCanonicalGpsCoverage() {
        assertThat(trackHealthRepository.findMovingHeartRateSummary(Long.MAX_VALUE, START, END))
                .isEqualTo(new TrackMovingHeartRateSummaryRow(0, null));
    }

    private void insertEvent(long trackId, String type, int startSeconds, int endSeconds) {
        jdbc.update("""
                INSERT INTO gps_track_event (gps_track_id, event_type, source, start_timestamp, end_timestamp)
                VALUES (?, ?, 'DETECTED', ?, ?)
                """, trackId, type, Timestamp.from(START.plusSeconds(startSeconds)), Timestamp.from(START.plusSeconds(endSeconds)));
    }

    private static HeartRateSample sample(long sourceId, Instant timestamp, int bpm, char fingerprintCharacter) {
        return new HeartRateSample(
                sourceId,
                timestamp,
                timestamp.toString(),
                "+00:00",
                LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC),
                bpm,
                null,
                "WRIST",
                hex(fingerprintCharacter));
    }

    private static String hex(char character) {
        return String.valueOf(character).repeat(64);
    }
}
