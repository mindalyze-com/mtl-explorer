/*
 * The MIT License (MIT)
 * Copyright (c) 2019 BRouter contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.x8ing.mtl.server.mtlserver.gpx;

/**
 * Elevation buffer ported from BRouter 1.7.10, RoutingEngine.recalcTrack/elevationFilter:
 * https://github.com/abrensch/brouter/blob/v1.7.10/brouter-core/src/main/java/btools/router/RoutingEngine.java#L1438
 *
 * <p>MTL Explorer adaptations: input/output in metres without integer truncation;
 * reverse traversal gives descent on the identical geometry; missing heights break
 * the accumulation. This counts elevation change, without modifying the profile.
 */
public final class BRouterElevationCalculator {
    private BRouterElevationCalculator() {
    }

    public static Totals calculate(double[] elevations, double bufferM) {
        if (!Double.isFinite(bufferM) || bufferM <= 0) {
            throw new IllegalArgumentException("Elevation buffer must be finite and positive");
        }
        return new Totals(filteredAscent(elevations, bufferM, false),
                filteredAscent(elevations, bufferM, true));
    }

    private static double filteredAscent(double[] elevations, double bufferM, boolean reverse) {
        double ascend = 0;
        double ehb = 0;
        double previous = Double.NaN;
        double filter = -bufferM;
        for (int offset = 0; offset < elevations.length; offset++) {
            double elevation = elevations[reverse ? elevations.length - 1 - offset : offset];
            if (Double.isFinite(elevation) && Double.isFinite(previous)) {
                // BRouter's height-buffer update, converted from quarter metres.
                ehb = ehb + (elevation - previous);
                if (ehb > 0) {
                    ascend += ehb;
                    ehb = 0;
                } else if (ehb < filter) {
                    ehb = filter;
                }
            } else {
                ehb = 0;
            }
            previous = elevation;
        }
        return ascend;
    }

    public record Totals(double ascentM, double descentM) {
    }
}
