package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "grouping",
        "scope",
        "trackIds"
})
public record MediaTrendRequest(
        MediaTrendGrouping grouping,
        MediaTrendScope scope,
        List<Long> trackIds
) {
}
