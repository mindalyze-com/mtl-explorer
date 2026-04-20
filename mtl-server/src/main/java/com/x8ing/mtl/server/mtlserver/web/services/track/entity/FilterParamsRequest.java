package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import lombok.Data;

import java.util.Map;

@Data
public class FilterParamsRequest {

    private Map<String, String> stringParams;
    private Map<String, String> dateTimeParams;
    private Map<String, GeoCircle> geoCircles;
    private Map<String, GeoRectangle> geoRectangles;
    private Map<String, GeoPolygon> geoPolygons;
}
