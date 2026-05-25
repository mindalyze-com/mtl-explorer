package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "bucketCount",
        "metricLabel",
        "metricUnit",
        "bucketLabel",
        "direction"
})
public class FilterGradientMetadata {

    @Schema(description = "Number of gradient buckets.", minimum = "1")
    private Integer bucketCount;

    @Schema(description = "User-facing metric label.")
    private String metricLabel;

    @Schema(description = "User-facing metric unit.")
    private String metricUnit;

    @Schema(description = "User-facing bucket label.")
    private String bucketLabel;

    @Schema(description = "Gradient direction.", allowableValues = {"low-to-high"})
    private FilterGradientDirection direction;
}
