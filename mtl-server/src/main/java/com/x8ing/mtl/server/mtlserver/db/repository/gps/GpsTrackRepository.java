package com.x8ing.mtl.server.mtlserver.db.repository.gps;


import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.*;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.energy.TrackEnergySummary;
import com.x8ing.mtl.server.mtlserver.gpx.RecordedPathGeometry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public interface GpsTrackRepository extends JpaRepository<GpsTrack, Long> {

    String TRACK_OVERVIEW_CTE_COLUMNS = """
            WITH q AS (
                SELECT
                    id,
                    start_date,
                    GREATEST(0, COALESCE(track_length_in_meter, 0))::double precision AS distance_m,
                    (CASE WHEN timing_unreliable THEN 0 ELSE GREATEST(0, COALESCE(track_duration_in_motion_secs, EXTRACT(EPOCH FROM (end_date - start_date)), 0)) END * 1000.0)::double precision AS duration_ms,
                    GREATEST(0, COALESCE(ascent_in_meter, 0))::double precision AS ascent_m,
                    CASE WHEN timing_unreliable THEN 0 ELSE GREATEST(0, COALESCE(CASE WHEN NOT timing_unreliable THEN energy_net_total_wh END, 0)) END::double precision AS energy_wh
            """;

    @Query(nativeQuery = true, value = """
            select
                gps_track_id
            from
                gps_track_data gtd
            inner join gps_track gt on gt.id = gtd.gps_track_id
            where 1=1
                and ST_DWithin(gtd.observed_path::geography, ST_SetSRID(ST_Point(:longitude, :latitude), 4326)::geography, :distanceInMeter + 10.0)
                and gtd.track_type = 'SIMPLIFIED_SHAPE'
                and not gt.review_map_excluded
                and gt.track_source = 'IMPORTED'
                and precision_in_meter = 10
                and gt.id = ANY(:filterIds)
            order by ST_Distance(
                gtd.observed_path::geography,
                ST_SetSRID(ST_Point(:longitude, :latitude), 4326)::geography
            ), gps_track_id
            """)
    List<Long> getTracksWithinDistanceToPoint(
            @Param("longitude") double longitude,
            @Param("latitude") double latitude,
            @Param("distanceInMeter") double distanceInMeter,
            @Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
                                               SELECT gtd.gps_track_id FROM gps_track_data gtd
                                               JOIN gps_track gt ON gt.id = gtd.gps_track_id
                                               WHERE gtd.track_type = 'RAW_OUTLIER_CLEANED'
                                                 AND NOT gt.review_map_excluded AND gt.track_source = 'IMPORTED'
                                                 AND gt.id = ANY(:filterIds)
                                                 AND ST_DWithin(gtd.observed_path::geography,
                                                     ST_Point(:longitude, :latitude, 4326)::geography, :distanceInMeter +
                                               """ + RecordedPathGeometry.CANDIDATE_ALLOWANCE_METRES + """
                                                 )
                                               ORDER BY gtd.gps_track_id
                                               """)
    List<Long> getSegmentCandidateTrackIds(@Param("longitude") double longitude,
                                           @Param("latitude") double latitude,
                                           @Param("distanceInMeter") double distanceInMeter,
                                           @Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
            WITH target_point AS (
                SELECT ST_SetSRID(ST_Point(:longitude, :latitude), 4326) AS point
            ), nearby_tracks AS (
                SELECT
                    gtd.gps_track_id AS "trackId",
                    MIN(ST_Distance(gtd.observed_path::geography, target.point::geography)) AS "distanceMeters"
                FROM gps_track_data gtd
                INNER JOIN gps_track gt ON gt.id = gtd.gps_track_id
                CROSS JOIN target_point target
                WHERE ST_DWithin(gtd.observed_path::geography, target.point::geography, :distanceInMeter + 10.0)
                  AND gtd.track_type = 'SIMPLIFIED_SHAPE'
                  AND NOT gt.review_map_excluded
                  AND gt.track_source = 'IMPORTED'
                  AND gtd.precision_in_meter = 10
                  AND gt.id = ANY(:filterIds)
                GROUP BY gtd.gps_track_id
            )
            SELECT "trackId", "distanceMeters"
            FROM nearby_tracks
            ORDER BY "distanceMeters", "trackId"
            """)
    List<NearbyTrackDistance> getTracksWithDistanceToPoint(
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
     * Includes successful imports that are still waiting for duplicate detection so the
     * initial map can follow a large import without running that memory-intensive job early.
     * Confirmed duplicates and excluded tracks remain outside the visible bounds.
     */
    @Query("""
            SELECT
                MIN(t.bboxMinLng) AS minLng,
                MIN(t.bboxMinLat) AS minLat,
                MAX(t.bboxMaxLng) AS maxLng,
                MAX(t.bboxMaxLat) AS maxLat
            FROM GpsTrack t
            WHERE t.loadStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.LOAD_STATUS.SUCCESS
              AND t.duplicateStatus IN (
                  com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE,
                  com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET
              )
              AND t.trackSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.TRACK_SOURCE.IMPORTED
              AND t.reviewMapExcluded = false
              AND t.bboxMinLat IS NOT NULL
              AND t.bboxMaxLat IS NOT NULL
              AND t.bboxMinLng IS NOT NULL
              AND t.bboxMaxLng IS NOT NULL
              AND t.bboxMinLat <= t.bboxMaxLat
              AND t.bboxMinLng <= t.bboxMaxLng
              AND t.bboxMinLat BETWEEN :minLatitude AND :maxLatitude
              AND t.bboxMaxLat BETWEEN :minLatitude AND :maxLatitude
              AND t.bboxMinLng BETWEEN :minLongitude AND :maxLongitude
              AND t.bboxMaxLng BETWEEN :minLongitude AND :maxLongitude
            """)
    GpsTrackBounds findImportedTrackBounds(
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude);

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
            SELECT t.id FROM GpsTrack t
            WHERE t.duplicateStatus = :status
              AND t.trackSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.TRACK_SOURCE.IMPORTED
            ORDER BY t.id
            """)
    List<Long> findIdsByDuplicateStatusExcludingPlanned(
            @Param("status") GpsTrack.DUPLICATE_CHECK_STATUS status, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE GpsTrack t
               SET t.duplicateStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.DUPLICATE_CHECK_STATUS.FAILED,
                   t.duplicateOf = NULL,
                   t.duplicateDetails = :details
             WHERE t.id = :id
               AND t.duplicateStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET
            """)
    int markDuplicateCheckFailedIfPending(@Param("id") Long id, @Param("details") String details);

    /**
     * Duplicate detection owns only the duplicate result. Preserve concurrent exclusions or failures.
     */
    @Modifying
    @Query("""
            UPDATE GpsTrack t
               SET t.duplicateStatus = :status,
                   t.duplicateOf = :duplicateOf
             WHERE t.id = :id AND t.duplicateStatus = :expectedStatus
            """)
    int updateDuplicateDetection(
            @Param("id") Long id,
            @Param("expectedStatus") GpsTrack.DUPLICATE_CHECK_STATUS expectedStatus,
            @Param("status") GpsTrack.DUPLICATE_CHECK_STATUS status,
            @Param("duplicateOf") Long duplicateOf);

    @Modifying
    @Query("""
            UPDATE GpsTrack t
               SET t.duplicateStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET,
                   t.duplicateOf = NULL
             WHERE t.duplicateOf = :primaryId AND t.id <> :primaryId
            """)
    int releaseDuplicateReferences(@Param("primaryId") Long primaryId);

    @Modifying
    @Query("""
            UPDATE GpsTrack t SET t.sourceParentTrackId = NULL
             WHERE t.sourceParentTrackId = :parentId AND t.id <> :parentId
            """)
    int clearSegmentParentReferences(@Param("parentId") Long parentId);

    boolean existsByTrackSourceAndDuplicateStatus(GpsTrack.TRACK_SOURCE source, GpsTrack.DUPLICATE_CHECK_STATUS status);

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
     * Bulk-excludes good tracks beyond {@code targetCount}, while retaining tracks
     * that spatially and temporally match GPS-timed demo photos.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE gps_track
               SET duplicate_status = 'EXCLUDED'
             WHERE id IN (
                   SELECT track.id
                     FROM gps_track track
                    WHERE track.load_status = 'SUCCESS'
                      AND track.duplicate_status = 'UNIQUE'
                    ORDER BY
                      CASE WHEN EXISTS (SELECT 1 FROM indexed_file file WHERE file.id = track.file_id
                          AND file.path = 'demo-health-showcase' AND file.name IN ('walking.gpx', 'running.gpx', 'bicycle.gpx')) THEN 0 ELSE 1 END,
                      CASE WHEN EXISTS (
                          SELECT 1
                            FROM media_file media
                            JOIN LATERAL (
                                SELECT data.observed_path
                                  FROM gps_track_data data
                                 WHERE data.gps_track_id = track.id
                                   AND data.track_type = 'RAW_OUTLIER_CLEANED'
                                 ORDER BY data.id DESC
                                 LIMIT 1
                            ) route ON TRUE
                           WHERE media.exif_gps_date BETWEEN track.start_date AND track.end_date
                             AND media.exif_gps_location IS NOT NULL
                             AND ST_DWithin(
                                 media.exif_gps_location::geography,
                                 route.observed_path::geography,
                                 :maxPhotoDistanceMeters
                             )
                      ) THEN 0 ELSE 1 END,
                      track.id
                    OFFSET :targetCount
                   )
            """)
    int excludeGoodTracksExceedingOffset(
            @Param("targetCount") int targetCount,
            @Param("maxPhotoDistanceMeters") double maxPhotoDistanceMeters);

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
     * Re-enables count-trimmed tracks needed by GPS-timed demo photos. The
     * duplicate detector validates them again before they become visible.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE gps_track track
               SET duplicate_status = 'NOT_CHECKED_YET',
                   duplicate_of = NULL
             WHERE track.load_status = 'SUCCESS'
               AND track.duplicate_status = 'EXCLUDED'
               AND track.track_source = 'IMPORTED'
               AND (track.start_date IS NULL OR track.start_date >= :cutoff)
               AND (track.meta_time IS NULL OR track.meta_time >= :cutoff)
               AND (track.activity_type IS NULL OR track.activity_type <> 'SUPER_SONIC')
               AND (track.max_distance_between_points IS NULL
                    OR track.max_distance_between_points <= 1000.0)
               AND EXISTS (
                   SELECT 1
                     FROM media_file media
                     JOIN LATERAL (
                         SELECT data.observed_path
                           FROM gps_track_data data
                          WHERE data.gps_track_id = track.id
                            AND data.track_type = 'RAW_OUTLIER_CLEANED'
                          ORDER BY data.id DESC
                          LIMIT 1
                     ) route ON TRUE
                    WHERE media.exif_gps_date BETWEEN track.start_date AND track.end_date
                      AND media.exif_gps_location IS NOT NULL
                      AND ST_DWithin(
                          media.exif_gps_location::geography,
                          route.observed_path::geography,
                          :maxPhotoDistanceMeters
                      )
               )
            """)
    int reEnablePhotoMatchedExcludedTracks(
            @Param("cutoff") Date cutoff,
            @Param("maxPhotoDistanceMeters") double maxPhotoDistanceMeters);

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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM GpsTrack t WHERE t.id = :id")
    java.util.Optional<GpsTrack> findByIdForUpdate(@Param("id") Long id);

    /**
     * Persist the energy worker's output without merging its loaded track or changing metadata.
     * The caller holds the track lock so activity and weight inputs remain current.
     */
    @Modifying
    @Query("""
            UPDATE GpsTrack t
               SET t.energyGravitationalTotalWh = :#{#summary.gravitationalAscentTotalWh},
                   t.energyGravitationalDescentWh = :#{#summary.gravitationalDescentTotalWh},
                   t.energyAeroDragTotalWh = :#{#summary.aeroDragTotalWh},
                   t.energyRollingResistanceTotalWh = :#{#summary.rollingResistanceTotalWh},
                   t.energyKineticPositiveTotalWh = :#{#summary.kineticPositiveTotalWh},
                   t.energyNetTotalWh = :#{#summary.netEnergyTotalWh},
                   t.energyWeightKgUsed = :#{#summary.weightKgUsed},
                   t.metabolicEnergy = :#{#summary.metabolicEnergy},
                   t.powerWattsAvg = :#{#summary.powerWattsAvg},
                   t.powerWattsMovingAvg = :#{#summary.powerWattsMovingAvg},
                   t.powerWattsMax = :#{#summary.powerWattsMax},
                   t.powerWatts30sMax = :#{#summary.powerWatts30sMax},
                   t.normalizedPowerWatts = :#{#summary.normalizedPowerWatts}
             WHERE t.id = :id
            """)
    int updateEnergyResults(@Param("id") Long id, @Param("summary") TrackEnergySummary summary);

    List<GpsTrack> findByActivityTypeSourceNull();

    @Query("""
            SELECT t.id FROM GpsTrack t
            WHERE t.activityTypeSource IS NULL
              AND t.duplicateStatus IS NOT NULL
              AND t.duplicateStatus <> com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET
            """)
    List<Long> findIdsPendingActivityClassification();

    @Query("""
            SELECT t.id FROM GpsTrack t
            WHERE t.activityTypeSource IS NULL
              AND t.duplicateStatus IS NOT NULL
              AND t.duplicateStatus <> com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET
            ORDER BY t.id
            """)
    List<Long> findIdsPendingActivityClassification(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE GpsTrack t
               SET t.activityType = :activityType,
                   t.activityTypeSource = :activityTypeSource,
                   t.activityTypeSourceDetails = :activityTypeSourceDetails
             WHERE t.id = :id
               AND t.activityTypeSource IS NULL
            """)
    int updateActivityClassificationIfPending(
            @Param("id") Long id,
            @Param("activityType") GpsTrack.ACTIVITY_TYPE activityType,
            @Param("activityTypeSource") GpsTrack.ACTIVITY_TYPE_SOURCE activityTypeSource,
            @Param("activityTypeSourceDetails") String activityTypeSourceDetails);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE GpsTrack t
               SET t.activityType = :activityType,
                   t.activityTypeSource = :activityTypeSource,
                   t.activityTypeSourceDetails = :activityTypeSourceDetails
             WHERE t.id = :id
               AND (t.activityTypeSource IS NULL
                    OR t.activityTypeSource <> com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET)
            """)
    int updateActivityClassificationUnlessUserSet(
            @Param("id") Long id,
            @Param("activityType") GpsTrack.ACTIVITY_TYPE activityType,
            @Param("activityTypeSource") GpsTrack.ACTIVITY_TYPE_SOURCE activityTypeSource,
            @Param("activityTypeSourceDetails") String activityTypeSourceDetails);

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
                SELECT ST_Transform(ST_Force2D(track), 3857) as orig_track, id, gps_track_id
                FROM gps_track_data
                WHERE gps_track_id  = :gps_track_id
                  and precision_in_meter = 100
                  -- Frechet expects one continuous curve; disconnected imports require manual duplicate review.
                  and jsonb_array_length(COALESCE(recording_break_before_point_indexes, '[]'::jsonb)) = 0
            )
            select\s
              gps_track_id_duplicate
            from (
            	SELECT
            	    gtd.id as id_duplicate ,
            	    gtd.gps_track_id as gps_track_id_duplicate,
            	    orig_track.id as id_orig_track,
            	    orig_track.gps_track_id as orig_track_gps_track_id,
                    ST_FrechetDistance(ST_Transform(ST_Force2D(gtd.track), 3857), orig_track.orig_track) as similarity,
                    ST_Transform(ST_Force2D(track), 3857) as track_duplicate_simplified,
            	    orig_track.orig_track
            	FROM gps_track_data gtd
            	         INNER JOIN orig_track ON 1=1
            	         INNER JOIN gps_track gt on gt.id = gtd.gps_track_id
            	where 1=1
            	  and gtd.precision_in_meter = 100
              and jsonb_array_length(COALESCE(gtd.recording_break_before_point_indexes, '[]'::jsonb)) = 0
              and gt.duplicate_status <> 'FAILED'
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
                    CASE WHEN NOT timing_unreliable THEN COALESCE(track_duration_in_motion_secs, ABS(EXTRACT(EPOCH FROM (end_date - start_date)))) END AS track_duration,
                    -- 1. Calculate the main grouping string early
                    to_char(start_date, cast(:group_by_date_format AS text)) AS group_by_col,
                    -- 2. Calculate the substring (everything after the first '-') early
                    split_part(to_char(start_date, cast(:group_by_date_format AS text)), '-', 2) AS sub_group_col,
                    :group_by_date_format AS group_by_date_format
                FROM gps_track_v gt
                -- Keep UI statistics aligned with SmartBaseFilter while duplicate detection waits for indexing.
                WHERE duplicate_status IN ('UNIQUE', 'NOT_CHECKED_YET')
                    AND load_status = 'SUCCESS'
                    AND track_source = 'IMPORTED'
                    AND (cast(:filterIds AS bigint[]) IS NULL OR id = ANY(:filterIds))
                    AND gt.statistics_exclusion_reason IS NULL
                    AND gt.review_excluded = false
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
                sum(track_length_in_meter)::double precision AS trackLengthInMeterSum,
                trunc(percentile_disc(0.5) WITHIN GROUP (ORDER BY track_length_in_meter)) AS trackLengthInMeterMed,
                trunc(avg(track_length_in_meter)) AS trackLengthInMeterAvg,
                round((percentile_disc(0.5) WITHIN GROUP (ORDER BY median_distance_between_points))::numeric, 1) AS distanceBetweenGpsPointsMed,
                sum(COALESCE(CASE WHEN NOT timing_unreliable THEN energy_net_total_wh END, 0))::double precision AS energyNetTotalWhSum,
                round((percentile_disc(0.5) WITHIN GROUP (ORDER BY CASE WHEN NOT timing_unreliable THEN COALESCE(power_watts_avg, 0) END))::numeric, 1) AS powerWattsAvgMed,
                round((percentile_disc(0.5) WITHIN GROUP (ORDER BY CASE WHEN NOT timing_unreliable THEN COALESCE(normalized_power_watts, 0) END))::numeric, 1) AS normalizedPowerMed,
                round(avg(CASE
                    WHEN COALESCE(CASE WHEN NOT timing_unreliable THEN normalized_power_watts END, 0)::double precision > 0 AND cast(:threshold_power AS double precision) > 0
                    THEN normalized_power_watts::double precision / cast(:threshold_power AS double precision)
                END)::numeric, 3) AS intensityIndexAvg,
                round(avg(CASE
                    WHEN COALESCE(CASE WHEN NOT timing_unreliable THEN normalized_power_watts END, 0)::double precision > 0
                        AND COALESCE(track_duration_in_motion_secs, 0)::double precision > 0
                        AND cast(:threshold_power AS double precision) > 0
                    THEN POWER(normalized_power_watts::double precision / cast(:threshold_power AS double precision), 2)
                         * (track_duration_in_motion_secs::double precision / 3600.0) * 100
                END)::numeric, 1) AS trainingLoadPerRideAvg,
                round(avg(CASE WHEN NOT timing_unreliable AND exploration_status = 'CALCULATED' AND exploration_score IS NOT NULL THEN exploration_score END)::numeric, 3) AS explorationScoreAvg
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
            WITH q AS (
                SELECT
                    start_date,
                    GREATEST(0, COALESCE(track_length_in_meter, 0))::double precision AS distance_m,
                    (CASE WHEN timing_unreliable THEN 0 ELSE GREATEST(0, COALESCE(track_duration_in_motion_secs, EXTRACT(EPOCH FROM (end_date - start_date)), 0)) END * 1000.0)::double precision AS duration_ms,
                    GREATEST(0, COALESCE(ascent_in_meter, 0))::double precision AS ascent_m,
                    CASE WHEN timing_unreliable THEN 0 ELSE GREATEST(0, COALESCE(CASE WHEN NOT timing_unreliable THEN energy_net_total_wh END, 0)) END::double precision AS energy_wh
                FROM gps_track
                WHERE id = ANY(:filterIds)
                  AND statistics_exclusion_reason IS NULL
                  AND review_excluded = false
            )
            SELECT
                COUNT(*)::bigint AS trackCount,
                COALESCE(SUM(distance_m), 0)::double precision AS distanceM,
                COALESCE(SUM(duration_ms), 0)::double precision AS durationMs,
                COALESCE(SUM(ascent_m), 0)::double precision AS ascentM,
                COALESCE(SUM(energy_wh), 0)::double precision AS energyWh,
                MIN(start_date) AS oldestStart,
                MAX(start_date) AS newestStart
            FROM q
            """)
    GpsTrackOverviewSummary getTrackOverviewSummary(@Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
            WITH q AS (
                SELECT
                    COALESCE(activity_type, 'UNKNOWN') AS activity_type,
                    GREATEST(0, COALESCE(track_length_in_meter, 0))::double precision AS distance_m,
                    (CASE WHEN timing_unreliable THEN 0 ELSE GREATEST(0, COALESCE(track_duration_in_motion_secs, EXTRACT(EPOCH FROM (end_date - start_date)), 0)) END * 1000.0)::double precision AS duration_ms,
                    CASE WHEN timing_unreliable THEN 0 ELSE GREATEST(0, COALESCE(CASE WHEN NOT timing_unreliable THEN energy_net_total_wh END, 0)) END::double precision AS energy_wh
                FROM gps_track
                WHERE id = ANY(:filterIds)
                  AND statistics_exclusion_reason IS NULL
                  AND review_excluded = false
            )
            SELECT
                activity_type AS activityType,
                COUNT(*)::bigint AS trackCount,
                COALESCE(SUM(distance_m), 0)::double precision AS distanceM,
                COALESCE(SUM(duration_ms), 0)::double precision AS durationMs,
                COALESCE(SUM(energy_wh), 0)::double precision AS energyWh
            FROM q
            GROUP BY activity_type
            ORDER BY trackCount DESC, distanceM DESC, activityType ASC
            """)
    List<GpsTrackOverviewActivity> getTrackOverviewActivityBreakdown(@Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
            SELECT
                COUNT(*) FILTER (
                    WHERE highlight_exclusion_reason IS NOT NULL
                       OR statistics_exclusion_reason IS NOT NULL
                )::bigint AS highlightExcludedTrackCount,
                COUNT(*) FILTER (
                    WHERE statistics_exclusion_reason IS NOT NULL
                )::bigint AS statisticsExcludedTrackCount,
                COUNT(*) FILTER (WHERE review_excluded)::bigint AS reviewWithheldTrackCount,
                COUNT(DISTINCT file_id) FILTER (WHERE EXISTS(SELECT 1 FROM source_issue si
                    WHERE si.source_id=gps_track.file_id AND si.resolved_at IS NULL AND si.severity='REVIEW'
                      AND NOT EXISTS(SELECT 1 FROM source_issue_acknowledgement a WHERE a.issue_id=si.id AND a.account_key=:account)))::bigint AS reviewPendingSourceCount
            FROM gps_track
            WHERE id = ANY(:filterIds)
            """)
    GpsTrackOverviewExclusions getTrackOverviewExclusions(@Param("filterIds") Long[] filterIds, @Param("account") String account);

    @Query(nativeQuery = true, value = TRACK_OVERVIEW_CTE_COLUMNS + """
                    ,
                    GREATEST(0, COALESCE(CASE WHEN NOT timing_unreliable THEN speed_in_kmh_30s_max END, 0))::double precision AS speed_30_kmh,
                    GREATEST(0, COALESCE(CASE WHEN NOT timing_unreliable THEN elevation_gain_per_hour_30s_max END, 0))::double precision AS ascent_rate_mh,
                    GREATEST(0, COALESCE(CASE WHEN NOT timing_unreliable THEN power_watts_30s_max END, 0))::double precision AS power_30_w
                FROM gps_track
                WHERE id = ANY(:filterIds)
                  AND highlight_exclusion_reason IS NULL
                  AND statistics_exclusion_reason IS NULL
                  AND review_excluded = false
            ),
            metric_rows AS (
                SELECT 'longest-distance' AS rowKey, id AS trackId, distance_m AS value
                FROM q WHERE distance_m > 0
                UNION ALL
                SELECT 'longest-duration' AS rowKey, id AS trackId, duration_ms AS value
                FROM q WHERE duration_ms > 0
                UNION ALL
                SELECT 'biggest-ascent' AS rowKey, id AS trackId, ascent_m AS value
                FROM q WHERE ascent_m > 0
                UNION ALL
                SELECT 'quickest-ascent' AS rowKey, id AS trackId, ascent_rate_mh AS value
                FROM q WHERE ascent_rate_mh > 0
                UNION ALL
                SELECT 'most-energy' AS rowKey, id AS trackId, energy_wh AS value
                FROM q WHERE energy_wh > 0
                UNION ALL
                SELECT 'fastest-speed' AS rowKey, id AS trackId, speed_30_kmh AS value
                FROM q WHERE speed_30_kmh > 0
                UNION ALL
                SELECT 'peak-power' AS rowKey, id AS trackId, power_30_w AS value
                FROM q WHERE power_30_w > 0
            ),
            ranked AS (
                SELECT
                    rowKey,
                    trackId,
                    value,
                    ROW_NUMBER() OVER (
                        PARTITION BY rowKey
                        ORDER BY value DESC, trackId DESC
                    )::integer AS sortOrder
                FROM metric_rows
            )
            SELECT sortOrder, rowKey, trackId, value
            FROM ranked
            WHERE sortOrder <= :limit
            ORDER BY rowKey ASC, sortOrder ASC
            """)
    List<GpsTrackOverviewTrackRow> getTrackOverviewTrackRankings(@Param("filterIds") Long[] filterIds, @Param("limit") int limit);

    @Query(nativeQuery = true, value = """
            WITH q AS (
                SELECT
                    id,
                    start_date
                FROM gps_track
                WHERE id = ANY(:filterIds)
                  AND start_date IS NOT NULL
                  AND statistics_exclusion_reason IS NULL
                  AND review_excluded = false
            )
            SELECT
                ROW_NUMBER() OVER (ORDER BY start_date DESC, id DESC)::integer AS sortOrder,
                'recent' AS rowKey,
                id AS trackId,
                0::double precision AS value
            FROM q
            ORDER BY start_date DESC, id DESC
            LIMIT 5
            """)
    List<GpsTrackOverviewTrackRow> getTrackOverviewRecentActivities(@Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
            WITH q AS (
                SELECT
                    start_date,
                    GREATEST(0, COALESCE(track_length_in_meter, 0))::double precision AS distance_m,
                    (CASE WHEN timing_unreliable THEN 0 ELSE GREATEST(0, COALESCE(track_duration_in_motion_secs, EXTRACT(EPOCH FROM (end_date - start_date)), 0)) END * 1000.0)::double precision AS duration_ms
                FROM gps_track
                WHERE id = ANY(:filterIds)
                  AND start_date IS NOT NULL
                  AND statistics_exclusion_reason IS NULL
                  AND review_excluded = false
            ),
            weekdays AS (
                SELECT * FROM (VALUES
                    (1, 'Monday'),
                    (2, 'Tuesday'),
                    (3, 'Wednesday'),
                    (4, 'Thursday'),
                    (5, 'Friday'),
                    (6, 'Saturday'),
                    (7, 'Sunday')
                ) AS w(period_num, label)
            ),
            weekday_grouped AS (
                SELECT EXTRACT(ISODOW FROM start_date)::integer AS period_num,
                       COUNT(*)::bigint AS trackCount,
                       COALESCE(SUM(distance_m), 0)::double precision AS distanceM,
                       COALESCE(SUM(duration_ms), 0)::double precision AS durationMs
                FROM q
                GROUP BY EXTRACT(ISODOW FROM start_date)
            ),
            grouped AS (
                SELECT 10 AS periodOrder, 'day' AS periodType, TO_CHAR(start_date, 'YYYY-MM-DD') AS periodKey,
                       TO_CHAR(start_date, 'YYYY-MM-DD') AS label, COUNT(*)::bigint AS trackCount,
                       COALESCE(SUM(distance_m), 0)::double precision AS distanceM,
                       COALESCE(SUM(duration_ms), 0)::double precision AS durationMs
                FROM q GROUP BY TO_CHAR(start_date, 'YYYY-MM-DD')
                UNION ALL
                SELECT 20 AS periodOrder, 'week' AS periodType, TO_CHAR(start_date, 'IYYY-"W"IW') AS periodKey,
                       TO_CHAR(start_date, 'IYYY-"W"IW') AS label, COUNT(*)::bigint AS trackCount,
                       COALESCE(SUM(distance_m), 0)::double precision AS distanceM,
                       COALESCE(SUM(duration_ms), 0)::double precision AS durationMs
                FROM q GROUP BY TO_CHAR(start_date, 'IYYY-"W"IW')
                UNION ALL
                SELECT 30 AS periodOrder, 'month' AS periodType, TO_CHAR(start_date, 'YYYY-MM') AS periodKey,
                       TO_CHAR(start_date, 'YYYY-MM') AS label, COUNT(*)::bigint AS trackCount,
                       COALESCE(SUM(distance_m), 0)::double precision AS distanceM,
                       COALESCE(SUM(duration_ms), 0)::double precision AS durationMs
                FROM q GROUP BY TO_CHAR(start_date, 'YYYY-MM')
                UNION ALL
                SELECT 40 AS periodOrder, 'weekday' AS periodType, weekdays.period_num::text AS periodKey,
                       weekdays.label AS label, COALESCE(weekday_grouped.trackCount, 0)::bigint AS trackCount,
                       COALESCE(weekday_grouped.distanceM, 0)::double precision AS distanceM,
                       COALESCE(weekday_grouped.durationMs, 0)::double precision AS durationMs
                FROM weekdays
                LEFT JOIN weekday_grouped ON weekday_grouped.period_num = weekdays.period_num
            ),
            ranked AS (
                SELECT *, ROW_NUMBER() OVER (
                    PARTITION BY periodType
                    ORDER BY durationMs DESC, trackCount DESC, distanceM DESC, label ASC
                ) AS rn
                FROM grouped
            )
            SELECT rn::integer AS sortOrder, periodType, periodKey, label, trackCount, distanceM, durationMs
            FROM ranked
            WHERE rn <= :limit
            ORDER BY periodOrder ASC, rn ASC
            """)
    List<GpsTrackOverviewPeriod> getTrackOverviewPeriodDistributions(@Param("filterIds") Long[] filterIds, @Param("limit") int limit);

    @Query(nativeQuery = true, value = TRACK_OVERVIEW_CTE_COLUMNS + """
                FROM gps_track
                WHERE id = ANY(:filterIds)
                  AND start_date IS NOT NULL
                  AND highlight_exclusion_reason IS NULL
                  AND statistics_exclusion_reason IS NULL
                  AND review_excluded = false
            ),
            rows AS (
                (SELECT 10 AS sortOrder, 'first-activity' AS rowKey, id AS trackId, 0::double precision AS value
                 FROM q ORDER BY start_date ASC, id ASC LIMIT 1)
                UNION ALL
                (SELECT 20 AS sortOrder, 'latest-activity' AS rowKey, id AS trackId, 0::double precision AS value
                 FROM q ORDER BY start_date DESC, id DESC LIMIT 1)
            )
            SELECT sortOrder, rowKey, trackId, value FROM rows ORDER BY sortOrder ASC
            """)
    List<GpsTrackOverviewTrackRow> getTrackOverviewActivityBounds(@Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
            WITH target AS (
                SELECT COALESCE(duplicate_of, id) AS primary_id
                FROM gps_track WHERE id = :gps_track_id
            )
            SELECT member.id
            FROM gps_track member
            JOIN target ON member.id = target.primary_id OR member.duplicate_of = target.primary_id
            WHERE member.id <> :gps_track_id
            ORDER BY member.id
            """)
    List<Long> getDuplicatesForGpsTrackId(@Param("gps_track_id") Long gpsTrackId);

    /**
     * Direct references to clear when deleting a track, not other members of its duplicate group.
     */
    @Query(nativeQuery = true, value = """
            SELECT id FROM gps_track WHERE duplicate_of = :gps_track_id ORDER BY id
            """)
    List<Long> getDuplicateReferenceIdsForGpsTrackId(@Param("gps_track_id") Long gpsTrackId);

    @Query(nativeQuery = true, value = """
            select gt1.id from gps_track gt1 where 1=1
                and gt1.start_date is not null
                -- Keep time navigation aligned with the visible SmartBaseFilter result.
                and gt1.duplicate_status IN ('UNIQUE', 'NOT_CHECKED_YET') and gt1.load_status='SUCCESS'
                and gt1.track_source='IMPORTED'
                and gt1.id = ANY(:filterIds)
                and gt1.start_date > (select gt2.start_date from gps_track gt2 where gt2.id = :id )
            order by gt1.start_date asc
            limit 50
            """)
    List<Long> getRelatedTrackIdsNext(@Param("id") Long gpsTrackId, @Param("filterIds") Long[] filterIds);

    @Query(nativeQuery = true, value = """
            select gt1.id from gps_track gt1 where 1=1
                and gt1.start_date is not null
                -- Keep time navigation aligned with the visible SmartBaseFilter result.
                and gt1.duplicate_status IN ('UNIQUE', 'NOT_CHECKED_YET') and gt1.load_status='SUCCESS'
                and gt1.track_source='IMPORTED'
                and gt1.id = ANY(:filterIds)
                and gt1.start_date < (select gt2.start_date from gps_track gt2 where gt2.id = :id )
            order by gt1.start_date desc
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
     * JPQL startup probe only. This intentionally exercises normal IN-list
     * expansion with a bounded list size; production large-ID lookups must use
     * native array binding instead.
     */
    @Query("SELECT t.id FROM GpsTrack t WHERE t.id IN :ids")
    List<Long> findTrackIdsByIdsJpqlStartupProbe(@Param("ids") List<Long> ids);

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

    /**
     * Exploration writes only its result after calculation, leaving other jobs' fields untouched.
     */
    @Modifying
    @Query("""
            UPDATE GpsTrack t
               SET t.explorationStatus = :status,
                   t.explorationScore = :score,
                   t.explorationCalcDate = :calculationDate
             WHERE t.id = :id
            """)
    int updateExplorationResult(
            @Param("id") Long id,
            @Param("status") GpsTrack.EXPLORATION_STATUS status,
            @Param("score") Double score,
            @Param("calculationDate") Date calculationDate);

    @Modifying
    @Query("""
            UPDATE GpsTrack t
               SET t.explorationStatus = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.EXPLORATION_STATUS.NEEDS_RECALCULATION,
                   t.explorationCalcDate = :calculationDate
             WHERE t.id = :id
            """)
    int markExplorationForRetry(@Param("id") Long id, @Param("calculationDate") Date calculationDate);

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

    @Query("SELECT COUNT(t) FROM GpsTrack t WHERE t.activityTypeSource IS NOT NULL AND t.activityTypeSource <> com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE_SOURCE.FAILED AND t.loadStatus = 'SUCCESS' AND t.trackSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.TRACK_SOURCE.IMPORTED")
    long countActivityTypeDone();

    @Query("SELECT COUNT(t) FROM GpsTrack t WHERE t.activityTypeSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE_SOURCE.FAILED AND t.loadStatus = 'SUCCESS' AND t.trackSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.TRACK_SOURCE.IMPORTED")
    long countActivityTypeFailed();

    @Query("SELECT COUNT(t) FROM GpsTrack t WHERE t.activityTypeSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE_SOURCE.FAILED AND t.loadStatus = 'SUCCESS' AND t.trackSource = com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.TRACK_SOURCE.IMPORTED AND t.activityTypeSourceDetails LIKE :reasonPrefix")
    long countActivityTypeUnavailable(String reasonPrefix);

    interface ActivityTypeSummaryCounts {
        long getPending();
        long getReady();
        long getWaiting();

        long getDone();

        long getFailed();

        long getUnavailable();
    }

    // All counts share one database snapshot while classification moves tracks between states.
    @Query(value = """
            SELECT count(*) FILTER (WHERE activity_type_source IS NULL) AS pending,
                   count(*) FILTER (WHERE activity_type_source IS NULL AND duplicate_status IS NOT NULL
                                    AND duplicate_status <> 'NOT_CHECKED_YET') AS ready,
                   count(*) FILTER (WHERE activity_type_source IS NULL AND
                                    (duplicate_status IS NULL OR duplicate_status = 'NOT_CHECKED_YET')) AS waiting,
                   count(*) FILTER (WHERE activity_type_source IS NOT NULL AND activity_type_source <> 'FAILED') AS done,
                   count(*) FILTER (WHERE activity_type_source = 'FAILED') AS failed,
                   count(*) FILTER (WHERE activity_type_source = 'FAILED'
                                    AND activity_type_source_details LIKE :reasonPrefix) AS unavailable
              FROM gps_track
             WHERE load_status = 'SUCCESS' AND track_source = 'IMPORTED'
            """, nativeQuery = true)
    ActivityTypeSummaryCounts countActivityTypeSummary(@Param("reasonPrefix") String reasonPrefix);

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
    @Query(value = "SELECT id, version FROM gps_track WHERE id = ANY(:ids)", nativeQuery = true)
    List<Object[]> findVersionsByIds(@Param("ids") Long[] ids);

    /** Complete archive membership for cache reconciliation, without user filters. */
    @Query(value = "SELECT id, version FROM gps_track", nativeQuery = true)
    List<Object[]> findAllTrackVersions();

    default Map<Long, Long> findVersionMapByIds(List<Long> ids) {
        Map<Long, Long> versions = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return versions;
        }
        for (Object[] row : findVersionsByIds(ids.toArray(Long[]::new))) {
            versions.put((Long) row[0], (Long) row[1]);
        }
        return versions;
    }

}
