package com.x8ing.mtl.server.mtlserver.measurement;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Supported measurement systems for user-facing values and domain definitions.
 */
@Schema(enumAsRef = true)
public enum MeasurementSystem {
    METRIC,
    US_CUSTOMARY
}
