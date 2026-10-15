package com.x8ing.mtl.server.mtlserver.web.services.status;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.planner.dto.BRouterStatusDto;
import com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchStatusDto;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerStatusDto;

@JsonPropertyOrder({
        "tileMode",
        "plannerEnabled",
        "map",
        "locationSearch",
        "planner"
})
public record ServicesStatusDto(
        String tileMode,
        boolean plannerEnabled,
        MapServerStatusDto map,
        LocationSearchStatusDto locationSearch,
        BRouterStatusDto planner
) {
}
