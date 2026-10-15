package com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GeoCircle;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GeoPolygon;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GeoRectangle;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonPropertyOrder({
        "stringParams",
        "dateTimeParams",
        "timeZone",
        "geoCircles",
        "geoRectangles",
        "geoPolygons",
        "trackViewScope",
        "resultGroupSelection",
        "trackIds"
})
public class FilterParamsRequest {

    private Map<String, String> stringParams;

    @Schema(description = "Date-time bounds as ISO 8601 timestamps with an explicit offset, e.g. 2025-06-15T12:00:00+02:00 or 2025-06-15T10:00:00Z. Null or blank bounds are omitted.")
    private Map<String, String> dateTimeParams;

    @Schema(description = "Browser IANA timezone for calendar grouping and year bounds, e.g. Europe/Zurich. Defaults to UTC when omitted.")
    private String timeZone;

    private Map<String, GeoCircle> geoCircles;
    private Map<String, GeoRectangle> geoRectangles;
    private Map<String, GeoPolygon> geoPolygons;
    private TrackViewScope trackViewScope = TrackViewScope.ALL;
    private FilterResultGroupSelection resultGroupSelection;

    /**
     * Optional hydration slice for /tracks/get-simplified mode=full.
     * Filter params still define the full matching set; when this is present,
     * the response only includes the requested IDs that are also in that set.
     */
    private List<Long> trackIds;
}
