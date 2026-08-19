package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "scope",
        "buckets"
})
public record MediaTrendResponseDto(
        MediaTrendScope scope,
        List<MediaTrendBucketDto> buckets
) {
}
