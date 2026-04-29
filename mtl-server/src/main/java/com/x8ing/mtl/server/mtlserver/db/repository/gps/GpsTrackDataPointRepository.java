package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface GpsTrackDataPointRepository extends JpaRepository<GpsTrackDataPoint, Long> {

    @Transactional
    void deleteByGpsTrackDataId(Long gpsTrackDataId);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "delete from gps_track_data_points gdp where gdp.gps_track_data_id in ( select id from gps_track_data gtd where gps_track_id = :gps_track_id)")
    void deleteAllByGpsTrackId(@Param("gps_track_id") Long gpsTrackId);

    List<GpsTrackDataPoint> findAllByGpsTrackDataIdOrderByPointIndexAsc(Long gpsTrackDataId);

    @Query(nativeQuery = true, value = """
            select gtdp.*  from gps_track_data_points gtdp\s
            inner join gps_track_data gtd on gtd.id = gtdp.gps_track_data_id\s
            inner join gps_track gt on gt.id  = gtd.gps_track_id
            where 1=1\s
            and gt.id = :gps_track_id
            and gtd.precision_in_meter = :precision_in_meter
            and gtd.track_type = :track_type
            and gtdp.point_long_lat is not null
            order by gtdp.gps_track_data_id , gtdp.point_index
            
            """)
    List<GpsTrackDataPoint> getTrackDetailsByGpsTrackIdAndPrecisionAndType(
            @Param("gps_track_id") Long gpsTrackId,
            @Param("precision_in_meter") BigDecimal precisionInMeter,
            @Param("track_type") String trackType
    );

    /**
     * Returns the points of the requested track variant identified by
     * {@link com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData.TRACK_TYPE}
     * (typically {@code RAW_OUTLIER_CLEANED} for metric calculations).
     * <p>
     * Prefer this over {@link #getTrackDetailsByGpsTrackIdAndPrecisionAndType}
     * when the variant is fixed and not driven by a precision value.
     */
    @Query(nativeQuery = true, value = """
            select gtdp.*  from gps_track_data_points gtdp
            inner join gps_track_data gtd on gtd.id = gtdp.gps_track_data_id
            where 1=1
            and gtd.gps_track_id = :gps_track_id
            and gtd.track_type = :track_type
            and gtdp.point_long_lat is not null
            order by gtdp.gps_track_data_id , gtdp.point_index
            """)
    List<GpsTrackDataPoint> getTrackDetailsByGpsTrackIdAndType(
            @Param("gps_track_id") Long gpsTrackId,
            @Param("track_type") String trackType
    );

    /**
     * Variant lookup for {@link com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData.TRACK_TYPE#SIMPLIFIED_FIXED_POINTS}
     * where the discriminator is {@code max_points} rather than
     * {@code precision_in_meter}. A track carries two pre-computed
     * fixed-point variants (e.g. 750 and 1500) — the caller picks one.
     */
    @Query(nativeQuery = true, value = """
            select gtdp.*  from gps_track_data_points gtdp
            inner join gps_track_data gtd on gtd.id = gtdp.gps_track_data_id
            where 1=1
            and gtd.gps_track_id = :gps_track_id
            and gtd.track_type = :track_type
            and gtd.max_points = :max_points
            and gtdp.point_long_lat is not null
            order by gtdp.gps_track_data_id , gtdp.point_index
            """)
    List<GpsTrackDataPoint> getTrackDetailsByGpsTrackIdAndTypeAndMaxPoints(
            @Param("gps_track_id") Long gpsTrackId,
            @Param("track_type") String trackType,
            @Param("max_points") Integer maxPoints
    );

    @Query(nativeQuery = true, value = """
                    select *  from
                        gps_track_data_points gtdp
                    where
                        1 = 1
                        and point_long_lat is not null
                        and point_index >= :track_data_point_index_from
                        and point_index <= :track_data_point_index_to
                        and gps_track_data_id = :gps_track_data_id
                    order by
                        point_index
            """)
    List<GpsTrackDataPoint> getSubTrackData(
            @Param("gps_track_data_id") Long gpsTrackDataId,
            @Param("track_data_point_index_from") Integer pointIndexFrom,
            @Param("track_data_point_index_to") Integer pointIndexTo);

    @Query(nativeQuery = true, value = """
            select distinct gt.id from gps_track gt
            inner join gps_track_data gtd on gtd.gps_track_id = gt.id
            inner join gps_track_data_points gtdp on gtdp.gps_track_data_id = gtd.id
            where ST_DWithin(gtdp.point_long_lat, :point, :distance)
            """)
    List<Long> findTrackIdsWithinDistanceOf(@Param("point") Point point, @Param("distance") Double distance);
}
