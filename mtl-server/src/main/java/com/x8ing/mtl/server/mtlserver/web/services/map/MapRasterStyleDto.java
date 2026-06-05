package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Browser-facing remote raster map style configuration.
 */
@Data
@JsonPropertyOrder({
        "url",
        "attribution"
})
public class MapRasterStyleDto {

    /**
     * Raster tile URL template. Must contain {z}, {x}, and {y}.
     */
    @Schema(example = "https://tile.openstreetmap.org/{z}/{x}/{y}.png")
    private String url;

    /**
     * Provider attribution HTML passed to MapLibre for this raster source.
     */
    @Schema(example = "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors")
    private String attribution;
}
