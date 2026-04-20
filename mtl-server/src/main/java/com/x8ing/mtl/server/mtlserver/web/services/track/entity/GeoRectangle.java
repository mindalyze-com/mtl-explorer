package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import lombok.Data;

@Data
public class GeoRectangle {

    private Double minLat;
    private Double maxLat;
    private Double minLng;
    private Double maxLng;
}
