package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import lombok.Data;

@Data
public class GpsTrackResponse {

    private GpsTrack gpsTrack;

    private QueryResult.QueryResultEntry filterMapping;

    public GpsTrackResponse(GpsTrack gpsTrack) {
        this.gpsTrack = gpsTrack;
    }

    public GpsTrackResponse(GpsTrack gpsTrack, QueryResult.QueryResultEntry filterMapping) {
        this.gpsTrack = gpsTrack;
        this.filterMapping = filterMapping;
    }

}
