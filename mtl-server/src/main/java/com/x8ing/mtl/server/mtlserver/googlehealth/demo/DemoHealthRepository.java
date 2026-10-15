package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.StoredPoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.HealthMetricPayload;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.StableFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Reads ordinary GPS and Health tables; generated Health records are the only persisted output.
 */
@Repository
@RequiredArgsConstructor
public class DemoHealthRepository {
    private static final short CONNECTION_ID = 1;
    private static final String SOURCE_FINGERPRINT = StableFingerprint.of("synthetic-shared-profile");
    private static final String ELIGIBLE = "t.load_status = 'SUCCESS' AND t.duplicate_status NOT IN ('EXCLUDED', 'DUPLICATE') AND NOT t.review_excluded";
    private static final String TRACKS = "SELECT t.id, COALESCE(NULLIF(f.hash, ''), CASE WHEN f.name IS NOT NULL THEN concat_ws('/', f.path, f.name) END, t.id::text) AS identity, "
                                         + "t.start_date, t.end_date, t.activity_type FROM gps_track t LEFT JOIN indexed_file f ON f.id=t.file_id WHERE " + ELIGIBLE
                                         + " AND NOT t.timing_unreliable AND t.start_date IS NOT NULL AND t.end_date > t.start_date";
    // The same SHA-256 selection bucket as DemoPhysiology.selected; no per-track skip state is needed.
    private static final String SELECTED_TRACKS = "SELECT candidate.* FROM (" + TRACKS + ") candidate WHERE "
                                                  + "('x' || substr(encode(sha256(convert_to(CAST(? AS text) || ':selection:' || candidate.identity, 'UTF8')), 'hex'), 1, "
                                                  + DemoPhysiology.SELECTION_HEX_DIGITS + "))::bit(32)::bigint * " + DemoPhysiology.FULL_PERCENT
                                                  + " < CAST(? AS bigint) * " + DemoPhysiology.SELECTION_BUCKET_COUNT;
    // Materialize coverage so each check joins stored exercises and tracks once.
    private static final String MISSING_DAYS = "WITH tracks AS (" + SELECTED_TRACKS + "), source AS "
                                               + "(SELECT id FROM google_health.data_source WHERE connection_id=1 AND fingerprint=?), "
                                               + "days AS (SELECT day::date AS date FROM generate_series(CAST(? AS date)::timestamp, CAST(? AS date)::timestamp, INTERVAL '1 day') day), "
                                               + """
                                                       sample_days AS MATERIALIZED (
                                                           SELECT (sample.sample_time AT TIME ZONE 'UTC')::date AS date, COUNT(*) AS samples
                                                           FROM google_health.heart_rate_sample sample
                                                           WHERE sample.connection_id=1 AND sample.data_source_id=(SELECT id FROM source)
                                                             AND sample.sample_time >= (SELECT MIN(date) FROM days)::timestamp AT TIME ZONE 'UTC'
                                                             AND sample.sample_time < ((SELECT MAX(date) FROM days) + 1)::timestamp AT TIME ZONE 'UTC'
                                                           GROUP BY (sample.sample_time AT TIME ZONE 'UTC')::date
                                                       ),
                                                       exercise_tracks AS MATERIALIZED (
                                                           SELECT DISTINCT owner.gps_track_id
                                                           FROM google_health.route_ownership owner
                                                           JOIN google_health.data_point point
                                                             ON point.connection_id=owner.connection_id AND point.identity_key=owner.identity_key
                                                           WHERE owner.connection_id=1 AND owner.gps_track_id IS NOT NULL
                                                             AND point.data_source_id=(SELECT id FROM source) AND point.data_type='exercise'
                                                       ), missing_tracks AS MATERIALIZED (
                                                           SELECT track.start_date, track.end_date FROM tracks track
                                                           LEFT JOIN exercise_tracks exercise ON exercise.gps_track_id=track.id
                                                           WHERE exercise.gps_track_id IS NULL
                                                       )
                                                       SELECT days.date FROM days
                                                       LEFT JOIN sample_days ON sample_days.date=days.date
                                                       WHERE EXISTS (
                                                           SELECT 1 FROM (VALUES ('steps','DAILY_ROLLUP'), ('distance','DAILY_ROLLUP'),
                                                               ('active-energy-burned','DAILY_ROLLUP'), ('daily-resting-heart-rate','RAW'),
                                                               ('weight','RAW'), ('sleep','RAW'), ('heart-rate','DAILY_ROLLUP')) expected(type, representation)
                                                           WHERE NOT EXISTS (SELECT 1 FROM google_health.data_point point
                                                               WHERE point.connection_id=1 AND point.data_source_id=(SELECT id FROM source)
                                                                 AND point.civil_date=days.date AND point.data_type=expected.type AND point.representation=expected.representation)
                                                       ) OR COALESCE(sample_days.samples, 0) < COALESCE((
                                                           SELECT (point.raw_json->'heartRate'->>'sampleCount')::bigint FROM google_health.data_point point
                                                           WHERE point.connection_id=1 AND point.data_source_id=(SELECT id FROM source)
                                                             AND point.civil_date=days.date AND point.data_type='heart-rate' AND point.representation='DAILY_ROLLUP'
                                                           LIMIT 1
                                                       ), 0) OR EXISTS (
                                                           SELECT 1 FROM missing_tracks track WHERE track.start_date < (days.date + 1)::timestamp
                                                             AND track.end_date >= days.date::timestamp
                                                       )
                                                       """;
    private final JdbcTemplate jdbc;
    private final MirrorRepository mirror;
    private final ObjectMapper json;
    private final DemoHealthProperties properties;

