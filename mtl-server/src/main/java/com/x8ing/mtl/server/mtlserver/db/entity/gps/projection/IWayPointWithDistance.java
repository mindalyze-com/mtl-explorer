package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

public interface IWayPointWithDistance {

    Double getLongitude();

    Double getLatitude();

    Double getElevation();

    java.util.Date getTimestamp();

    Double getDistanceToPoint();
}
