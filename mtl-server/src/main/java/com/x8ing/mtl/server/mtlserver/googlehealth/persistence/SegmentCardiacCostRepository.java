package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.MotionInterval;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.TimeRange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.MAX_SAMPLE_GAP_SECONDS;

@Repository
public class SegmentCardiacCostRepository {
    private static final short CONNECTION_ID = 1;
    private final JdbcTemplate jdbc;

    public SegmentCardiacCostRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MotionInterval> motion(long trackId, Instant start, Instant end) {
        return jdbc.query("""
                        WITH intervals AS (
                            SELECT LAG(point.point_timestamp) OVER point_order AT TIME ZONE 'UTC' AS start_time,
                                   point.point_timestamp AT TIME ZONE 'UTC' AS end_time,
                                   point.motion_state,
                                   point.energy_total_wh AS energy_wh
                              FROM gps_track_data_points point
                              JOIN gps_track_data data ON data.id = point.gps_track_data_id
                             WHERE data.gps_track_id = ? AND data.track_type = ?
                            WINDOW point_order AS (PARTITION BY data.id ORDER BY point.point_index)
                        )
                        SELECT * FROM intervals WHERE start_time < ? AND end_time > ? ORDER BY start_time, end_time
                        """, (rs, row) -> new MotionInterval(rs.getTimestamp("start_time").toInstant(),
                        rs.getTimestamp("end_time").toInstant(), rs.getString("motion_state") == null ? null
                        : com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.valueOf(rs.getString("motion_state")),
                        nullableDouble(rs, "energy_wh")),
                trackId, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name(), Timestamp.from(end), Timestamp.from(start));
    }

    public List<HeartRateSample> heartRate(Instant start, Instant end) {
        // Coincident device samples contribute one BPM value, not multiple heartbeats.
        return jdbc.query("""
                        SELECT sample_time, AVG(beats_per_minute) AS bpm
                          FROM google_health.heart_rate_sample
                         WHERE connection_id = ? AND sample_time >= ? AND sample_time <= ?
                         GROUP BY sample_time ORDER BY sample_time
                        """, (rs, row) -> new HeartRateSample(rs.getTimestamp("sample_time").toInstant(), rs.getDouble("bpm")),
                CONNECTION_ID, Timestamp.from(start.minusSeconds(MAX_SAMPLE_GAP_SECONDS)),
                Timestamp.from(end.plusSeconds(MAX_SAMPLE_GAP_SECONDS)));
    }

    public List<TimeRange> breaks(long trackId, Instant start, Instant end) {
        return breaks(trackId, start, end, false);
    }

    public List<TimeRange> recordingGaps(long trackId, Instant start, Instant end) {
        return breaks(trackId, start, end, false);
    }

    private List<TimeRange> breaks(long trackId, Instant start, Instant end, boolean includeStops) {
        return jdbc.query("""
                        SELECT start_timestamp AT TIME ZONE 'UTC' AS start_time, end_timestamp AT TIME ZONE 'UTC' AS end_time
                          FROM gps_track_event
                         WHERE gps_track_id = ? AND event_type IN (?, ?)
                           AND start_timestamp AT TIME ZONE 'UTC' < ? AND end_timestamp AT TIME ZONE 'UTC' > ?
                         ORDER BY start_timestamp
                        """, (rs, row) -> new TimeRange(rs.getTimestamp("start_time").toInstant(), rs.getTimestamp("end_time").toInstant()),
                trackId, includeStops ? GpsTrackEvent.EVENT_TYPE.STOP.name() : GpsTrackEvent.EVENT_TYPE.GPS_GAP.name(), GpsTrackEvent.EVENT_TYPE.GPS_GAP.name(),
                Timestamp.from(end), Timestamp.from(start));
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }
}
