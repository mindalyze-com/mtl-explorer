package com.x8ing.mtl.server.mtlserver.db.repository.gps;


import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface GpsTrackDataRepository extends JpaRepository<GpsTrackData, Long> {

    @Query(nativeQuery = true, value = """
            select gtd.*
            from
            gps_track_data gtd
            inner join gps_track gt on gt.id=gtd.gps_track_id
            where gt.load_status='SUCCESS' and start_date is not null and gtd.precision_in_meter =:precision_in_meter
            """)
    List<GpsTrackData> findAllWithLoadStatusSuccessAndPrecision(@Param(value = "precision_in_meter") BigDecimal precisionInMeter);

    @Query(nativeQuery = true, value = """
            select gtd.*
            from
            gps_track_data gtd
            inner join gps_track gt on gt.id=gtd.gps_track_id
            where gt.load_status='SUCCESS'
                and start_date is not null
                and gtd.precision_in_meter = :precision_in_meter
                and gtd.gps_track_id in (:track_ids)
            """)
    List<GpsTrackData> findAllWithLoadStatusSuccessAndPrecisionAndTrackIds(
            @Param(value = "precision_in_meter") BigDecimal precisionInMeter,
            @Param(value = "track_ids") List<Long> trackIds);

    /**
     * only consider simplified or RAW_OUTLIER_CLEANED (ignore the RAW)
     */
    @Query(nativeQuery = true, value = """
            select *
            from
            gps_track_data
            where 1=1 
                and gps_track_id = :gps_track_id 
                and precision_in_meter = :precision_in_meter
                and track_type != 'RAW'
            """)
    GpsTrackData findFirstByGpsTrackIdAndPrecisionInMeter(
            @Param("gps_track_id") Long gpsTrackId,
            @Param("precision_in_meter") BigDecimal precisionInMeter);

    @Query(nativeQuery = true, value = """
            select id
            from
            gps_track_data
            where 1=1 
                and gps_track_id = :gps_track_id 
                and precision_in_meter = :precision_in_meter
                and track_type != 'RAW'
            """)
    Long findFirstByGpsTrackIdAndPrecisionInMeterOnlyIds(
            @Param("gps_track_id") Long gpsTrackId,
            @Param("precision_in_meter") BigDecimal precisionInMeter);

    /**
     * Look up a track-data variant by its explicit {@link GpsTrackData.TRACK_TYPE}.
     * <p>
     * Use this for metric/calculation consumers that need a deterministic variant
     * (typically {@code RAW_OUTLIER_CLEANED}) without depending on the precision
     * value or on the {@code != 'RAW'} side-effect of the precision-based finders.
     */
    @Query(nativeQuery = true, value = """
            select *
            from
            gps_track_data
            where 1=1
                and gps_track_id = :gps_track_id
                and track_type = :track_type
            """)
    GpsTrackData findFirstByGpsTrackIdAndTrackType(
            @Param("gps_track_id") Long gpsTrackId,
            @Param("track_type") String trackType);

    /**
     * Id-only variant of {@link #findFirstByGpsTrackIdAndTrackType(Long, String)}.
     */
    @Query(nativeQuery = true, value = """
            select id
            from
            gps_track_data
            where 1=1
                and gps_track_id = :gps_track_id
                and track_type = :track_type
            """)
    Long findFirstByGpsTrackIdAndTrackTypeOnlyIds(
            @Param("gps_track_id") Long gpsTrackId,
            @Param("track_type") String trackType);


    /**
     * All track-data variants (RAW, RAW_OUTLIER_CLEANED, SIMPLIFIED@*) for a track.
     * Used by energy-recalc flows that need to re-populate per-point energy on every
     * stored variant after a track attribute changes (e.g. activity type).
     */
    @Query(nativeQuery = true, value = """
            select * from gps_track_data
            where gps_track_id = :gps_track_id
            """)
    List<GpsTrackData> findAllByGpsTrackId(@Param("gps_track_id") Long gpsTrackId);


    @Transactional
    void deleteByGpsTrackId(Long gpsTrackId);

}
