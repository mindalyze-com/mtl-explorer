package com.x8ing.mtl.server.mtlserver.gpx;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Distance-and-time based median filter for GPS elevation denoising.
 *
 * <p>For each coordinate, collects all neighbours within
 * {@value #DENOISE_DISTANCE_RADIUS_M} metres geodesic distance AND
 * {@value #DENOISE_TIME_RADIUS_SEC} seconds, then replaces Z with the median
 * of the collected altitudes.  X, Y, and M (timestamp) are preserved.
 *
 * <p>The median filter eliminates isolated spike outliers while preserving
 * sharp real terrain transitions.  Selected via
 * {@code mtl.gpx.denoise.algorithm: median} (the default).
 */
@Component("median")
public class MedianElevationSmoother implements GpsSmoothingAlgorithm {

    /**
     * Maximum distance (metres) each side of centre point for the denoising window.
     */
    static final double DENOISE_DISTANCE_RADIUS_M = 50.0;

    /**
     * Maximum time (seconds) each side of centre point for the denoising window.
     */
    static final double DENOISE_TIME_RADIUS_SEC = 30.0;

    @Override
    public LineString denoise(LineString lineString) {
        int n = lineString.getNumPoints();
        if (n <= 2) return lineString;

        Coordinate[] coords = lineString.getCoordinates();

        // Pre-compute cumulative geodesic distance from start (O(N))
        double[] cumulativeDist = new double[n];
        cumulativeDist[0] = 0;
        for (int i = 1; i < n; i++) {
            cumulativeDist[i] = cumulativeDist[i - 1]
                                + GPXReader.getDistanceBetweenTwoWGS84(coords[i - 1], coords[i]);
        }

        // Extract timestamps from M coordinate
        double[] timestamps = new double[n];
        for (int i = 0; i < n; i++) {
            timestamps[i] = coords[i].getM();
        }

        Coordinate[] smoothed = new Coordinate[n];

        for (int i = 0; i < n; i++) {
            double z = coords[i].getZ();
            if (Double.isNaN(z)) {
                smoothed[i] = new CoordinateXYZM(
                        coords[i].getX(), coords[i].getY(), z, coords[i].getM());
                continue;
            }

            double centerDist = cumulativeDist[i];
            double centerTime = timestamps[i];

            // Expand window left
            int left = i;
            while (left > 0) {
                int candidate = left - 1;
                if (centerDist - cumulativeDist[candidate] > DENOISE_DISTANCE_RADIUS_M) break;
                if (centerTime > 0 && timestamps[candidate] > 0
                    && centerTime - timestamps[candidate] > DENOISE_TIME_RADIUS_SEC) break;
                left = candidate;
            }

            // Expand window right
            int right = i;
            while (right < n - 1) {
                int candidate = right + 1;
                if (cumulativeDist[candidate] - centerDist > DENOISE_DISTANCE_RADIUS_M) break;
                if (centerTime > 0 && timestamps[candidate] > 0
                    && timestamps[candidate] - centerTime > DENOISE_TIME_RADIUS_SEC) break;
                right = candidate;
            }

            // Collect valid Z values in window and compute median
            List<Double> zValues = new ArrayList<>();
            for (int j = left; j <= right; j++) {
                double zj = coords[j].getZ();
                if (!Double.isNaN(zj)) zValues.add(zj);
            }

            double smoothedZ;
            if (zValues.isEmpty()) {
                smoothedZ = z;
            } else {
                Collections.sort(zValues);
                int size = zValues.size();
                smoothedZ = (size % 2 == 1)
                        ? zValues.get(size / 2)
                        : (zValues.get(size / 2 - 1) + zValues.get(size / 2)) / 2.0;
            }

            smoothed[i] = new CoordinateXYZM(
                    coords[i].getX(), coords[i].getY(), smoothedZ, coords[i].getM());
        }

        GeometryFactory factory = lineString.getFactory();
        return new LineString(new CoordinateArraySequence(smoothed), factory);
    }
}
