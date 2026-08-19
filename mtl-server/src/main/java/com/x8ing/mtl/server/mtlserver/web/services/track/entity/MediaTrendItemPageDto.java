package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "items",
        "page",
        "pageSize",
        "totalItems",
        "totalPages"
})
public record MediaTrendItemPageDto(
        List<MediaTrendItemDto> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
}
