package com.x8ing.mtl.server.mtlserver.web.services.freshness;

import java.util.Date;
import java.util.List;

public record DataFreshnessResponseDto(
        String freshnessToken,
        Date changedAt,
        List<DataFreshnessItemDto> items
) {
}
