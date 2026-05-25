package com.x8ing.mtl.server.mtlserver.web.services.freshness;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;

@JsonPropertyOrder({
        "key",
        "revision",
        "changedAt"
})
public record DataFreshnessItemDto(
        String key,
        long revision,
        Date changedAt
) {
}
