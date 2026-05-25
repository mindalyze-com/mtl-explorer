package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Geographic bounds in MapLibre order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
        "minLng",
        "minLat",
        "maxLng",
        "maxLat"
})
public class MapBoundsDto {

    private double minLng;
    private double minLat;
    private double maxLng;
    private double maxLat;
}
