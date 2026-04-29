package com.x8ing.mtl.server.mtlserver.db.repository.gps;


import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.GpsTrackStatistics;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.IWayPointWithDistance;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface GpsTrackRepository extends JpaRepository<GpsTrack, Long> {


    @Query(nativeQuery = true, value = """
            select
            	gps_track_id
            from
            	gps_track_data gtd
            inner join gps_track gt on gt.id = gtd.gps_track_id
            where 1=1
            	and gtd.track && ST_Expand(
            	    ST_SetSRID(ST_Point(:longitude, :latitude), 4326),
            	    (:distanceInMeter + 10.0) / (111320.0 * GREATEST(ABS(COS(RADIANS(:latitude))), 0.01))
            	)
            	and ST_DWithin(gtd.track::geography, ST_SetSRID(ST_Point(:longitude, :latitude), 4326)::geography, :distanceInMeter + 10.0)
            	and gtd.track_type = 'SIMPLIFIED_SHAPE'
            	and gt.duplicate_status != 'DUPLICATE'
            	and gt.track_source = 'IMPORTED'
            	and precision_in_meter = 10
                and gt.id = ANY(:filterIds)
            """)
    List<Long> getTracksWithinDistanceToPoint(
            @Param("longitude") double longitude,
            @Param("latitude") double latitude,
            @Param("distanceInMeter") double distanceInMeter,
            @Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
            WITH
                linestring AS (
                    select track from gps_track gt where id=:track_id
                ),
                num_points AS (
                    SELECT
                        ST_NPoints(track) AS count
            FROM
                linestring
                ),
                points AS (
            SELECT
                i,
                ST_PointN(track, i) AS point
            FROM
                linestring,
                generate_series(1, (SELECT count FROM num_points)) AS i
                )
            SELECT
                i,
                ST_X(point) AS longitude,
                ST_Y(point) AS latitude,
                ST_Z(point) AS elevation,
                to_timestamp(ST_M(point)) timestamp,
                ST_Distance(ST_Transform(point,3857), ST_Transform(ST_SetSRID(ST_Point(:longitude, :latitude), 4326), 3857)) as distanceToPoint
            FROM
                points
            order by distanceToPoint asc
            limit :limit_rows
            """)
    List<IWayPointWithDistance> findWithinTrackClosestWaypointToGivenPoint(double longitude, double latitude, long track_id, int limit_rows);

    List<GpsTrack> findGpsTrackByLoadStatus(GpsTrack.LOAD_STATUS loadStatus);

    List<GpsTrack> findByDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS duplicateCheckStatus);

    /**
     * Duplicate-detector job queue source. Planner-created PLANNED tracks are excluded:
     * they are synthetic "ideas" and never need duplicate checking.
     */
    @Query("""
            SELECT t FROM GpsTrack t
            WHERE t.duplicateStatus = :status
              AND t.trackSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.TRACK_SOURCE.IMPORTED
            """)
    List<GpsTrack> findByDuplicateStatusExcludingPlanned(@Param("status") GpsTrack.DUPLICATE_CHECK_STATUS status);

    @Query("""
            SELECT t FROM GpsTrack t WHERE t.loadStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.LOAD_STATUS.SUCCESS
              AND (
                (t.startDate IS NOT NULL AND t.startDate < :cutoff)
                OR (t.metaTime IS NOT NULL AND t.metaTime < :cutoff)
                OR t.activityType = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE.SUPER_SONIC
                OR (t.maxDistanceBetweenPoints IS NOT NULL AND t.maxDistanceBetweenPoints > 1000.0)
              )
            """)
    List<GpsTrack> findSuspiciousTracks(@Param("cutoff") Date cutoff);

    @Query("""
            SELECT COUNT(t) FROM GpsTrack t
             WHERE t.loadStatus     = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.LOAD_STATUS.SUCCESS
               AND t.duplicateStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE
            """)
    long countGoodTracks();

    /**
     * IDs of successfully-loaded tracks that have no detected-stop totals yet.
     * Used by {@code MotionStatsService.backfillMissing()} after the stop-stats
     * columns were added — every pre-existing track needs a one-shot recalc.
     */
    @Query("""
            SELECT t.id FROM GpsTrack t
             WHERE t.loadStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.LOAD_STATUS.SUCCESS
               AND t.trackDurationStoppedSecs IS NULL
             ORDER BY t.id
            """)
    List<Long> findIdsWithMissingStopStats();

    /**
     * Bulk-excludes all good tracks beyond {@code targetCount} (ordered by id).
     * The sub-select uses OFFSET so no entities are loaded into memory.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE gps_track
               SET duplicate_status = 'EXCLUDED'
             WHERE id IN (
                   SELECT id FROM gps_track
                    WHERE load_status = 'SUCCESS'
                      AND duplicate_status = 'UNIQUE'
                    ORDER BY id
                    OFFSET :targetCount
                   )
            """)
    int excludeGoodTracksExceedingOffset(@Param("targetCount") int targetCount);

    /**
     * Bulk-excludes suspicious tracks that aren't already EXCLUDED.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE gps_track
               SET duplicate_status = 'EXCLUDED'
             WHERE load_status = 'SUCCESS'
               AND duplicate_status <> 'EXCLUDED'
               AND (
                   (start_date IS NOT NULL AND start_date < :cutoff)
                OR (meta_time  IS NOT NULL AND meta_time  < :cutoff)
                OR activity_type = 'SUPER_SONIC'
                OR (max_distance_between_points IS NOT NULL AND max_distance_between_points > 1000.0)
               )
            """)
    int excludeSuspiciousTracks(@Param("cutoff") Date cutoff);

    /**
     * Re-enables up to {@code limit} EXCLUDED tracks that are NOT suspicious
     * (i.e. they were excluded only by the count-trimming step).
     * Sets them back to NOT_CHECKED_YET so downstream jobs can re-evaluate.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE gps_track
               SET duplicate_status = 'NOT_CHECKED_YET'
             WHERE id IN (
                   SELECT id FROM gps_track
                    WHERE load_status = 'SUCCESS'
                      AND duplicate_status = 'EXCLUDED'
                      AND NOT (
                          (start_date IS NOT NULL AND start_date < :cutoff)
                       OR (meta_time  IS NOT NULL AND meta_time  < :cutoff)
                       OR activity_type = 'SUPER_SONIC'
                       OR (max_distance_between_points IS NOT NULL AND max_distance_between_points > 1000.0)
                      )
                    ORDER BY id
                    LIMIT :limit
                   )
            """)
    int reEnableNonSuspiciousExcludedTracks(@Param("cutoff") Date cutoff, @Param("limit") int limit);

    List<GpsTrack> findByActivityTypeSourceNull();

    /**
     * Lookup by origin — used by the planner feature to list "My Plans".
     */
    List<GpsTrack> findByTrackSource(GpsTrack.TRACK_SOURCE trackSource);

    /**
     * Finds tracks which are similar and are within a certain time range..
     *
     * @return List of GpsTrack-ids.
     */
    @Query(nativeQuery = true, value = """
            WITH orig_track AS (
                SELECT ST_Transform(track, 3857) as orig_track, id, gps_track_id
                FROM gps_track_data
                WHERE gps_track_id  = :gps_track_id
                  and precision_in_meter = 100
            )
            select\s
              gps_track_id_duplicate
            from (
            	SELECT
            	    gtd.id as id_duplicate ,
            	    gtd.gps_track_id as gps_track_id_duplicate,
            	    orig_track.id as id_orig_track,
            	    orig_track.gps_track_id as orig_track_gps_track_id,
            	    ST_FrechetDistance(ST_Transform(gtd.track, 3857), orig_track.orig_track) as similarity,
            	    ST_Transform(track, 3857) as track_duplicate_simplified,
            	    orig_track.orig_track
            	FROM gps_track_data gtd
            	         INNER JOIN orig_track ON 1=1
            	         INNER JOIN gps_track gt on gt.id = gtd.gps_track_id
            	where 1=1
            	  and gtd.precision_in_meter = 100
            	  -- TO_TIMESTAMP( '2017-03-31 9:30:20', 'YYYY-MM-DD HH24:MI:SS')
            	  AND (gt.start_date  > :start_date and gt.start_date < :end_date or gt.start_date is null)
            ) t
            where similarity < :similarity
            ORDER BY similarity asc
            """)
    List<Long> findSimilarTracksWithinTimeRangeForTrack(
            @Param("start_date") Date startDate,
            @Param("end_date") Date endDate,
            @Param("similarity") Double similarity,
            @Param("gps_track_id") Long trackId);


    @Query(nativeQuery = true, value = """
            WITH q AS (
                SELECT\s
                    gt.*,
                    COALESCE(track_duration_in_motion_secs, ABS(EXTRACT(EPOCH FROM (end_date - start_date)))) AS track_duration,
                    -- 1. Calculate the main grouping string early
                    to_char(start_date, cast(:group_by_date_format AS text)) AS group_by_col,
                    -- 2. Calculate the substring (everything after the first '-') early
                    split_part(to_char(start_date, cast(:group_by_date_format AS text)), '-', 2) AS sub_group_col,
                    :group_by_date_format AS group_by_date_format
                FROM gps_track_v gt
                WHERE duplicate_status = 'UNIQUE'
                    AND load_status = 'SUCCESS'
                    AND track_source = 'IMPORTED'
                    AND (cast(:filterIds AS bigint[]) IS NULL OR id = ANY(:filterIds))
            )
            SELECT\s
                group_by_col AS groupBy,
                sub_group_col AS subGroup,
                group_by_date_format,
                count(1) AS numberOfTracks,
                -- NEW: Counts unique days within the grouped period
                count(DISTINCT start_date::date) AS daysWithActivities,
                trunc(sum(track_duration)) AS totalTrackDurationSecs,
                round((1.0 * sum(track_duration) / 3600 / 24)::numeric, 1) AS totalTrackDurationDays,
                percentile_disc(0.5) WITHIN GROUP (ORDER BY track_duration) AS trackDurationSecsMed,
                trunc(avg(track_duration)) AS trackDurationSecsAvg,
                trunc(sum(track_length_in_meter)) AS trackLengthInMeterSum,
                trunc(percentile_disc(0.5) WITHIN GROUP (ORDER BY track_length_in_meter)) AS trackLengthInMeterMed,
                trunc(avg(track_length_in_meter)) AS trackLengthInMeterAvg,
                round((percentile_disc(0.5) WITHIN GROUP (ORDER BY median_distance_between_points))::numeric, 1) AS distanceBetweenGpsPointsMed,
                round(sum(COALESCE(energy_net_total_wh, 0))::numeric, 1) AS energyNetTotalWhSum,
                round((percentile_disc(0.5) WITHIN GROUP (ORDER BY COALESCE(power_watts_avg, 0)))::numeric, 1) AS powerWattsAvgMed,
                round((percentile_disc(0.5) WITHIN GROUP (ORDER BY COALESCE(normalized_power_watts, 0)))::numeric, 1) AS normalizedPowerMed,
                round(avg(CASE
                    WHEN COALESCE(normalized_power_watts, 0) > 0 AND cast(:threshold_power AS double precision) > 0
                    THEN normalized_power_watts / cast(:threshold_power AS double precision)
                END)::numeric, 3) AS intensityIndexAvg,
                round(avg(CASE
                    WHEN COALESCE(normalized_power_watts, 0) > 0
                        AND COALESCE(track_duration_in_motion_secs, 0) > 0
                        AND cast(:threshold_power AS double precision) > 0
                    THEN POWER(normalized_power_watts / cast(:threshold_power AS double precision), 2)
                         * (track_duration_in_motion_secs / 3600.0) * 100
                END)::numeric, 1) AS trainingLoadPerRideAvg,
                round(avg(CASE WHEN exploration_status = 'CALCULATED' AND exploration_score IS NOT NULL THEN exploration_score END)::numeric, 3) AS explorationScoreAvg
            FROM q
            WHERE start_date >= '1971-01-01'
                -- 3. Apply the conditional filter against the pre-calculated column
                AND (cast(:filter_value AS text) IS NULL OR sub_group_col = cast(:filter_value AS text))
            GROUP BY group_by_col, sub_group_col, group_by_date_format
            ORDER BY group_by_col ASC;
            """)
    List<GpsTrackStatistics> getTrackStatistics(@Param("group_by_date_format") String groupByDateFormat,
                                                @Param("filter_value") String filterValue,
                                                @Param("filterIds") Long[] filterIds,
                                                @Param("threshold_power") Double thresholdPowerWatts);

    @Query(nativeQuery = true, value = """
                select * from gps_track gt where duplicate_of = :gps_track_id or (id=:gps_track_id and duplicate_status!='UNIQUE' )
            """)
    List<Long> getDuplicatesForGpsTrackId(@Param("gps_track_id") Long gpsTrackId);

    @Query(nativeQuery = true, value = """
            select gt1.id from gps_track gt1 where 1=1
               	and gt1.start_date is not null\s
               	and gt1.duplicate_status='UNIQUE' and gt1.load_status='SUCCESS'
               	and gt1.track_source='IMPORTED'
                and gt1.id = ANY(:filterIds)
            	and gt1.start_date > (select gt2.start_date from gps_track gt2 where gt2.id = :id )\s
            order by gt1.start_date asc\s
            limit 50
            """)
    List<Long> getRelatedTrackIdsNext(@Param("id") Long gpsTrackId, @Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
            select gt1.id from gps_track gt1 where 1=1
               	and gt1.start_date is not null\s
               	and gt1.duplicate_status='UNIQUE' and gt1.load_status='SUCCESS'
               	and gt1.track_source='IMPORTED'
                and gt1.id = ANY(:filterIds)
            	and gt1.start_date < (select gt2.start_date from gps_track gt2 where gt2.id = :id )\s
            order by gt1.start_date desc\s
            limit 50
            """)
    List<Long> getRelatedTrackIdsPrevious(@Param("id") Long gpsTrackId, @Param("filterIds") Long[] filterIds);

    List<GpsTrack> findByIndexedFile(IndexedFile indexedFile);

    @Query(nativeQuery = true, value = "select distinct garmin_activity_id from gps_track gt where garmin_activity_id is not null order by garmin_activity_id asc")
    List<String> findGarminActivitiesIds();


    /**
     * Custom method using a native query to find tracks by a list of IDs
     * For TESTING only
     * <p>
     * Dynamic IN CLAUSE has a limit in postgres/hibernate of 64k. This seems caused as hibernate adds them as single params and not an array
     * Query: "SELECT id FROM gps_track WHERE id IN :ids", nativeQuery = true)
     * Fails with below:
     * Caused by: org.postgresql.util.PSQLException: PreparedStatement can have at most 65’535 parameters. Please consider using arrays, or splitting the query in several ones, or using COPY. Given query has 150’180 parameters
     * SELECT id FROM gps_track WHERE id IN (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, .....
     * <p>
     * Seems as when using a JDBC template with a proper array bigger lists are possible.
     * <p>
     * SOLUTION: In JPA Managed queries use ID=ANY(:ids) to trigger the user of array instead of an IN-Clause
     */
    @Query(value = "SELECT id FROM gps_track WHERE id = ANY(:ids)", nativeQuery = true)
    List<Long> findTrackIDsByIdsCustom(@Param("ids") Long[] ids);

    /**
     * Find segment siblings for a given track.
     * Returns all tracks that share the same source parent (including the parent itself),
     * or all children if the given track IS the parent.
     */
    @Query(nativeQuery = true, value = """
            SELECT gt.id FROM gps_track gt
            WHERE gt.load_status = 'SUCCESS'
              AND gt.id != :trackId
              AND (
                  -- siblings: same parent
                  (gt.source_parent_track_id IS NOT NULL
                   AND gt.source_parent_track_id = (SELECT COALESCE(g2.source_parent_track_id, g2.id)
                                                    FROM gps_track g2 WHERE g2.id = :trackId))
                  OR
                  -- parent itself (when current track is a child)
                  (gt.id = (SELECT g3.source_parent_track_id FROM gps_track g3 WHERE g3.id = :trackId))
                  OR
                  -- children (when current track is the parent)
                  (gt.source_parent_track_id = :trackId)
              )
            ORDER BY gt.source_segment_index ASC NULLS FIRST
            """)
    List<Long> findSegmentSiblingIds(@Param("trackId") Long trackId);

    // ── Exploration Score ──

    @Query("""
            SELECT t FROM GpsTrack t
            WHERE t.explorationStatus IN :statuses
              AND t.startDate IS NOT NULL
            ORDER BY t.startDate ASC
            """)
    List<GpsTrack> findByExplorationStatusIn(
            @Param("statuses") List<GpsTrack.EXPLORATION_STATUS> statuses,
            Pageable pageable);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE gps_track
            SET exploration_status = 'NEEDS_RECALCULATION'
            WHERE exploration_status = 'CALCULATED'
              AND start_date > :startDate
              AND start_date IS NOT NULL
              AND bbox_max_lat > :bboxMinLat
              AND bbox_min_lat < :bboxMaxLat
              AND bbox_max_lng > :bboxMinLng
              AND bbox_min_lng < :bboxMaxLng
            """)
    int resetExplorationForLaterOverlappingTracks(
            @Param("startDate") Date startDate,
            @Param("bboxMinLat") double bboxMinLat,
            @Param("bboxMaxLat") double bboxMaxLat,
            @Param("bboxMinLng") double bboxMinLng,
            @Param("bboxMaxLng") double bboxMaxLng);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE gps_track
            SET exploration_status = 'NEEDS_RECALCULATION'
            WHERE exploration_status = 'CALCULATED'
            """)
    int resetAllExplorationScores();

    /**
     * Self-invalidation: mark CALCULATED tracks as NEEDS_RECALCULATION if an earlier
     * SCHEDULED track exists whose bounding box overlaps theirs.
     * This replaces the per-track invalidation that was previously done during import,
     * avoiding lock contention on the gps_track table.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE gps_track c
            SET exploration_status = 'NEEDS_RECALCULATION'
            WHERE c.exploration_status = 'CALCULATED'
              AND c.start_date IS NOT NULL
              AND c.bbox_min_lat IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM gps_track s
                  WHERE s.exploration_status = 'SCHEDULED'
                    AND s.start_date IS NOT NULL
                    AND s.start_date < c.start_date
                    AND s.bbox_min_lat IS NOT NULL
                    AND s.bbox_max_lat > c.bbox_min_lat
                    AND s.bbox_min_lat < c.bbox_max_lat
                    AND s.bbox_max_lng > c.bbox_min_lng
                    AND s.bbox_min_lng < c.bbox_max_lng
              )
            """)
    int invalidateCalculatedTracksOverlappingScheduled();

    // ── Job progress counts ──

    @Query("SELECT t.duplicateStatus, COUNT(t) FROM GpsTrack t GROUP BY t.duplicateStatus")
    List<Object[]> countGroupedByDuplicateStatus();

    @Query("SELECT COUNT(t) FROM GpsTrack t WHERE t.activityTypeSource IS NULL AND t.loadStatus = 'SUCCESS' AND t.trackSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.TRACK_SOURCE.IMPORTED")
    long countActivityTypePending();

    @Query("SELECT COUNT(t) FROM GpsTrack t WHERE t.activityTypeSource IS NOT NULL AND t.loadStatus = 'SUCCESS' AND t.trackSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.TRACK_SOURCE.IMPORTED")
    long countActivityTypeDone();

    @Query("SELECT t.explorationStatus, COUNT(t) FROM GpsTrack t GROUP BY t.explorationStatus")
    List<Object[]> countGroupedByExplorationStatus();

    /**
     * Counts NOT_SCHEDULED tracks that were explicitly processed by the exploration job but had no
     * geometry (explorationCalcDate is set). These should count as "done" in the progress summary,
     * since the job evaluated them and determined they cannot have an exploration score.
     * Tracks that were always NOT_SCHEDULED (default, never scheduled) have explorationCalcDate = null.
     */
    @Query("SELECT COUNT(t) FROM GpsTrack t WHERE t.explorationStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.EXPLORATION_STATUS.NOT_SCHEDULED AND t.explorationCalcDate IS NOT NULL")
    long countExplorationExplicitlySkipped();

    /**
     * Returns (id, version) pairs for the given track IDs.
     * Used by mode=ids to include entity versions without loading full GpsTrack objects.
     */
    @Query("SELECT t.id, t.version FROM GpsTrack t WHERE t.id IN :ids")
    List<Object[]> findVersionsByIds(@Param("ids") List<Long> ids);

}
