package com.x8ing.mtl.server.mtlserver.measurement;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Canonical measurement used to qualify a statistics milestone.
 */
@Schema(enumAsRef = true)
public enum MilestoneDimension {
    DISTANCE,
    ASCENT,
    ENERGY
}
