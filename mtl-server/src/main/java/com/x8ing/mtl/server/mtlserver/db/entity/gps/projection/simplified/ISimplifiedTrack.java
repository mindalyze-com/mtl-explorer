package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified;

import org.locationtech.jts.geom.LineString;

public interface ISimplifiedTrack {

    Long getGpsTrackId();

    Long getGpsTrackDataId();

    String getTrackType();

    LineString getLineString();


}
