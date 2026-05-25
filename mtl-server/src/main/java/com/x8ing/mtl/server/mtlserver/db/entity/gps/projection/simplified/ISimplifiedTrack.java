package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.locationtech.jts.geom.LineString;

@JsonPropertyOrder({
        "gpsTrackId",
        "gpsTrackDataId",
        "trackType",
        "lineString"
})
public interface ISimplifiedTrack {

    Long getGpsTrackId();

    Long getGpsTrackDataId();

    String getTrackType();

    LineString getLineString();


}
