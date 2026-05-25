package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repairs leading GPS elevation warm-up artifacts without changing XY or time.
 */
final class StartupElevationStabilizer {

    static final String CORRECTOR_NAME = "StartupElevationStabilizer";

    static final double STARTUP_TIME_WINDOW_SEC = 60.0;
    static final double STARTUP_DISTANCE_WINDOW_M = 75.0;
    static final int MIN_VALID_ELEVATION_SAMPLES = 12;
    static final double BASELINE_TAIL_START_SEC = 10.0;
    static final int MIN_BASELINE_TAIL_SAMPLES = 8;
    static final double MIN_STARTUP_SHIFT_M = 25.0;
    static final double MIN_CATCHUP_RATE_MS = 1.5;
    static final int STABLE_SAMPLE_COUNT = 5;
    static final double STABLE_BASELINE_TOLERANCE_M = 3.0;
    static final double MAX_CORRECTION_TIME_SEC = 45.0;

    private StartupElevationStabilizer() {
    }

    static Result stabilize(LineString lineString) {
        if (lineString == null || lineString.isEmpty() || lineString.getNumPoints() < MIN_VALID_ELEVATION_SAMPLES) {
            return unchanged(lineString);
        }

        Coordinate first = lineString.getCoordinateN(0);
        if (Double.isNaN(first.getZ()) || Double.isNaN(first.getM())) {
            return unchanged(lineString);
        }

        List<Sample> samples = collectStartupSamples(lineString, first.getM());
        if (samples.size() < MIN_VALID_ELEVATION_SAMPLES) {
            return unchanged(lineString);
        }

        double baseline = median(baselineValues(samples));
        int stableRunStart = findStableRunStart(samples, baseline);
        if (stableRunStart < 0) {
            return unchanged(lineString);
        }

        Sample firstSample = samples.get(0);
        Sample stableSample = samples.get(stableRunStart);
        if (stableSample.index() <= firstSample.index()) {
            return unchanged(lineString);
        }

        double startupShift = baseline - firstSample.elevation();
        if (Math.abs(startupShift) < MIN_STARTUP_SHIFT_M) {
            return unchanged(lineString);
        }

        double secondsToStable = stableSample.elapsedSeconds();
        if (Double.isNaN(secondsToStable) || secondsToStable <= 0
            || Math.abs(startupShift) / secondsToStable < MIN_CATCHUP_RATE_MS) {
            return unchanged(lineString);
        }

        Coordinate[] repaired = copyCoordinates(lineString);
        int corrected = 0;
        for (int i = firstSample.index(); i < stableSample.index(); i++) {
            Coordinate coordinate = repaired[i];
            double elapsedSeconds = coordinate.getM() - first.getM();
            if (Double.isNaN(elapsedSeconds) || elapsedSeconds > MAX_CORRECTION_TIME_SEC) {
                break;
            }
            if (!Double.isNaN(coordinate.getZ())) {
                repaired[i] = new CoordinateXYZM(
                        coordinate.getX(),
                        coordinate.getY(),
                        baseline,
                        coordinate.getM());
                corrected++;
            }
        }

        if (corrected == 0) {
            return unchanged(lineString);
        }

        LineString stabilized = new LineString(new CoordinateArraySequence(repaired), lineString.getFactory());
        return new Result(stabilized, corrected, baseline);
    }

    private static List<Sample> collectStartupSamples(LineString lineString, double startTimeSeconds) {
        List<Sample> samples = new ArrayList<>();
        double cumulativeDistanceM = 0.0;

        Coordinate previous = lineString.getCoordinateN(0);
        for (int i = 0; i < lineString.getNumPoints(); i++) {
            Coordinate current = lineString.getCoordinateN(i);
            if (i > 0) {
                cumulativeDistanceM += GPXReader.getDistanceBetweenTwoWGS84(previous, current);
                previous = current;
            }
            if (cumulativeDistanceM > STARTUP_DISTANCE_WINDOW_M) {
                break;
            }

            double timeSeconds = current.getM();
            if (Double.isNaN(timeSeconds)) {
                continue;
            }
            double elapsedSeconds = timeSeconds - startTimeSeconds;
            if (elapsedSeconds < 0) {
                return List.of();
            }
            if (elapsedSeconds > STARTUP_TIME_WINDOW_SEC) {
                break;
            }
            if (!Double.isNaN(current.getZ())) {
                samples.add(new Sample(i, current.getZ(), elapsedSeconds));
            }
        }

        return samples;
    }

    private static List<Double> baselineValues(List<Sample> samples) {
        List<Double> tailValues = new ArrayList<>();
        for (Sample sample : samples) {
            if (sample.elapsedSeconds() >= BASELINE_TAIL_START_SEC) {
                tailValues.add(sample.elevation());
            }
        }
        if (tailValues.size() >= MIN_BASELINE_TAIL_SAMPLES) {
            return tailValues;
        }

        List<Double> allValues = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            allValues.add(sample.elevation());
        }
        return allValues;
    }

    private static int findStableRunStart(List<Sample> samples, double baseline) {
        for (int i = 0; i <= samples.size() - STABLE_SAMPLE_COUNT; i++) {
            boolean stable = true;
            for (int j = 0; j < STABLE_SAMPLE_COUNT; j++) {
                if (Math.abs(samples.get(i + j).elevation() - baseline) > STABLE_BASELINE_TOLERANCE_M) {
                    stable = false;
                    break;
                }
            }
            if (stable) {
                return i;
            }
        }
        return -1;
    }

    private static Coordinate[] copyCoordinates(LineString lineString) {
        Coordinate[] copied = new Coordinate[lineString.getNumPoints()];
        for (int i = 0; i < lineString.getNumPoints(); i++) {
            Coordinate coordinate = lineString.getCoordinateN(i);
            copied[i] = new CoordinateXYZM(
                    coordinate.getX(),
                    coordinate.getY(),
                    coordinate.getZ(),
                    coordinate.getM());
        }
        return copied;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    private static Result unchanged(LineString lineString) {
        return new Result(lineString, 0, Double.NaN);
    }

    @JsonPropertyOrder({
            "lineString",
            "correctedPointCount",
            "baselineElevation"
    })
    record Result(LineString lineString, int correctedPointCount, double baselineElevation) {
        boolean corrected() {
            return correctedPointCount > 0;
        }
    }

    @JsonPropertyOrder({
            "index",
            "elevation",
            "elapsedSeconds"
    })
    private record Sample(int index, double elevation, double elapsedSeconds) {
    }
}
