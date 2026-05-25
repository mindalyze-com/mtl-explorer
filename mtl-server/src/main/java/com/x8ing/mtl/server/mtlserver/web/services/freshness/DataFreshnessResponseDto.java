package com.x8ing.mtl.server.mtlserver.web.services.freshness;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;
import java.util.List;

@JsonPropertyOrder({
        "freshnessToken",
        "changedAt",
        "items"
})
public record DataFreshnessResponseDto(
        String freshnessToken,
        Date changedAt,
        List<DataFreshnessItemDto> items
) {
}
