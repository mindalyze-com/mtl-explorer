package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class TrackHealthRepository {

    private static final short CONNECTION_ID = 1;
    // Do not infer movement across recording gaps, even when both endpoints have speed.
    private static final int MAX_MOVING_INTERVAL_SECONDS = 60;

    private final JdbcTemplate jdbc;

    public TrackHealthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TrackHeartRateSummaryRow> findHeartRateSummary(Instant start, Instant end) {
        return jdbc.query("""
                                SELECT COUNT(*) AS sample_count,
                                       AVG(sample.beats_per_minute) AS average_bpm,
                                       MIN(sample.beats_per_minute) AS minimum_bpm,
                                       MAX(sample.beats_per_minute) AS maximum_bpm,
                                       MIN(sample.sample_time) AS first_sample_time,
                                       MAX(sample.sample_time) AS last_sample_time
                                  FROM google_health.heart_rate_sample sample
                                 WHERE sample.connection_id = ?
                                   AND sample.sample_time >= ?
                                   AND sample.sample_time <= ?
                                """, (resultSet, rowNumber) -> mapSummary(resultSet),
                        CONNECTION_ID, timestamp(start), timestamp(end)).stream()
                .filter(summary -> summary.sampleCount() > 0)
                .findFirst();
    }

    public List<TrackHeartRateBucketRow> findHeartRateBuckets(
            Instant start,
            Instant end,
            int maxBuckets) {
        double durationSeconds = Math.max(1.0, Duration.between(start, end).toMillis() / 1_000.0);
        double bucketWidthSeconds = durationSeconds / maxBuckets;
        int lastBucketIndex = maxBuckets - 1;

        return jdbc.query("""
                        SELECT LEAST(?, FLOOR(EXTRACT(EPOCH FROM (sample.sample_time - ?)) / ?))::integer
                                   AS bucket_index,
                               TO_TIMESTAMP(AVG(EXTRACT(EPOCH FROM sample.sample_time))) AS representative_time,
                               AVG(sample.beats_per_minute) AS average_bpm,
                               MIN(sample.beats_per_minute) AS minimum_bpm,
                               MAX(sample.beats_per_minute) AS maximum_bpm,
                               COUNT(*) AS sample_count
                          FROM google_health.heart_rate_sample sample
                         WHERE sample.connection_id = ?
                           AND sample.sample_time >= ?
                           AND sample.sample_time <= ?
                         GROUP BY bucket_index
                         ORDER BY bucket_index
                        """, (resultSet, rowNumber) -> new TrackHeartRateBucketRow(
                        instant(resultSet, "representative_time"),
                        resultSet.getDouble("average_bpm"),
                        resultSet.getInt("minimum_bpm"),
                        resultSet.getInt("maximum_bpm"),
                        resultSet.getLong("sample_count")
                ), lastBucketIndex, timestamp(start), bucketWidthSeconds,
                CONNECTION_ID, timestamp(start), timestamp(end));
    }

    public TrackMovingHeartRateSummaryRow findMovingHeartRateSummary(long trackId, Instant start, Instant end) {
        return jdbc.queryForObject("""
                        WITH intervals AS (
                            SELECT LAG(point.point_timestamp) OVER point_order AS start_time,
                                   point.point_timestamp AS end_time,
                                   point.motion_state
                              FROM gps_track_data_points point
                              JOIN gps_track_data data ON data.id = point.gps_track_data_id
                             WHERE data.gps_track_id = ? AND data.track_type = ?
                            WINDOW point_order AS (PARTITION BY data.id ORDER BY point.point_index)
                        ), moving_ranges AS (
                            SELECT RANGE_AGG(TSTZRANGE(start_time AT TIME ZONE 'UTC', end_time AT TIME ZONE 'UTC', '[)')) AS coverage
                              FROM intervals
                             WHERE motion_state = 'MOVING'
                               AND end_time > start_time
                               AND EXTRACT(EPOCH FROM (end_time - start_time)) <= ?
                        )
                        SELECT COUNT(*) AS sample_count, AVG(sample.beats_per_minute) AS average_bpm
                          FROM google_health.heart_rate_sample sample
                          CROSS JOIN moving_ranges
                         WHERE sample.connection_id = ?
                           AND sample.sample_time >= ? AND sample.sample_time <= ?
                           AND moving_ranges.coverage @> sample.sample_time
                        """, (resultSet, rowNumber) -> new TrackMovingHeartRateSummaryRow(
                        resultSet.getLong("sample_count"),
                        resultSet.getLong("sample_count") == 0 ? null : resultSet.getDouble("average_bpm")),
                trackId, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name(),
                MAX_MOVING_INTERVAL_SECONDS, CONNECTION_ID, timestamp(start), timestamp(end));
    }

    private static TrackHeartRateSummaryRow mapSummary(ResultSet resultSet) throws SQLException {
        return new TrackHeartRateSummaryRow(
                resultSet.getLong("sample_count"),
                resultSet.getDouble("average_bpm"),
                resultSet.getInt("minimum_bpm"),
                resultSet.getInt("maximum_bpm"),
                instant(resultSet, "first_sample_time"),
                instant(resultSet, "last_sample_time")
        );
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
