package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import lombok.extern.slf4j.Slf4j;

/**
 * Fail early if an IDE or launcher accidentally puts the isolated core on the classpath.
 */
@Slf4j
public final class GpsQualityModules {
    public static final String DIAGNOSTICS_MODULE = "com.x8ing.mtl.gpsquality.diagnostics";

    private GpsQualityModules() {
    }

    public static void verify() {
        Module diagnostics = TrackDiagnosticEngine.class.getModule();
        Module application = GpsQualityModules.class.getModule();
        if (!DIAGNOSTICS_MODULE.equals(diagnostics.getName())
            || diagnostics.canRead(application)) {
            throw new IllegalStateException("GPS quality modules must run on the module path, with no dependency "
                                            + "back into the application. Use the configured Maven or Docker launcher; "
                                            + "see documentation/development/gps-quality-modules.md.");
        }
        log.info("GPS quality module isolation verified: diagnostics={}", diagnostics.getName());
    }
}