    public record Track(long id, String identity, Instant start, Instant end, GpsTrack.ACTIVITY_TYPE activity) {
    }

    public long sharedSource() {
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM google_health.connection WHERE google_user_id IS NOT NULL OR refresh_token_ciphertext IS NOT NULL) "
                                                    + "OR EXISTS (SELECT 1 FROM google_health.data_source WHERE platform IS DISTINCT FROM 'SYNTHETIC_DEMO')", Boolean.class))) {
            throw new IllegalStateException("Synthetic generation requires a dedicated demo Health database");
        }
        jdbc.update("INSERT INTO google_health.connection (id) VALUES (?) ON CONFLICT (id) DO NOTHING", CONNECTION_ID);
        var source = jdbc.query("SELECT id FROM google_health.data_source WHERE connection_id=? AND fingerprint=?",
                (rs, row) -> rs.getLong(1), CONNECTION_ID, SOURCE_FINGERPRINT);
        return source.isEmpty() ? mirror.upsertDataSource(SOURCE_FINGERPRINT, "SYNTHETIC_DEMO", "SIMULATED",
                "MTL Explorer synthetic demo", "Fictional wearable", "{\"synthetic\":true,\"scope\":\"shared-profile\"}") : source.getFirst();
    }

    public boolean trackCalculationPending() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM track_energy_recalculation energy "
                                                       + "JOIN gps_track t ON t.id=energy.track_id WHERE " + ELIGIBLE + ")", Boolean.class));
    }

    public List<Track> tracksForDay(LocalDate date, long afterId) {
        return jdbc.query("SELECT track.* FROM (" + SELECTED_TRACKS + ") track "
                          + "WHERE track.id > ? AND track.start_date < ? AND track.end_date >= ? ORDER BY track.id LIMIT ?",
                (rs, row) -> new Track(rs.getLong("id"), rs.getString("identity"), rs.getTimestamp("start_date").toInstant(),
                        rs.getTimestamp("end_date").toInstant(), rs.getString("activity_type") == null ? null
                        : GpsTrack.ACTIVITY_TYPE.valueOf(rs.getString("activity_type"))),
                properties.getSeed(), properties.getTrackPercentage(), afterId, midnight(date.plusDays(1)), midnight(date), properties.getBatchSize());
    }

    public List<DemoPhysiology.Point> points(long trackId) {
        return jdbc.query("""
                SELECT point.point_timestamp, point.distance_in_meter_since_start, point.point_altitude,
                       point.speed_in_kmh_moving_window, point.power_watts, point.motion_state
                  FROM gps_track_data_points point JOIN gps_track_data data ON data.id = point.gps_track_data_id
                 WHERE data.gps_track_id = ? AND data.track_type = 'RAW_OUTLIER_CLEANED'
                 ORDER BY point.point_index
                """, (rs, row) -> new DemoPhysiology.Point(rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), number(rs, 2),
                number(rs, 3), number(rs, 4), number(rs, 5), rs.getString(6)), trackId);
    }

    public LocalDate firstDay(LocalDate recentStart) {
        return jdbc.queryForObject("SELECT LEAST(COALESCE(MIN(t.start_date)::date, ?), ?) FROM gps_track t WHERE "
                                   + ELIGIBLE + " AND NOT t.timing_unreliable AND t.start_date >= TIMESTAMP '2000-01-01' AND t.start_date <= now()",
                LocalDate.class, recentStart, recentStart);
    }

    public List<LocalDate> missingDays(LocalDate start, LocalDate end) {
        return jdbc.query(MISSING_DAYS + " ORDER BY days.date DESC LIMIT ?", (rs, row) -> rs.getObject(1, LocalDate.class),
                properties.getSeed(), properties.getTrackPercentage(), SOURCE_FINGERPRINT, start, end, properties.getBatchSize());
    }

    public long missingDayCount(LocalDate start, LocalDate end) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM (" + MISSING_DAYS + ") missing", Long.class,
                properties.getSeed(), properties.getTrackPercentage(), SOURCE_FINGERPRINT, start, end);
    }

    public StoredPoint record(long source, String identity, StoredPoint.Representation representation,
                              LocalDate date, Instant start, Instant end, HealthMetricPayload payload) {
        return rawRecord(source, identity, payload.dataType(), representation, date, start, end,
                Map.of("synthetic", true, DataTypePolicy.jsonFieldName(payload.dataType()), payload));
    }

    public StoredPoint exerciseRecord(long source, long trackId, LocalDate date, Instant start, Instant end,
                                      Map<String, Object> payload) {
        return rawRecord(source, "exercise:" + trackId, DataTypePolicy.EXERCISE_DATA_TYPE,
                StoredPoint.Representation.RAW, date, start, end, payload);
    }

    private StoredPoint rawRecord(long source, String identity, String type, StoredPoint.Representation representation,
                                  LocalDate date, Instant start, Instant end, Map<String, Object> payload) {
        String raw = json.writeValueAsString(payload);
        return new StoredPoint(type, representation, StableFingerprint.of("demo:" + identity), StableFingerprint.of(raw), null,
                source, start, end, start == null ? null : start.toString(), end == null ? null : end.toString(),
                start == null ? null : LocalDateTime.ofInstant(start, ZoneOffset.UTC), end == null ? null : LocalDateTime.ofInstant(end, ZoneOffset.UTC),
                "+00:00", "+00:00", date, "SYNTHETIC_DEMO", raw);
    }

    public void storeMissingRecords(List<StoredPoint> records) {
        if (records.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(records.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(CONNECTION_ID);
        records.forEach(record -> parameters.add(record.identityKey()));
        var existing = new HashSet<>(jdbc.query("SELECT identity_key FROM google_health.data_point WHERE connection_id=? AND identity_key IN (" + placeholders + ")",
                (rs, row) -> rs.getString(1), parameters.toArray()));
        mirror.upsertDataPoints(records.stream().filter(record -> !existing.contains(record.identityKey())).toList(), UUID.randomUUID());
    }

    public void storeMissingSamples(long source, LocalDate date, List<DemoPhysiology.Sample> samples) {
        var existing = new HashSet<>(jdbc.query("SELECT sample_time FROM google_health.heart_rate_sample WHERE connection_id=? AND data_source_id=? AND sample_time >= ? AND sample_time < ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), CONNECTION_ID, source, midnight(date), midnight(date.plusDays(1))));
        mirror.upsertHeartRateSamples(samples.stream().filter(sample -> !existing.contains(sample.time()))
                .map(sample -> new HeartRateSample(source, sample.time(), sample.time().toString(), "+00:00",
                        LocalDateTime.ofInstant(sample.time(), ZoneOffset.UTC), sample.bpm(), "SIMULATED", "SYNTHETIC",
                        StableFingerprint.of("demo:timeline:" + sample.time()))).toList(), UUID.randomUUID());
    }

    public void associateExercise(long trackId) {
        jdbc.update("UPDATE google_health.route_ownership SET gps_track_id=?, route_state='NOT_APPLICABLE' WHERE connection_id=? AND identity_key=? AND gps_track_id IS NULL",
                trackId, CONNECTION_ID, StableFingerprint.of("demo:exercise:" + trackId));
    }

    private static Timestamp midnight(LocalDate date) {
        return Timestamp.from(date.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private static Double number(ResultSet rs, int column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
