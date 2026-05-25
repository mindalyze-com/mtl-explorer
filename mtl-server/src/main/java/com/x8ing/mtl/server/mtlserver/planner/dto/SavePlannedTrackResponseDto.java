package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for persisting a planned route.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
        "id",
        "name",
        "distanceM"
})
public class SavePlannedTrackResponseDto {
    private Long id;
    private String name;
    private double distanceM;
}
