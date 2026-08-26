package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.web.services.track.MediaKindResolver;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendBucketDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendGrouping;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendItemDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendKindFilter;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendScope;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MediaTrendQueryRepository {

    private static final String ELIGIBLE_TRACK_SQL = """
            track.load_status = 'SUCCESS'
            AND track.duplicate_status = 'UNIQUE'
            AND track.track_source = 'IMPORTED'
            AND track.statistics_exclusion_reason IS NULL
            AND track.id = ANY(string_to_array(:trackIdsCsv, ',')::bigint[])
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MediaTrendQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<MediaTrendBucketDto> findTrendBuckets(
            MediaTrendGrouping grouping,
            MediaTrendScope scope,
            List<Long> trackIds) {
        if (scope == MediaTrendScope.MATCHED_ACTIVITIES && trackIds.isEmpty()) return List.of();

        String bucketExpression = bucketExpression(grouping, "effective_capture_time");
        String sql = effectiveMediaCte(scope) + """
                , bucketed_media AS (
                    SELECT effective_media.*, %s AS bucket_key
                    FROM effective_media
                )
                SELECT
                    bucket_key,
                    CASE WHEN bucket_key = 'UNDATED' THEN 'Undated' ELSE bucket_key END AS label,
                    CASE
                        WHEN bucket_key IN ('TOTAL', 'UNDATED') THEN NULL
                        ELSE NULLIF(split_part(bucket_key, '-', 2), '')
                    END AS sub_group,
                    bucket_key = 'UNDATED' AS undated,
                    COUNT(*) FILTER (WHERE media_kind = 'IMAGE') AS image_count,
                    COUNT(*) FILTER (WHERE media_kind = 'VIDEO') AS video_count
                FROM bucketed_media
                GROUP BY bucket_key
                ORDER BY
                    CASE WHEN bucket_key = 'UNDATED' THEN 1 ELSE 0 END,
                    MIN(effective_capture_time) ASC NULLS LAST,
                    bucket_key ASC
                """.formatted(bucketExpression);

        return jdbcTemplate.query(sql, parameters(scope, trackIds), MediaTrendQueryRepository::mapBucket);
    }

    @Transactional(readOnly = true)
    public TrackMediaPageResult<MediaTrendItemDto> findItems(
            MediaTrendGrouping grouping,
            MediaTrendScope scope,
            String bucketKey,
            MediaTrendKindFilter kind,
            List<Long> trackIds,
            int pageSize,
            long offset) {
        if (scope == MediaTrendScope.MATCHED_ACTIVITIES && trackIds.isEmpty()) {
            return new TrackMediaPageResult<>(List.of(), 0);
        }

        MapSqlParameterSource params = parameters(scope, trackIds)
                .addValue("bucketKey", bucketKey)
                .addValue("pageSize", pageSize)
                .addValue("offset", offset);
        String whereSql = bucketWhere(grouping, bucketKey);
        if (kind != MediaTrendKindFilter.ALL) {
            whereSql += " AND media_kind = :kind";
            params.addValue("kind", kind.name());
        }

        String baseSql = effectiveMediaCte(scope) + " SELECT * FROM effective_media WHERE " + whereSql;
        long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + baseSql + ") counted_media",
                params,
                Long.class);
        if (totalItems == 0 || offset >= totalItems) {
            return new TrackMediaPageResult<>(List.of(), totalItems);
        }

        String pageSql = baseSql + """

                ORDER BY effective_capture_time DESC NULLS LAST,
                         CASE WHEN effective_capture_time IS NULL THEN indexed_create_time END DESC NULLS LAST,
                         id DESC
                LIMIT :pageSize
                OFFSET :offset
                """;
        List<MediaTrendItemDto> items = jdbcTemplate.query(pageSql, params, MediaTrendQueryRepository::mapItem);
        return new TrackMediaPageResult<>(items, totalItems);
    }

    private static String effectiveMediaCte(MediaTrendScope scope) {
        String mediaKindSql = MediaKindResolver.sqlCaseExpression("indexed.name");
        if (scope == MediaTrendScope.MATCHED_ACTIVITIES) {
            return """
                    WITH effective_media AS MATERIALIZED (
                        SELECT
                            media.id,
                            indexed.name AS file_name,
                            indexed.create_date AS indexed_create_time,
                            %s AS media_kind,
                            correlation.adjusted_capture_time AS effective_capture_time,
                            correlation.applied_camera_offset_seconds,
                            correlation.time_source,
                            correlation.track_id,
                            ST_Y(resolved.location) AS resolved_lat,
                            ST_X(resolved.location) AS resolved_lng,
                            resolved.position_origin,
                            correlation.track_point_time_delta_seconds,
                            correlation.ambiguous
                        FROM media_track_correlation correlation
                        JOIN media_file media ON media.id = correlation.media_id
                        JOIN indexed_file indexed ON indexed.id = media.file_id
                        JOIN gps_track track ON track.id = correlation.track_id
                        LEFT JOIN media_resolved_location resolved ON resolved.media_id = media.id
                        WHERE correlation.selected
                          AND %s
                    )
                    """.formatted(mediaKindSql, ELIGIBLE_TRACK_SQL);
        }

        return """
                WITH effective_media AS MATERIALIZED (
                    SELECT
                        media.id,
                        indexed.name AS file_name,
                        indexed.create_date AS indexed_create_time,
                        %s AS media_kind,
                        COALESCE(
                            media.exif_gps_date,
                            media.exif_date_image_taken
                                + make_interval(secs => COALESCE(correction.offset_seconds, 0))
                        ) AS effective_capture_time,
                        CASE
                            WHEN media.exif_gps_date IS NULL THEN COALESCE(correction.offset_seconds, 0)
                            ELSE 0
                        END AS applied_camera_offset_seconds,
                        CASE
                            WHEN media.exif_gps_date IS NOT NULL THEN 'EXIF_GPS'
                            WHEN media.exif_date_image_taken IS NOT NULL THEN 'EXIF_DATE_TAKEN'
                            ELSE NULL
                        END AS time_source,
                        correlation.track_id,
                        ST_Y(resolved.location) AS resolved_lat,
                        ST_X(resolved.location) AS resolved_lng,
                        resolved.position_origin,
                        correlation.track_point_time_delta_seconds,
                        correlation.ambiguous
                    FROM media_file media
                    JOIN indexed_file indexed ON indexed.id = media.file_id
                    LEFT JOIN media_time_correction correction ON correction.media_id = media.id
                    LEFT JOIN media_track_correlation correlation
                      ON correlation.media_id = media.id
                     AND correlation.selected
                    LEFT JOIN media_resolved_location resolved ON resolved.media_id = media.id
                )
                """.formatted(mediaKindSql);
    }

    private static String bucketExpression(MediaTrendGrouping grouping, String timestampColumn) {
        if (grouping == MediaTrendGrouping.TOTAL) {
            return "CASE WHEN " + timestampColumn + " IS NULL THEN 'UNDATED' ELSE 'TOTAL' END";
        }
        return "CASE WHEN " + timestampColumn + " IS NULL THEN 'UNDATED' ELSE to_char(date_trunc('"
               + grouping.dateTruncUnit() + "', " + timestampColumn + "), '" + grouping.labelFormat() + "') END";
    }

    private static String bucketWhere(MediaTrendGrouping grouping, String bucketKey) {
        if (MediaTrendGrouping.UNDATED_BUCKET_KEY.equals(bucketKey)) {
            return "effective_capture_time IS NULL";
        }
        if (grouping == MediaTrendGrouping.TOTAL) {
            return "effective_capture_time IS NOT NULL";
        }
        return "effective_capture_time IS NOT NULL AND to_char(date_trunc('" + grouping.dateTruncUnit()
               + "', effective_capture_time), '" + grouping.labelFormat() + "') = :bucketKey";
    }

    private static MapSqlParameterSource parameters(MediaTrendScope scope, List<Long> trackIds) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (scope == MediaTrendScope.MATCHED_ACTIVITIES) {
            params.addValue("trackIdsCsv", trackIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        return params;
    }

    private static MediaTrendBucketDto mapBucket(ResultSet rs, int rowNumber) throws SQLException {
        return new MediaTrendBucketDto(
                rs.getString("bucket_key"),
                rs.getString("label"),
                rs.getString("sub_group"),
                rs.getBoolean("undated"),
                rs.getLong("image_count"),
                rs.getLong("video_count"));
    }

    private static MediaTrendItemDto mapItem(ResultSet rs, int rowNumber) throws SQLException {
        String originValue = rs.getString("position_origin");
        TrackMediaDto.POSITION_ORIGIN origin = originValue == null
                ? null
                : TrackMediaDto.POSITION_ORIGIN.valueOf(originValue);
        String timeSourceValue = rs.getString("time_source");
        TrackMediaDto.TIME_SOURCE timeSource = timeSourceValue == null
                ? null
                : TrackMediaDto.TIME_SOURCE.valueOf(timeSourceValue);
        return new MediaTrendItemDto(
                rs.getLong("id"),
                TrackMediaDto.MEDIA_KIND.valueOf(rs.getString("media_kind")),
                rs.getString("file_name"),
                date(rs, "effective_capture_time"),
                nullableInteger(rs, "applied_camera_offset_seconds"),
                timeSource,
                nullableLong(rs, "track_id"),
                nullableDouble(rs, "resolved_lat"),
                nullableDouble(rs, "resolved_lng"),
                origin,
                origin == TrackMediaDto.POSITION_ORIGIN.TRACK_INTERPOLATED,
                rs.getBoolean("ambiguous"),
                nullableDouble(rs, "track_point_time_delta_seconds"));
    }

    private static Date date(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : new Date(value.getTime());
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
