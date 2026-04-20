package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import lombok.Data;

import java.util.List;

@Data
public class GeoPolygon {

    /**
     * List of coordinate pairs [lng, lat] forming the polygon boundary.
     * The polygon will be auto-closed if the last point doesn't match the first.
     */
    private List<double[]> coordinates;
}
