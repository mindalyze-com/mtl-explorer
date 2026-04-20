package com.x8ing.mtl.server.mtlserver.jobs.exploration;

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
public class ExplorationScoreQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExplorationScoreQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Result of the PostGIS exploration score query for a single track.
     */
    @Data
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
     * 2. Break it into short segments via ST_Segmentize + ST_DumpSegments (max length = corridorWidthM)
     * 3. For each segment, check if ANY prior track (by start_date) passes within corridorWidthM
     * using a two-step filter: fast GIST bbox check (&&) then exact ST_DWithin
     * 4. Aggregate: return total track length and sum of novel (unmatched) segment lengths
     * <p>
     * The score (novelM / totalM) is computed in Java, not in SQL.
     *
     * @see <a href="EXPLORATION_SCORE.md">EXPLORATION_SCORE.md</a> for full documentation
     */
    private static final String EXPLORATION_QUERY = """
            WITH target_track AS (
                SELECT
                    gt.id AS track_id,
                    gt.start_date,
                    ST_Transform(gtd.track, 3857) AS geom_3857,
                    ST_Length(ST_Transform(gtd.track, 3857)) AS total_m
                FROM gps_track gt
                JOIN gps_track_data gtd ON gt.id = gtd.gps_track_id
                WHERE gt.id = :trackId
                  AND gtd.track_type = :trackType
                  AND gtd.precision_in_meter = :precisionInMeter
            ),
            target_segments AS (
                SELECT
                    track_id,
                    start_date,
                    total_m,
                    (ST_DumpSegments(ST_Segmentize(geom_3857, :corridorWidthM))).geom AS segment_3857
                FROM target_track
            ),
            evaluated_segments AS (
                SELECT
                    ts.track_id,
                    ts.total_m,
                    ST_Length(ts.segment_3857) AS segment_m,
                    NOT EXISTS (
                        SELECT 1
                        FROM gps_track_data gtd
                        JOIN gps_track gt ON gt.id = gtd.gps_track_id
                        WHERE gt.start_date < ts.start_date
                          AND gt.duplicate_status = 'UNIQUE'
                          AND gt.load_status = 'SUCCESS'
                          AND gtd.track_type = :trackType
                          AND gtd.precision_in_meter = :precisionInMeter
                          AND gtd.track && ST_Transform(ST_Expand(ts.segment_3857, :corridorWidthM), 4326)
                          AND ST_DWithin(ST_Transform(gtd.track, 3857), ts.segment_3857, :corridorWidthM)
                    ) AS is_novel
                FROM target_segments ts
            )
            SELECT
                MAX(total_m) AS total_m,
                SUM(CASE WHEN is_novel THEN segment_m ELSE 0 END) AS novel_m
            FROM evaluated_segments
            GROUP BY track_id
            """;

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
                .addValue("trackType", "SIMPLIFIED")
                .addValue("precisionInMeter", precisionInMeter)
                .addValue("corridorWidthM", corridorWidthM);

        return jdbcTemplate.query(EXPLORATION_QUERY, params, rs -> {
            if (!rs.next()) {
                log.warn("No geometry found for trackId={} at precision={}m. Skipping exploration score.", trackId, precisionInMeter);
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
