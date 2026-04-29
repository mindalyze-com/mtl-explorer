package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FilterParamsRequest {

    private Map<String, String> stringParams;
    private Map<String, String> dateTimeParams;
    private Map<String, GeoCircle> geoCircles;
    private Map<String, GeoRectangle> geoRectangles;
    private Map<String, GeoPolygon> geoPolygons;

    /**
     * Optional hydration slice for /tracks/get-simplified mode=full.
     * Filter params still define the full matching set; when this is present,
     * the response only includes the requested IDs that are also in that set.
     */
    private List<Long> trackIds;
}
