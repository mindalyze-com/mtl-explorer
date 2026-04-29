package com.x8ing.mtl.server.mtlserver.web.services.freshness;

import java.util.Date;

public record DataFreshnessItemDto(
        String key,
        long revision,
        Date changedAt
) {
}
