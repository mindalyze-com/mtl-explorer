package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "lat",
        "lng",
        "radiusM"
})
public class GeoCircle {

    private Double lat;
    private Double lng;
    private Double radiusM;
}
