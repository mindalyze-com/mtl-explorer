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
        "label",
        "order",
        "defaultOpen"
})
public class FilterParamGroupMetadata {

    @Schema(description = "User-facing group label.")
    private String label;

    @Schema(description = "Ascending group display order.")
    private Integer order;

    @Schema(description = "Initial group expansion behavior.", allowableValues = {"always", "never", "whenActive"})
    private FilterParamGroupDefaultOpen defaultOpen;
}
