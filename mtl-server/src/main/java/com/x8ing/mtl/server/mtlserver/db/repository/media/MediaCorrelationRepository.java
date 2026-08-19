package com.x8ing.mtl.server.mtlserver.db.repository.media;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MediaCorrelationRepository {

    static final String EXPAND_TRACK_WORK_SQL = """
            WITH claimed_tracks AS MATERIALIZED (
                SELECT work.track_id
                FROM media_correlation_track_work work
                ORDER BY work.requested_at, work.track_id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            ),
            affected_media AS (
                SELECT correlation.media_id
                FROM media_track_correlation correlation
                JOIN claimed_tracks work ON work.track_id = correlation.track_id

                UNION

                SELECT media.id
                FROM claimed_tracks work
                JOIN gps_track track ON track.id = work.track_id
                JOIN media_file media
                  ON media.exif_gps_date BETWEEN track.start_date AND track.end_date
                WHERE media.exif_gps_date IS NOT NULL

                UNION

                SELECT media.id
                FROM claimed_tracks work
                JOIN gps_track track ON track.id = work.track_id
                JOIN media_file media
                  ON media.exif_date_image_taken BETWEEN track.start_date AND track.end_date
                LEFT JOIN media_time_correction correction ON correction.media_id = media.id
                WHERE media.exif_gps_date IS NULL
                  AND media.exif_date_image_taken IS NOT NULL
                  AND correction.media_id IS NULL

                UNION

                SELECT media.id
                FROM claimed_tracks work
                JOIN gps_track track ON track.id = work.track_id
                JOIN media_time_correction correction ON TRUE
                JOIN media_file media
                  ON media.id = correction.media_id
                 AND media.exif_date_image_taken BETWEEN
                     track.start_date - make_interval(secs => correction.offset_seconds)
                     AND track.end_date - make_interval(secs => correction.offset_seconds)
                WHERE media.exif_gps_date IS NULL
                  AND media.exif_date_image_taken IS NOT NULL
            ),
            queued AS (
                INSERT INTO media_correlation_media_work(media_id, reason, requested_at)
                SELECT DISTINCT media_id, 'track_window_changed', CURRENT_TIMESTAMP
                FROM affected_media
                ON CONFLICT (media_id) DO UPDATE SET
                    reason = EXCLUDED.reason,
                    requested_at = EXCLUDED.requested_at,
                    attempt_count = 0,
                    last_error = NULL,
                    retry_after = EXCLUDED.requested_at
                RETURNING media_id
            )
            DELETE FROM media_correlation_track_work work
            USING claimed_tracks claimed
            WHERE work.track_id = claimed.track_id
            RETURNING work.track_id
            """;

    static final String INSERT_CORRELATIONS_SQL = """
            WITH media_times AS MATERIALIZED (
                SELECT
                    media.id AS media_id,
                    media.exif_gps_location,
                    CASE
                        WHEN media.exif_gps_date IS NOT NULL THEN media.exif_gps_date
                        ELSE media.exif_date_image_taken
                    END AS captured_at,
                    CASE
                        WHEN media.exif_gps_date IS NOT NULL THEN media.exif_gps_date
                        ELSE media.exif_date_image_taken
                             + make_interval(secs => COALESCE(correction.offset_seconds, 0))
                    END AS adjusted_capture_time,
                    CASE
                        WHEN media.exif_gps_date IS NOT NULL THEN 0
                        ELSE COALESCE(correction.offset_seconds, 0)
                    END AS applied_camera_offset_seconds,
                    CASE
                        WHEN media.exif_gps_date IS NOT NULL THEN 'EXIF_GPS'
                        ELSE 'EXIF_DATE_TAKEN'
                    END AS time_source
                FROM media_file media
                LEFT JOIN media_time_correction correction ON correction.media_id = media.id
                WHERE media.id IN (:mediaIds)
                  AND (media.exif_gps_date IS NOT NULL OR media.exif_date_image_taken IS NOT NULL)
            ),
            candidate_tracks AS MATERIALIZED (
                SELECT
                    media_time.*,
                    track.id AS track_id,
                    track.version AS track_version,
                    track.start_date,
                    track.end_date,
                    canonical.id AS track_data_id,
                    CASE
                        WHEN media_time.exif_gps_location IS NULL THEN NULL
                        ELSE ST_DistanceSphere(
                            media_time.exif_gps_location,
                            COALESCE(shape.track, canonical.track)
                        )
                    END AS exif_route_distance_meters
                FROM media_times media_time
                JOIN gps_track track
                  ON media_time.adjusted_capture_time BETWEEN track.start_date AND track.end_date
                JOIN LATERAL (
                    SELECT data.id, data.track
                    FROM gps_track_data data
                    WHERE data.gps_track_id = track.id
                      AND data.track_type = 'RAW_OUTLIER_CLEANED'
                    ORDER BY data.id DESC
                    LIMIT 1
                ) canonical ON TRUE
                LEFT JOIN LATERAL (
                    SELECT data.track
                    FROM gps_track_data data
                    WHERE data.gps_track_id = track.id
                      AND data.track_type = 'SIMPLIFIED_SHAPE'
                      AND data.precision_in_meter = 10
                    ORDER BY data.id DESC
                    LIMIT 1
                ) shape ON TRUE
                WHERE track.load_status = 'SUCCESS'
                  AND track.duplicate_status = 'UNIQUE'
                  AND track.track_source = 'IMPORTED'
                  AND EXISTS (
                      SELECT 1
                      FROM gps_track_data_points point
                      WHERE point.gps_track_data_id = canonical.id
                        AND point.point_timestamp IS NOT NULL
                  )
            ),
            scored_tracks AS (
                SELECT
                    candidate.*,
                    COUNT(*) OVER (PARTITION BY candidate.media_id) AS alternative_count
                FROM candidate_tracks candidate
            ),
            ranked_tracks AS (
                SELECT
                    scored.*,
                    ROW_NUMBER() OVER (
                        PARTITION BY scored.media_id
                        ORDER BY
                            scored.exif_route_distance_meters ASC NULLS LAST,
                            ABS(EXTRACT(EPOCH FROM (
                                scored.adjusted_capture_time
                                - (scored.start_date + (scored.end_date - scored.start_date) / 2)
                            ))) ASC,
                            EXTRACT(EPOCH FROM (scored.end_date - scored.start_date)) ASC,
                            scored.track_id ASC
                    ) AS candidate_rank
                FROM scored_tracks scored
            ),
            bracketed AS (
                SELECT
                    ranked.*,
                    before_point.point_timestamp AS before_timestamp,
                    before_point.point_index AS before_index,
                    before_point.point_long_lat AS before_location,
                    before_point.distance_in_meter_since_start AS before_distance,
                    before_point.duration_since_start AS before_duration,
                    after_point.point_timestamp AS after_timestamp,
                    after_point.point_index AS after_index,
                    after_point.point_long_lat AS after_location,
                    after_point.distance_in_meter_since_start AS after_distance,
                    after_point.duration_since_start AS after_duration
                FROM ranked_tracks ranked
                LEFT JOIN LATERAL (
                    SELECT point_timestamp, point_index, point_long_lat,
                           distance_in_meter_since_start, duration_since_start
                    FROM gps_track_data_points
                    WHERE gps_track_data_id = ranked.track_data_id
                      AND point_timestamp IS NOT NULL
                      AND point_timestamp <= ranked.adjusted_capture_time
                    ORDER BY point_timestamp DESC, point_index DESC
                    LIMIT 1
                ) before_point ON TRUE
                LEFT JOIN LATERAL (
                    SELECT point_timestamp, point_index, point_long_lat,
                           distance_in_meter_since_start, duration_since_start
                    FROM gps_track_data_points
                    WHERE gps_track_data_id = ranked.track_data_id
                      AND point_timestamp IS NOT NULL
                      AND point_timestamp >= ranked.adjusted_capture_time
                    ORDER BY point_timestamp ASC, point_index ASC
                    LIMIT 1
                ) after_point ON TRUE
            ),
            with_ratio AS (
                SELECT
                    bracketed.*,
                    CASE
                        WHEN before_timestamp IS NOT NULL
                         AND after_timestamp IS NOT NULL
                         AND after_timestamp > before_timestamp
                        THEN LEAST(1.0, GREATEST(0.0,
                            EXTRACT(EPOCH FROM (adjusted_capture_time - before_timestamp))
                            / EXTRACT(EPOCH FROM (after_timestamp - before_timestamp))))
                        ELSE 0.0
                    END AS interpolation_ratio
                FROM bracketed
            ),
            positioned AS (
                SELECT
                    with_ratio.*,
                    CASE
                        WHEN before_location IS NOT NULL AND after_location IS NOT NULL THEN
                            ST_SetSRID(ST_MakePoint(
                                ST_X(before_location)
                                    + interpolation_ratio * (ST_X(after_location) - ST_X(before_location)),
                                ST_Y(before_location)
                                    + interpolation_ratio * (ST_Y(after_location) - ST_Y(before_location))
                            ), 4326)
                        ELSE COALESCE(before_location, after_location)
                    END AS route_location,
                    CASE
                        WHEN before_distance IS NOT NULL AND after_distance IS NOT NULL
                            THEN before_distance + interpolation_ratio * (after_distance - before_distance)
                        ELSE COALESCE(before_distance, after_distance)
                    END AS route_distance,
                    CASE
                        WHEN before_duration IS NOT NULL AND after_duration IS NOT NULL
                            THEN before_duration + interpolation_ratio * (after_duration - before_duration)
                        ELSE COALESCE(before_duration, after_duration)
                    END AS route_duration
                FROM with_ratio
            )
            INSERT INTO media_track_correlation(
                media_id, track_id, track_version, algorithm_version,
                captured_at, adjusted_capture_time, applied_camera_offset_seconds, time_source,
                route_location, distance_in_meter_since_start, duration_since_start_seconds,
                track_point_index, track_point_time_delta_seconds, exif_route_distance_meters,
                selected, alternative_count, ambiguous, calculated_at
            )
            SELECT
                positioned.media_id,
                positioned.track_id,
                positioned.track_version,
                :algorithmVersion,
                positioned.captured_at,
                positioned.adjusted_capture_time,
                positioned.applied_camera_offset_seconds,
                positioned.time_source,
                positioned.route_location,
                positioned.route_distance,
                positioned.route_duration,
                CASE
                    WHEN positioned.before_timestamp IS NULL THEN positioned.after_index
                    WHEN positioned.after_timestamp IS NULL THEN positioned.before_index
                    WHEN ABS(EXTRACT(EPOCH FROM (positioned.adjusted_capture_time - positioned.before_timestamp)))
                         <= ABS(EXTRACT(EPOCH FROM (positioned.after_timestamp - positioned.adjusted_capture_time)))
                        THEN positioned.before_index
                    ELSE positioned.after_index
                END,
                LEAST(
                    COALESCE(ABS(EXTRACT(EPOCH FROM (
                        positioned.adjusted_capture_time - positioned.before_timestamp))), 'Infinity'::double precision),
                    COALESCE(ABS(EXTRACT(EPOCH FROM (
                        positioned.after_timestamp - positioned.adjusted_capture_time))), 'Infinity'::double precision)
                ),
                positioned.exif_route_distance_meters,
                positioned.candidate_rank = 1,
                positioned.alternative_count,
                positioned.alternative_count > 1,
                CURRENT_TIMESTAMP
            FROM positioned
            WHERE positioned.route_location IS NOT NULL
            """;

    static final String INSERT_RESOLVED_LOCATIONS_SQL = """
            INSERT INTO media_resolved_location(media_id, location, position_origin, correlation_id, resolved_at)
            SELECT
                media.id,
                CASE
                    WHEN manual.location IS NOT NULL THEN manual.location
                    WHEN media.exif_gps_location IS NOT NULL THEN media.exif_gps_location
                    ELSE correlation.route_location
                END,
                CASE
                    WHEN manual.location IS NOT NULL THEN 'USER_ASSIGNED'
                    WHEN media.exif_gps_location IS NOT NULL THEN 'EXIF_EMBEDDED'
                    ELSE 'TRACK_INTERPOLATED'
                END,
                CASE
                    WHEN manual.location IS NULL AND media.exif_gps_location IS NULL THEN correlation.id
                    ELSE NULL
                END,
                CURRENT_TIMESTAMP
            FROM media_file media
            LEFT JOIN media_manual_location manual ON manual.media_id = media.id
            LEFT JOIN media_track_correlation correlation
              ON correlation.media_id = media.id
             AND correlation.selected
            WHERE media.id IN (:mediaIds)
              AND COALESCE(manual.location, media.exif_gps_location, correlation.route_location) IS NOT NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MediaCorrelationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int enqueueStaleMedia(int algorithmVersion) {
        return jdbcTemplate.update("""
                INSERT INTO media_correlation_media_work(media_id, reason, requested_at)
                SELECT media.id, 'algorithm_version', CURRENT_TIMESTAMP
                FROM media_file media
                LEFT JOIN media_correlation_state state ON state.media_id = media.id
                WHERE state.media_id IS NULL OR state.algorithm_version <> :algorithmVersion
                ON CONFLICT (media_id) DO UPDATE SET
                    reason = EXCLUDED.reason,
                    requested_at = EXCLUDED.requested_at,
                    attempt_count = 0,
                    last_error = NULL,
                    retry_after = EXCLUDED.requested_at
                """, new MapSqlParameterSource("algorithmVersion", algorithmVersion));
    }

    public int expandTrackWork(int batchSize) {
        return jdbcTemplate.queryForList(
                EXPAND_TRACK_WORK_SQL,
                new MapSqlParameterSource("batchSize", batchSize),
                Long.class).size();
    }

    public List<Long> claimMediaWork(int batchSize) {
        return jdbcTemplate.queryForList("""
                SELECT media_id
                FROM media_correlation_media_work
                WHERE retry_after <= CURRENT_TIMESTAMP
                ORDER BY requested_at, media_id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """, new MapSqlParameterSource("batchSize", batchSize), Long.class);
    }

    public boolean claimSpecificMediaWork(long mediaId) {
        return !jdbcTemplate.queryForList("""
                SELECT media_id
                FROM media_correlation_media_work
                WHERE media_id = :mediaId
                FOR UPDATE
                """, new MapSqlParameterSource("mediaId", mediaId), Long.class).isEmpty();
    }

    public int deferMediaWork(long mediaId, String error, int retryDelaySeconds) {
        return jdbcTemplate.update("""
                UPDATE media_correlation_media_work
                SET attempt_count = attempt_count + 1,
                    last_error = :error,
                    retry_after = CURRENT_TIMESTAMP + make_interval(secs => :retryDelaySeconds)
                WHERE media_id = :mediaId
                """, new MapSqlParameterSource()
                .addValue("mediaId", mediaId)
                .addValue("error", error)
                .addValue("retryDelaySeconds", retryDelaySeconds));
    }

    public void rebuildMedia(List<Long> mediaIds, int algorithmVersion) {
        if (mediaIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mediaIds", mediaIds)
                .addValue("algorithmVersion", algorithmVersion);

        jdbcTemplate.queryForList("""
                SELECT id
                FROM media_file
                WHERE id IN (:mediaIds)
                ORDER BY id
                FOR UPDATE
                """, params, Long.class);
        jdbcTemplate.update(
                "DELETE FROM media_resolved_location WHERE media_id IN (:mediaIds)", params);
        jdbcTemplate.update(
                "DELETE FROM media_track_correlation WHERE media_id IN (:mediaIds)", params);
        jdbcTemplate.update(INSERT_CORRELATIONS_SQL, params);
        jdbcTemplate.update(INSERT_RESOLVED_LOCATIONS_SQL, params);
        jdbcTemplate.update("""
                INSERT INTO media_correlation_state(media_id, algorithm_version, calculated_at)
                SELECT id, :algorithmVersion, CURRENT_TIMESTAMP
                FROM media_file
                WHERE id IN (:mediaIds)
                ON CONFLICT (media_id) DO UPDATE SET
                    algorithm_version = EXCLUDED.algorithm_version,
                    calculated_at = EXCLUDED.calculated_at
                """, params);
        jdbcTemplate.update(
                "DELETE FROM media_correlation_media_work WHERE media_id IN (:mediaIds)", params);
        jdbcTemplate.getJdbcTemplate().execute("SELECT bump_data_freshness('media')");
    }

    public long countPendingWork() {
        Long count = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM media_correlation_media_work)
                    + (SELECT COUNT(*) FROM media_correlation_track_work)
                """, Long.class);
        return count == null ? 0 : count;
    }

    public long countCompletedMedia(int algorithmVersion) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_correlation_state WHERE algorithm_version = :algorithmVersion",
                new MapSqlParameterSource("algorithmVersion", algorithmVersion),
                Long.class);
        return count == null ? 0 : count;
    }

    public void upsertManualLocation(long mediaId, double latitude, double longitude, String note) {
        jdbcTemplate.update("""
                INSERT INTO media_manual_location(media_id, location, note, updated_at)
                VALUES (
                    :mediaId,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                    :note,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (media_id) DO UPDATE SET
                    location = EXCLUDED.location,
                    note = EXCLUDED.note,
                    updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource()
                .addValue("mediaId", mediaId)
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("note", note));
    }

    public int deleteManualLocation(long mediaId) {
        return jdbcTemplate.update(
                "DELETE FROM media_manual_location WHERE media_id = :mediaId",
                new MapSqlParameterSource("mediaId", mediaId));
    }

    public List<Long> saveTimeCorrection(List<Long> mediaIds, int offsetSeconds) {
        if (mediaIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mediaIds", mediaIds)
                .addValue("offsetSeconds", offsetSeconds);
        if (offsetSeconds == 0) {
            return jdbcTemplate.queryForList("""
                    DELETE FROM media_time_correction
                    WHERE media_id IN (:mediaIds)
                    RETURNING media_id
                    """, params, Long.class);
        }
        return jdbcTemplate.queryForList("""
                INSERT INTO media_time_correction(media_id, offset_seconds, updated_at)
                SELECT media.id, :offsetSeconds, CURRENT_TIMESTAMP
                FROM media_file media
                WHERE media.id IN (:mediaIds)
                  AND media.exif_gps_date IS NULL
                  AND media.exif_date_image_taken IS NOT NULL
                ON CONFLICT (media_id) DO UPDATE SET
                    offset_seconds = EXCLUDED.offset_seconds,
                    updated_at = EXCLUDED.updated_at
                WHERE media_time_correction.offset_seconds IS DISTINCT FROM EXCLUDED.offset_seconds
                RETURNING media_id
                """, params, Long.class);
    }
}
