package com.x8ing.mtl.server.mtlserver.web.services.status;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHStatusResponse;
import com.x8ing.mtl.server.mtlserver.web.services.freshness.DataFreshnessResponseDto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@JsonPropertyOrder({
        "generatedAt",
        "included",
        "freshness",
        "processing",
        "services",
        "googleHealth",
        "errors"
})
public record ApplicationStatusResponseDto(
        Instant generatedAt,
        Set<ApplicationStatusSection> included,
        DataFreshnessResponseDto freshness,
        ProcessingStatusDto processing,
        ServicesStatusDto services,
        GHStatusResponse googleHealth,
        List<ApplicationStatusErrorDto> errors
) {
}
