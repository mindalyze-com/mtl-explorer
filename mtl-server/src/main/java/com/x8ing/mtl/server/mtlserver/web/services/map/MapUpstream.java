package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "source",
        "baseUrl"
})
public record MapUpstream(MapUpstreamSource source, String baseUrl) {
}
