package com.x8ing.mtl.server.mtlserver.jobs.exploration;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * Separate repository using {@link NamedParameterJdbcTemplate} instead of a {@code @Query} in GpsTrackRepository
 * because: (1) multi-level CTEs with PostGIS functions (ST_DumpSegments, ST_Segmentize) are fragile in JPA native
 * queries, (2) the result needs custom handling — 0 rows (no geometry) vs. 1 row — which doesn't map to a simple
 * projection interface, (3) keeping the 30+ line PostGIS query, its parameters, and result mapping co-located
 * in one class is easier to maintain.
 */
@Repository
@Slf4j
@JsonPropertyOrder({
        "jdbcTemplate"
})
public class ExplorationScoreQueryRepository {

    private static final String TRACK_TYPE = "SIMPLIFIED_SHAPE";
    private static final int INDEXED_PRECISION_M = 10;
    // The maintained bounds describe the cleaned source, while simplified shapes
    // snap to a 0.00001-degree grid. Allow conservatively for that displacement,
    // six-decimal bound rounding and spatial bounding-box precision. The exact
    // geometry predicates below still determine coverage, not this margin.
    private static final double CANDIDATE_BOUNDS_MARGIN_DEGREES = 0.001;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExplorationScoreQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Result of the PostGIS exploration score query for a single track.
     */
    @Data
    @JsonPropertyOrder({
            "totalM",
            "novelM",
            "explorationScore"
    })
    public static class ExplorationResult {
        /**
         * Total track length in meters (EPSG:3857 — may overestimate slightly at high latitudes).
         */
        private final double totalM;
        /**
         * Length in meters of segments that had no prior track within the corridor distance.
         */
        private final double novelM;
        /**
         * Ratio novelM/totalM, clamped to [0.0, 1.0]. 1.0 = entirely new territory.
         */
        private final double explorationScore;
    }

    /**
     * Segment-based PostGIS query that computes exploration score for a single track.
     * <p>
     * Algorithm:
     * 1. Fetch the target track geometry, transform to EPSG:3857 (meters)
     * 2. Split the target via ST_Segmentize + ST_DumpSegments and prepare each segment's search bounds once.
     * 3. Select eligible earlier tracks within those bounds and project them once.
     * 4. Test each segment against those candidates using bbox overlap (&&) followed by ST_DWithin.
     * 5. Aggregate: return total track length and sum of novel (unmatched) segment lengths.
     * Materialized CTEs prevent inlining from repeating candidate selection and projection for every segment.
     * Intermediate results can spill under the existing database work_mem; no archive cache is held in Java.
     * <p>
     * The score (novelM / totalM) is computed in Java, not in SQL.
     * <p>
     * Full documentation: {@code documentation/architecture/analysis/exploration.md}.
     */
    private static final String EXPLORATION_QUERY = """
            WITH target_track AS MATERIALIZED (
                SELECT
                    gt.id AS track_id,
                    gt.start_date,
                    ST_Transform(gtd.observed_path, 3857) AS geom_3857,
                    ST_Length(ST_Transform(gtd.observed_path, 3857)) AS total_m
                FROM gps_track gt
                JOIN gps_track_data gtd ON gt.id = gtd.gps_track_id
                WHERE gt.id = :trackId
                  AND NOT gt.review_excluded AND NOT gt.timing_unreliable
                  AND gtd.track_type = :trackType
                  AND gtd.precision_in_meter = :precisionInMeter
            ),
            target_segments AS MATERIALIZED (
                SELECT
                    tt.track_id,
                    tt.total_m,
                    s.geom AS segment_3857,
                    ST_Transform(ST_Expand(s.geom, :corridorWidthM), 4326) AS search_bbox_4326,
                    ST_Length(s.geom) AS segment_m
                FROM target_track tt
                CROSS JOIN LATERAL ST_DumpSegments(ST_Segmentize(tt.geom_3857, :corridorWidthM)) s
            ),
            target_bounds AS MATERIALIZED (
                -- Enclose the actual segment search boxes: transforming one expanded path
                -- envelope can wrap differently near the antimeridian and lose candidates.
                SELECT ST_SetSRID(ST_Extent(search_bbox_4326)::geometry, 4326) AS bbox_4326
                FROM target_segments
            ),
            prior_track_ids AS MATERIALIZED (
                SELECT gt.id
                FROM gps_track gt
                CROSS JOIN target_track tt
                CROSS JOIN target_bounds tb
                WHERE gt.start_date < tt.start_date
                  AND gt.duplicate_status = 'UNIQUE'
                  AND gt.load_status = 'SUCCESS'
                  AND NOT gt.review_excluded AND NOT gt.timing_unreliable
                  AND gt.bbox_max_lng >= ST_XMin(tb.bbox_4326) - :boundsMarginDegrees
                  AND gt.bbox_min_lng <= ST_XMax(tb.bbox_4326) + :boundsMarginDegrees
                  AND gt.bbox_max_lat >= ST_YMin(tb.bbox_4326) - :boundsMarginDegrees
                  AND gt.bbox_min_lat <= ST_YMax(tb.bbox_4326) + :boundsMarginDegrees
            ),
            prior_tracks AS MATERIALIZED (
                SELECT candidate.bbox_4326, candidate.geom_3857
                FROM prior_track_ids p
                CROSS JOIN target_bounds tb
                CROSS JOIN LATERAL (
                    SELECT ST_Envelope(gtd.track) AS bbox_4326,
                           ST_Transform(gtd.observed_path, 3857) AS geom_3857
                    FROM gps_track_data gtd
                    WHERE gtd.gps_track_id = p.id
                      AND gtd.track_type = :trackType
                      AND gtd.precision_in_meter = :precisionInMeter
                      AND gtd.track && tb.bbox_4326
                    -- Keep geometry retrieval per eligible ID. Flattening this
                    -- join can read thousands of later/ineligible spatial hits.
                    OFFSET 0
                ) candidate
            ),
            evaluated_segments AS (
                SELECT
                    ts.track_id,
                    ts.total_m,
                    ts.segment_m,
                    NOT EXISTS (
                        SELECT 1
                        FROM prior_tracks p
                        WHERE p.bbox_4326 && ts.search_bbox_4326
                          AND ST_DWithin(p.geom_3857, ts.segment_3857, :corridorWidthM)
                    ) AS is_novel
                FROM target_segments ts
            )
            SELECT
                MAX(total_m) AS total_m,
                SUM(CASE WHEN is_novel THEN segment_m ELSE 0 END) AS novel_m
            FROM evaluated_segments
            GROUP BY track_id
            """;

