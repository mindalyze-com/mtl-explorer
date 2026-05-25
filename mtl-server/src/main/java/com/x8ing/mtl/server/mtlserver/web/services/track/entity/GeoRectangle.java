package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "minLat",
        "maxLat",
        "minLng",
        "maxLng"
})
public class GeoRectangle {

    private Double minLat;
    private Double maxLat;
    private Double minLng;
    private Double maxLng;
}
