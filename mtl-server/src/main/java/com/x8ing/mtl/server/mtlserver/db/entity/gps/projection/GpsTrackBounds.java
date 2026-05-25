package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "minLng",
        "minLat",
        "maxLng",
        "maxLat"
})
public interface GpsTrackBounds {

    Double getMinLng();

    Double getMinLat();

    Double getMaxLng();

    Double getMaxLat();
}
