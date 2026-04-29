package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface GpsTrackEventRepository extends JpaRepository<GpsTrackEvent, Long> {

    List<GpsTrackEvent> findAllByGpsTrackIdOrderByStartPointIndexAsc(Long gpsTrackId);

    @Transactional
    void deleteByGpsTrackId(Long gpsTrackId);

    @Transactional
    void deleteByGpsTrackIdAndEventTypeAndSource(
            Long gpsTrackId,
            GpsTrackEvent.EVENT_TYPE eventType,
            GpsTrackEvent.SOURCE source);
}
