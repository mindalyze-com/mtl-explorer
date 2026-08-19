package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "grouping",
        "scope",
        "bucketKey",
        "kind",
        "trackIds",
        "page",
        "pageSize"
})
public record MediaTrendItemsRequest(
        MediaTrendGrouping grouping,
        MediaTrendScope scope,
        String bucketKey,
        MediaTrendKindFilter kind,
        List<Long> trackIds,
        Integer page,
        Integer pageSize
) {
}
