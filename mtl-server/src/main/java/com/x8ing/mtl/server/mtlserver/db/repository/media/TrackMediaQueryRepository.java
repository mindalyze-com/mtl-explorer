package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TrackMediaQueryRepository {

    static final String DISABLE_JIT_FOR_TRANSACTION_SQL = "SET LOCAL jit = off";
    static final String SELECTED_MEDIA_COUNTS_BY_TRACK_SQL = """
            SELECT correlation.track_id, COUNT(*) AS media_count
            FROM media_track_correlation correlation
            WHERE correlation.selected
              AND correlation.track_id IN (:trackIds)
            GROUP BY correlation.track_id
            """;

    static final String TRACK_MEDIA_SQL = """
            WITH target_track AS MATERIALIZED (
                SELECT gt.id, gt.start_date, gt.end_date, gtd.id AS track_data_id
                FROM gps_track gt
                JOIN gps_track_data gtd
                  ON gtd.gps_track_id = gt.id
                 AND gtd.track_type = 'RAW_OUTLIER_CLEANED'
                WHERE gt.id = :trackId
                  AND gt.load_status = 'SUCCESS'
                  AND gt.duplicate_status = 'UNIQUE'
                  AND gt.track_source = 'IMPORTED'
                  AND gt.start_date IS NOT NULL
                  AND gt.end_date IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM gps_track_data_points time_point
                      WHERE time_point.gps_track_data_id = gtd.id
                        AND time_point.point_timestamp IS NOT NULL
                  )
            ),
            target_candidates AS MATERIALIZED (
                SELECT
                    m.id, ixf.name AS file_name, m.camera_make, m.camera_model,
                    m.exif_gps_date AS captured_at,
                    m.exif_gps_date AS adjusted_captured_at,
                    0 AS applied_camera_offset_seconds,
                    'EXIF_GPS' AS time_source,
                    m.exif_gps_location,
                    tt.track_data_id
                FROM target_track tt
                JOIN media_file m
                  ON m.exif_gps_date BETWEEN tt.start_date AND tt.end_date
                JOIN indexed_file ixf ON ixf.id = m.file_id
                WHERE m.exif_gps_date IS NOT NULL

                UNION ALL

                SELECT
                    m.id, ixf.name AS file_name, m.camera_make, m.camera_model,
                    m.exif_date_image_taken AS captured_at,
                    m.exif_date_image_taken + make_interval(secs => :cameraOffsetSeconds) AS adjusted_captured_at,
                    :cameraOffsetSeconds AS applied_camera_offset_seconds,
                    'EXIF_DATE_TAKEN' AS time_source,
                    m.exif_gps_location,
                    tt.track_data_id
                FROM target_track tt
                JOIN media_file m
                  ON m.exif_date_image_taken BETWEEN
                     tt.start_date - make_interval(secs => :cameraOffsetSeconds)
                     AND tt.end_date - make_interval(secs => :cameraOffsetSeconds)
                JOIN indexed_file ixf ON ixf.id = m.file_id
                WHERE m.exif_gps_date IS NULL
                  AND m.exif_date_image_taken IS NOT NULL
            ),
            selected_media AS (
                SELECT tc.*
                FROM target_candidates tc
                JOIN LATERAL (
                    SELECT ranked_track.id
                    FROM (
                        SELECT
                            candidate_track.id,
                            candidate_track.start_date,
                            candidate_track.end_date,
                            COALESCE(candidate_shape.track, candidate_canonical.track) AS route_shape,
                            COUNT(*) OVER () AS overlap_count
                        FROM gps_track candidate_track
                        LEFT JOIN gps_track_data candidate_shape
                          ON candidate_shape.gps_track_id = candidate_track.id
                         AND candidate_shape.track_type = 'SIMPLIFIED_SHAPE'
                         AND candidate_shape.precision_in_meter = 10
                        JOIN gps_track_data candidate_canonical
                          ON candidate_canonical.gps_track_id = candidate_track.id
                         AND candidate_canonical.track_type = 'RAW_OUTLIER_CLEANED'
                        WHERE candidate_track.load_status = 'SUCCESS'
                          AND candidate_track.duplicate_status = 'UNIQUE'
                          AND candidate_track.track_source = 'IMPORTED'
                          AND tc.adjusted_captured_at BETWEEN candidate_track.start_date AND candidate_track.end_date
                          AND EXISTS (
                              SELECT 1
                              FROM gps_track_data_points candidate_time_point
                              WHERE candidate_time_point.gps_track_data_id = candidate_canonical.id
                                AND candidate_time_point.point_timestamp IS NOT NULL
                          )
                    ) ranked_track
                    ORDER BY
                        CASE WHEN tc.exif_gps_location IS NULL THEN 1 ELSE 0 END,
                        CASE WHEN tc.exif_gps_location IS NULL OR ranked_track.overlap_count = 1 THEN NULL
                             ELSE ST_DistanceSphere(
                                 tc.exif_gps_location,
                                 ranked_track.route_shape
                             )
                        END ASC NULLS LAST,
                        ABS(EXTRACT(EPOCH FROM (
                            tc.adjusted_captured_at - (
                                ranked_track.start_date
                                + (ranked_track.end_date - ranked_track.start_date) / 2
                            )
                        ))) ASC,
                        EXTRACT(EPOCH FROM (ranked_track.end_date - ranked_track.start_date)) ASC,
                        ranked_track.id ASC
                    LIMIT 1
                ) best_match ON best_match.id = :trackId
            )
            SELECT
                sm.id,
                sm.file_name,
                sm.camera_make,
                sm.camera_model,
                sm.captured_at,
                sm.adjusted_captured_at,
                sm.applied_camera_offset_seconds,
                sm.time_source,
                ST_Y(sm.exif_gps_location) AS exif_lat,
                ST_X(sm.exif_gps_location) AS exif_lng,
                ST_Y(manual.location) AS manual_lat,
                ST_X(manual.location) AS manual_lng,
                manual.note AS manual_note,
                before_point.point_timestamp AS before_timestamp,
                before_point.point_index AS before_point_index,
                ST_Y(before_point.point_long_lat) AS before_lat,
                ST_X(before_point.point_long_lat) AS before_lng,
                before_point.point_altitude AS before_altitude,
                before_point.distance_in_meter_since_start AS before_distance_m,
                before_point.duration_since_start AS before_duration_seconds,
                after_point.point_timestamp AS after_timestamp,
                after_point.point_index AS after_point_index,
                ST_Y(after_point.point_long_lat) AS after_lat,
                ST_X(after_point.point_long_lat) AS after_lng,
                after_point.point_altitude AS after_altitude,
                after_point.distance_in_meter_since_start AS after_distance_m,
                after_point.duration_since_start AS after_duration_seconds,
                COUNT(*) OVER () AS total_elements
            FROM selected_media sm
            LEFT JOIN media_manual_location manual ON manual.media_id = sm.id
            LEFT JOIN LATERAL (
                SELECT point_timestamp, point_index, point_long_lat, point_altitude,
                       distance_in_meter_since_start, duration_since_start
                FROM gps_track_data_points
                WHERE gps_track_data_id = sm.track_data_id
                  AND point_timestamp IS NOT NULL
                  AND point_timestamp <= sm.adjusted_captured_at
                ORDER BY point_timestamp DESC, point_index DESC
                LIMIT 1
            ) before_point ON TRUE
            LEFT JOIN LATERAL (
                SELECT point_timestamp, point_index, point_long_lat, point_altitude,
                       distance_in_meter_since_start, duration_since_start
                FROM gps_track_data_points
                WHERE gps_track_data_id = sm.track_data_id
                  AND point_timestamp IS NOT NULL
                  AND point_timestamp >= sm.adjusted_captured_at
                ORDER BY point_timestamp ASC, point_index ASC
                LIMIT 1
            ) after_point ON TRUE
            ORDER BY sm.adjusted_captured_at, sm.id
            LIMIT :pageSize
            OFFSET :offset
            """;

    static final String PERSISTED_TRACK_MEDIA_SQL = """
            SELECT
                correlation.media_id AS id,
                indexed.name AS file_name,
                media.camera_make,
                media.camera_model,
                correlation.captured_at,
                correlation.adjusted_capture_time,
                correlation.applied_camera_offset_seconds,
                correlation.time_source,
                ST_Y(media.exif_gps_location) AS exif_lat,
                ST_X(media.exif_gps_location) AS exif_lng,
                ST_Y(correlation.route_location) AS route_lat,
                ST_X(correlation.route_location) AS route_lng,
                ST_Y(resolved.location) AS resolved_lat,
                ST_X(resolved.location) AS resolved_lng,
                ST_Y(manual.location) AS manual_lat,
                ST_X(manual.location) AS manual_lng,
                manual.note AS manual_note,
                resolved.position_origin,
                correlation.distance_in_meter_since_start,
                correlation.duration_since_start_seconds,
                correlation.track_point_index,
                correlation.track_point_time_delta_seconds,
                correlation.ambiguous,
                correlation.alternative_count,
                COUNT(*) OVER () AS total_elements
            FROM media_track_correlation correlation
            JOIN media_file media ON media.id = correlation.media_id
            JOIN indexed_file indexed ON indexed.id = media.file_id
            LEFT JOIN media_resolved_location resolved ON resolved.media_id = media.id
            LEFT JOIN media_manual_location manual ON manual.media_id = media.id
            WHERE correlation.track_id = :trackId
              AND correlation.selected
            ORDER BY correlation.adjusted_capture_time, correlation.media_id
            LIMIT :pageSize
            OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TrackMediaQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> findSelectedMediaCountsByTrackIds(List<Long> trackIds) {
        if (trackIds.isEmpty()) return Map.of();

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("trackIds", trackIds);
        List<Map.Entry<Long, Long>> entries = jdbcTemplate.query(
                SELECTED_MEDIA_COUNTS_BY_TRACK_SQL,
                params,
                (rs, rowNumber) -> Map.entry(rs.getLong("track_id"), rs.getLong("media_count")));
        Map<Long, Long> counts = new LinkedHashMap<>();
        entries.forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    @Transactional(readOnly = true)
    public TrackMediaPageResult<TrackMediaMatchRow> findByTrackId(
            long trackId,
            int cameraOffsetSeconds,
            int pageSize,
            long offset) {
        // Dynamic target-track bounds make PostgreSQL deliberately conservative about the candidate count.
        // Avoid per-connection JIT compilation for what is intentionally a small indexed lookup.
        jdbcTemplate.getJdbcTemplate().execute(DISABLE_JIT_FOR_TRANSACTION_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("trackId", trackId)
                .addValue("cameraOffsetSeconds", cameraOffsetSeconds)
                .addValue("pageSize", pageSize)
                .addValue("offset", offset);
        List<PageEntry<TrackMediaMatchRow>> rows = jdbcTemplate.query(
                TRACK_MEDIA_SQL,
                params,
                (rs, rowNumber) -> new PageEntry<>(mapRow(rs, rowNumber), rs.getLong("total_elements")));
        return pageResult(rows, () -> findByTrackId(trackId, cameraOffsetSeconds, 1, 0).totalElements(), offset);
    }

    @Transactional(readOnly = true)
    public TrackMediaPageResult<PersistedTrackMediaRow> findPersistedByTrackId(
            long trackId,
            int pageSize,
            long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("trackId", trackId)
                .addValue("pageSize", pageSize)
                .addValue("offset", offset);
        List<PageEntry<PersistedTrackMediaRow>> rows = jdbcTemplate.query(
                PERSISTED_TRACK_MEDIA_SQL,
                params,
                (rs, rowNumber) -> new PageEntry<>(mapPersistedRow(rs, rowNumber), rs.getLong("total_elements")));
        return pageResult(rows, () -> findPersistedByTrackId(trackId, 1, 0).totalElements(), offset);
    }

    private static <T> TrackMediaPageResult<T> pageResult(
            List<PageEntry<T>> rows,
            java.util.function.LongSupplier emptyPageTotalSupplier,
            long offset) {
        if (rows.isEmpty()) {
            return new TrackMediaPageResult<>(
                    List.of(),
                    offset == 0 ? 0 : emptyPageTotalSupplier.getAsLong());
        }
        return new TrackMediaPageResult<>(
                rows.stream().map(PageEntry::item).toList(),
                rows.getFirst().totalElements());
    }

    private record PageEntry<T>(T item, long totalElements) {
    }

    private static TrackMediaMatchRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new TrackMediaMatchRow(
                rs.getLong("id"),
                rs.getString("file_name"),
                rs.getString("camera_make"),
                rs.getString("camera_model"),
                date(rs, "captured_at"),
                date(rs, "adjusted_captured_at"),
                rs.getInt("applied_camera_offset_seconds"),
                TrackMediaDto.TIME_SOURCE.valueOf(rs.getString("time_source")),
                nullableDouble(rs, "exif_lat"),
                nullableDouble(rs, "exif_lng"),
                nullableDouble(rs, "manual_lat"),
                nullableDouble(rs, "manual_lng"),
                rs.getString("manual_note"),
                trackPoint(rs, "before"),
                trackPoint(rs, "after"));
    }

    private static PersistedTrackMediaRow mapPersistedRow(ResultSet rs, int rowNumber) throws SQLException {
        String origin = rs.getString("position_origin");
        return new PersistedTrackMediaRow(
                rs.getLong("id"),
                rs.getString("file_name"),
                rs.getString("camera_make"),
                rs.getString("camera_model"),
                date(rs, "captured_at"),
                date(rs, "adjusted_capture_time"),
                rs.getInt("applied_camera_offset_seconds"),
                TrackMediaDto.TIME_SOURCE.valueOf(rs.getString("time_source")),
                nullableDouble(rs, "exif_lat"),
                nullableDouble(rs, "exif_lng"),
                nullableDouble(rs, "route_lat"),
                nullableDouble(rs, "route_lng"),
                nullableDouble(rs, "resolved_lat"),
                nullableDouble(rs, "resolved_lng"),
                nullableDouble(rs, "manual_lat"),
                nullableDouble(rs, "manual_lng"),
                rs.getString("manual_note"),
                origin == null ? null : com.x8ing.mtl.server.mtlserver.db.entity.media.MediaPositionOrigin.valueOf(origin),
                nullableDouble(rs, "distance_in_meter_since_start"),
                nullableDouble(rs, "duration_since_start_seconds"),
                nullableInteger(rs, "track_point_index"),
                nullableDouble(rs, "track_point_time_delta_seconds"),
                rs.getBoolean("ambiguous"),
                rs.getInt("alternative_count"));
    }

    private static TrackMediaMatchRow.TrackPoint trackPoint(ResultSet rs, String prefix) throws SQLException {
        Date timestamp = date(rs, prefix + "_timestamp");
        if (timestamp == null) {
            return null;
        }
        return new TrackMediaMatchRow.TrackPoint(
                timestamp,
                nullableInteger(rs, prefix + "_point_index"),
                nullableDouble(rs, prefix + "_lat"),
                nullableDouble(rs, prefix + "_lng"),
                nullableDouble(rs, prefix + "_altitude"),
                nullableDouble(rs, prefix + "_distance_m"),
                nullableDouble(rs, prefix + "_duration_seconds"));
    }

    private static Date date(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : new Date(timestamp.getTime());
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
