package com.x8ing.mtl.server.mtlserver.gpx;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Spatial candidate representation of the recorded wrapped lon/lat curve, including isolated fixes.
 */
public final class RecordedPathGeometry {
    // At this angular step, the curvature bound R * (|dLat| + |dLon|)^2 / 8 is below 0.1 m.
    private static final double MAX_ANGULAR_STEP_DEGREES = 0.01;
    public static final double CANDIDATE_ALLOWANCE_METRES = 0.25;

    private RecordedPathGeometry() {
    }

    public static Coordinate interpolate(Coordinate from, Coordinate to, double fraction) {
        return new Coordinate(wrapLongitude(from.x + wrapLongitude(to.x - from.x) * fraction),
                from.y + (to.y - from.y) * fraction);
    }

    public static double wrapLongitude(double longitude) {
        double wrapped = longitude % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    public static Geometry candidates(LineString line, List<Integer> breakIndexes) {
        if (line == null) return null;
        var breaks = new HashSet<>(breakIndexes == null ? List.of() : breakIndexes);
        var factory = line.getFactory();
        List<Geometry> spans = new ArrayList<>();
        for (int from = 0; from < line.getNumPoints(); ) {
            int to = from + 1;
            while (to < line.getNumPoints() && !breaks.contains(to)) to++;
            List<Coordinate> vertices = new ArrayList<>();
            vertices.add(interpolate(line.getCoordinateN(from), line.getCoordinateN(from), 0));
            for (int i = from + 1; i < to; i++) {
                var a = line.getCoordinateN(i - 1);
                var b = line.getCoordinateN(i);
                int pieces = Math.max(1, (int) Math.ceil(Math.max(Math.abs(b.y - a.y),
                        Math.abs(wrapLongitude(b.x - a.x))) / MAX_ANGULAR_STEP_DEGREES));
                for (int part = 1; part <= pieces; part++) vertices.add(interpolate(a, b, (double) part / pieces));
            }
            spans.add(vertices.size() == 1 ? factory.createPoint(vertices.getFirst())
                    : factory.createLineString(vertices.toArray(Coordinate[]::new)));
            from = to;
        }
        Geometry result = factory.buildGeometry(spans);
        result.setSRID(line.getSRID());
        return result;
    }
}
