package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "longitude",
        "latitude",
        "elevation",
        "timestamp",
        "distanceToPoint"
})
public interface IWayPointWithDistance {

    Double getLongitude();

    Double getLatitude();

    Double getElevation();

    java.util.Date getTimestamp();

    Double getDistanceToPoint();
}
