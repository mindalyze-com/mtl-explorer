package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "bucketKey",
        "label",
        "subGroup",
        "undated",
        "imageCount",
        "videoCount"
})
public record MediaTrendBucketDto(
        String bucketKey,
        String label,
        String subGroup,
        boolean undated,
        long imageCount,
        long videoCount
) {
}
