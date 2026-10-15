package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface GpsTrackEventRepository extends JpaRepository<GpsTrackEvent, Long> {

    List<GpsTrackEvent> findAllByGpsTrackIdOrderByStartPointIndexAsc(Long gpsTrackId);

    interface SegmentQualificationGap {
        Long getTrackId();

        Integer getEndPointIndex();
    }

    @Query("""
            select event.gpsTrackId as trackId, event.endPointIndex as endPointIndex
            from GpsTrackEvent event
            where event.gpsTrackId in :trackIds and event.eventType = :eventType
            order by event.gpsTrackId, event.endPointIndex
            """)
    List<SegmentQualificationGap> findSegmentQualificationGaps(@Param("trackIds") List<Long> trackIds,
                                                               @Param("eventType") GpsTrackEvent.EVENT_TYPE eventType);

    @Transactional
    void deleteByGpsTrackId(Long gpsTrackId);

    @Transactional
    void deleteByGpsTrackIdAndEventTypeAndSource(
            Long gpsTrackId,
            GpsTrackEvent.EVENT_TYPE eventType,
            GpsTrackEvent.SOURCE source);
}
