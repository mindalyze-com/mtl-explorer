package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * A named point belonging to one saved MTL Explorer plan.
 */
@Data
@JsonPropertyOrder({"id", "lat", "lng", "name", "iconType", "notes"})
public class PointOfInterestDto {
    /**
     * Stable identity within the plan, retained when it is reopened or copied.
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Double lat;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Double lng;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    /**
     * Missing icon types use the standard pin, including maps created before icon selection.
     */
    @Schema(defaultValue = "pin")
    private PointOfInterestIconType iconType = PointOfInterestIconType.PIN;
    private String notes;
}
