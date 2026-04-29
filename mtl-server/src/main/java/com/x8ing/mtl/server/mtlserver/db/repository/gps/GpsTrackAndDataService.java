package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GpsTrackAndDataService {

    private final GpsTrackRepository gpsTrackRepository;

    private final GpsTrackDataRepository gpsTrackDataRepository;

    public GpsTrackAndDataService(GpsTrackRepository gpsTrackRepository, GpsTrackDataRepository gpsTrackDataRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataRepository = gpsTrackDataRepository;
    }

    public List<GpsTrack> findAllGpsTracksWithData(BigDecimal precisionInMeter, List<Long> trackIds) {
        long t0 = System.currentTimeMillis();
        List<GpsTrack> ret = new ArrayList<>();
        if (trackIds == null || trackIds.isEmpty()) {
            log.info("Loaded all tracks: empty ID list for precision={}", precisionInMeter);
            return ret;
        }

        List<GpsTrack> gpsTracks = gpsTrackRepository.findAllById(trackIds);
        List<GpsTrackData> gpsTrackData = gpsTrackDataRepository.findAllWithLoadStatusSuccessAndPrecisionAndTrackIds(precisionInMeter, trackIds);
        long tDataLoaded = System.currentTimeMillis();

        if (gpsTrackData != null) {

            final Map<Long, GpsTrackData> gpsTrackDataByGpsTrackId = gpsTrackData.stream().collect(Collectors.toMap(GpsTrackData::getGpsTrackId, data -> data));

            gpsTracks.forEach(gpsTrack -> {
                GpsTrackData trackData = gpsTrackDataByGpsTrackId.get(gpsTrack.getId());
                if (trackData != null) {
                    gpsTrack.getGpsTracksData().add(trackData);
                }
                ret.add(gpsTrack);
                gpsTrack.setLoadMessages(null); // ignore to save bandwidth
            });
        }

        long tJavaMerge = System.currentTimeMillis();

        log.info("Loaded all tracks: dtData={}, dtJavaMerge={}, dTotal={}", tDataLoaded - t0, tJavaMerge - tDataLoaded, tJavaMerge - t0);

        return ret;
    }

}