    // Match the partial per-track index predicate even in a generic prepared plan.
    // Keep one SQL definition; only these fixed application constants become literals.
    private static final String INDEXED_EXPLORATION_QUERY = EXPLORATION_QUERY
            .replace(":trackType", "'" + TRACK_TYPE + "'")
            .replace(":precisionInMeter", Integer.toString(INDEXED_PRECISION_M));

    /**
     * Calculate the exploration score for a single track.
     *
     * @param trackId          the gps_track.id to evaluate
     * @param startDate        the track's start_date (unused in query params but kept for clarity; the query reads it from DB)
     * @param corridorWidthM   radius in meters — segments within this distance of a prior track are "known"
     * @param precisionInMeter which simplified track variant to use (e.g. 10 = 10m precision)
     * @return the exploration result, or null if no geometry exists for the given track/precision
     */
    public ExplorationResult calculateExplorationScore(long trackId, Date startDate,
                                                       double corridorWidthM, int precisionInMeter) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("trackId", trackId)
                .addValue("trackType", TRACK_TYPE)
                .addValue("precisionInMeter", precisionInMeter)
                .addValue("boundsMarginDegrees", CANDIDATE_BOUNDS_MARGIN_DEGREES)
                .addValue("corridorWidthM", corridorWidthM);

        String query = precisionInMeter == INDEXED_PRECISION_M
                ? INDEXED_EXPLORATION_QUERY : EXPLORATION_QUERY;
        return jdbcTemplate.query(query, params, rs -> {
            if (!rs.next()) {
                log.info("Exploration score unavailable: trackId={} precision={}m; source is ineligible or has no observed geometry.", trackId, precisionInMeter);
                return null;
            }

            double totalM = rs.getDouble("total_m");
            double novelM = rs.getDouble("novel_m");

            if (totalM <= 0) {
                return new ExplorationResult(0, 0, 1.0);
            }

            double score = novelM / totalM;
            return new ExplorationResult(totalM, novelM, Math.min(1.0, Math.max(0.0, score)));
        });
    }

}
