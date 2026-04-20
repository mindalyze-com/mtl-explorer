package com.x8ing.mtl.server.mtlserver.gpx;

import org.locationtech.jts.geom.LineString;

/**
 * Strategy interface for GPS track smoothing algorithms.
 *
 * <p>Implementations receive an XYZM {@link LineString} (X=longitude, Y=latitude,
 * Z=elevation in metres, M=epoch seconds) and must return a new LineString with the
 * same number of points and identical M (timestamp) values.
 *
 * <p>Implementations may smooth Z only (e.g. median filter) or all three spatial
 * dimensions X/Y/Z (e.g. 3-D Kalman filter).  Callers must not assume that X/Y
 * are unchanged.
 *
 * <p>The active implementation is selected via {@code mtl.denoise.algorithm}
 * in {@code application.yml}.  Valid values correspond to the Spring bean names of
 * the registered implementations (e.g. {@code median}, {@code kalman}).
 */
public interface GpsSmoothingAlgorithm {

    /**
     * Smooth the given XYZM LineString.
     *
     * @param lineString XYZM LineString; M (timestamp) must be preserved unchanged.
     *                   X/Y may or may not be modified depending on the implementation.
     * @return a new LineString with smoothed values and identical M
     */
    LineString denoise(LineString lineString);
}
