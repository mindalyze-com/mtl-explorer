package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Repository
public interface GpsTrackDataPointRepository extends JpaRepository<GpsTrackDataPoint, Long> {

    int ENERGY_UPDATE_CHUNK_SIZE = 500;

    @Transactional
    void deleteByGpsTrackDataId(Long gpsTrackDataId);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "delete from gps_track_data_points gdp where gdp.gps_track_data_id in ( select id from gps_track_data gtd where gps_track_id = :gps_track_id)")
    void deleteAllByGpsTrackId(@Param("gps_track_id") Long gpsTrackId);

    List<GpsTrackDataPoint> findAllByGpsTrackDataIdOrderByPointIndexAsc(Long gpsTrackDataId);

    interface SegmentQualificationSize {
        Long getTrackId();

        long getPointCount();
    }

    @Query("""
            select data.gpsTrackId as trackId, count(point) as pointCount
            from GpsTrackData data join GpsTrackDataPoint point on point.gpsTrackDataId = data.id
            where data.gpsTrackId in :trackIds and data.trackType = :trackType
            group by data.gpsTrackId order by data.gpsTrackId
            """)
    List<SegmentQualificationSize> findSegmentQualificationSizes(@Param("trackIds") List<Long> trackIds,
                                                                 @Param("trackType") GpsTrackData.TRACK_TYPE trackType);

    interface SegmentQualificationPoint {
        Long getTrackId();

        Integer getPointIndex();

        Point getPointLongLat();

        Date getPointTimestamp();

        GpsTrackDataPoint.IntervalStatus getIntervalStatus();
    }

    /**
     * Geometry and continuity only; avoid loading physics, motion and parent-track data for single-zone candidates.
     */
    @Query("""
            select data.gpsTrackId as trackId, point.pointIndex as pointIndex,
                   point.pointLongLat as pointLongLat, point.pointTimestamp as pointTimestamp,
                   point.intervalStatus as intervalStatus
            from GpsTrackData data join GpsTrackDataPoint point on point.gpsTrackDataId = data.id
            where data.gpsTrackId in :trackIds and data.trackType = :trackType
            order by data.gpsTrackId, point.pointIndex
            """)
    List<SegmentQualificationPoint> findSegmentQualificationPoints(@Param("trackIds") List<Long> trackIds,
                                                                   @Param("trackType") GpsTrackData.TRACK_TYPE trackType);

    /**
     * Energy is persisted explicitly; Hibernate must not flush full point entities afterward.
     */
    @Query("select point from GpsTrackDataPoint point where point.gpsTrackDataId = :dataId order by point.pointIndex")
    @QueryHints(@QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true"))
    List<GpsTrackDataPoint> findEnergyInputPoints(@Param("dataId") Long gpsTrackDataId);

    /**
     * All chunks participate in the caller's track transaction, including rollback.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    default void saveEnergyPoints(long trackDataId, List<GpsTrackDataPoint> points) {
        for (int from = 0; from < points.size(); from += ENERGY_UPDATE_CHUNK_SIZE) {
            int count = Math.min(ENERGY_UPDATE_CHUNK_SIZE, points.size() - from);
            Long[] ids = new Long[count];
            Double[] gravitational = new Double[count];
            Double[] drag = new Double[count];
            Double[] rolling = new Double[count];
            Double[] kinetic = new Double[count];
            Double[] total = new Double[count];
            Double[] cumulative = new Double[count];
            Double[] power = new Double[count];
            Boolean[] limited = new Boolean[count];
            for (int i = 0; i < count; i++) {
                var point = points.get(from + i);
                ids[i] = point.getId();
                gravitational[i] = point.getEnergyGravitationalWh();
                drag[i] = point.getEnergyAeroDragWh();
                rolling[i] = point.getEnergyRollingResistanceWh();
                kinetic[i] = point.getEnergyKineticWh();
                total[i] = point.getEnergyTotalWh();
                cumulative[i] = point.getEnergyCumulativeWh();
                power[i] = point.getPowerWatts();
                limited[i] = point.getEnergyLimited();
            }
            int updated = updateEnergyPoints(trackDataId, ids, gravitational, drag, rolling, kinetic,
                    total, cumulative, power, limited);
            if (updated != count) {
                throw new IllegalStateException("Energy point update count mismatch: trackDataId="
                                                + trackDataId + ", expected=" + count + ", updated=" + updated);
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE public.gps_track_data_points point SET
                energy_gravitational_wh = value.gravitational,
                energy_aero_drag_wh = value.drag,
                energy_rolling_resistance_wh = value.rolling,
                energy_kinetic_wh = value.kinetic,
                energy_total_wh = value.total,
                energy_cumulative_wh = value.cumulative,
                power_watts = value.power,
                energy_limited = value.limited
            FROM unnest(CAST(:ids AS bigint[]), CAST(:gravitational AS double precision[]),
                        CAST(:drag AS double precision[]), CAST(:rolling AS double precision[]),
                        CAST(:kinetic AS double precision[]), CAST(:total AS double precision[]),
                        CAST(:cumulative AS double precision[]), CAST(:power AS double precision[]),
                        CAST(:limited AS boolean[]))
                AS value(id, gravitational, drag, rolling, kinetic, total, cumulative, power, limited)
            WHERE point.id = value.id AND point.gps_track_data_id = :dataId
            """)
    int updateEnergyPoints(@Param("dataId") long trackDataId, @Param("ids") Long[] ids,
                           @Param("gravitational") Double[] gravitational, @Param("drag") Double[] drag,
                           @Param("rolling") Double[] rolling, @Param("kinetic") Double[] kinetic,
                           @Param("total") Double[] total, @Param("cumulative") Double[] cumulative,
                           @Param("power") Double[] power, @Param("limited") Boolean[] limited);

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
            select * from gps_track_data_points gtdp
            where gtdp.gps_track_data_id = :gps_track_data_id
              and gtdp.point_long_lat is not null
              and gtdp.point_timestamp >= :track_data_point_time_from
              and gtdp.point_timestamp <= :track_data_point_time_to
            order by gtdp.point_timestamp, gtdp.point_index
            """)
    List<GpsTrackDataPoint> getSubTrackDataByTimestamp(
            @Param("gps_track_data_id") Long gpsTrackDataId,
            @Param("track_data_point_time_from") Date timestampFrom,
            @Param("track_data_point_time_to") Date timestampTo);

    @Query(nativeQuery = true, value = """
            select distinct gt.id from gps_track gt
            inner join gps_track_data gtd on gtd.gps_track_id = gt.id
            inner join gps_track_data_points gtdp on gtdp.gps_track_data_id = gtd.id
            where ST_DWithin(gtdp.point_long_lat, :point, :distance)
            """)
    List<Long> findTrackIdsWithinDistanceOf(@Param("point") Point point, @Param("distance") Double distance);
}
