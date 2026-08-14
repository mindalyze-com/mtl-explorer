package com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GeoCircle;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GeoPolygon;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GeoRectangle;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonPropertyOrder({
        "stringParams",
        "dateTimeParams",
        "geoCircles",
        "geoRectangles",
        "geoPolygons",
        "resultGroupSelection",
        "trackIds"
})
public class FilterParamsRequest {

    private Map<String, String> stringParams;
    private Map<String, String> dateTimeParams;
    private Map<String, GeoCircle> geoCircles;
    private Map<String, GeoRectangle> geoRectangles;
    private Map<String, GeoPolygon> geoPolygons;
    private FilterResultGroupSelection resultGroupSelection;

    /**
     * Optional hydration slice for /tracks/get-simplified mode=full.
     * Filter params still define the full matching set; when this is present,
     * the response only includes the requested IDs that are also in that set.
     */
    private List<Long> trackIds;
}
