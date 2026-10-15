package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducible independent synthetic shapes/cadences. Reports accuracy rather than tuning to one anecdotal trace.
 */
class GpsSmoothingEvaluationTest {
    private static final int DURATION_SECONDS = 600;
    private static final long NOISE_SEED = 20260905L;

    @Test
    void compareConfiguredKalmanAgainstRawOnSyntheticGroundTruth() throws Exception {
        System.out.println("GPS_EVALUATION shape,cadence_s,raw_distance_m,kalman_distance_m,reference_distance_m,false_stop_s");
        for (String shape : List.of("straight", "curve", "switchback")) {
            for (int cadence : new int[]{1, 2, 5, 10, 60}) {
                Random random = new Random(NOISE_SEED + cadence);
                List<Coordinate> reference = new ArrayList<>(), noisy = new ArrayList<>();
                for (int time = 0; time <= DURATION_SECONDS; time += cadence) {
                    double[] xy = position(shape, time);
                    reference.add(GpsPipelineReliabilityTest.c(xy[0], xy[1], 500 + time * .03, time));
                    noisy.add(GpsPipelineReliabilityTest.c(xy[0] + random.nextGaussian() * 6,
                            xy[1] + random.nextGaussian() * 6, 500 + time * .03 + random.nextGaussian() * 4, time));
                }
                var loaded = GpsPipelineReliabilityTest.read(noisy);
                var smoothed = GpsPipelineReliabilityTest.smooth(loaded);
                var points = GpsPipelineReliabilityTest.metrics(smoothed, loaded.recordingBreakBeforePointIndexes);
                double distance = points.getLast().getDistanceInMeterSinceStart();
                double trueDistance = GPXReader.getDistanceOfWGS84(GpsPipelineReliabilityTest.line(reference));
                double rawDistance = GPXReader.getDistanceOfWGS84(GpsPipelineReliabilityTest.line(noisy));
                double stopped = loaded.stopRanges.stream().mapToDouble(stop -> stop.durationS()).sum();
                assertTrue(Double.isFinite(distance));
                assertEquals(0, stopped, "Continuous movement must not become a stop: " + shape + "/" + cadence);
                // Straight dense samples have independent ground truth and should improve on raw jitter.
                if (shape.equals("straight") && cadence <= 5)
                    assertTrue(Math.abs(distance - trueDistance) < Math.abs(rawDistance - trueDistance));
                System.out.printf(Locale.ROOT, "GPS_EVALUATION %s,%d,%.3f,%.3f,%.3f,%.3f%n", shape, cadence, rawDistance, distance, trueDistance, stopped);
            }
        }
    }

    private static double[] position(String shape, double time) {
        return switch (shape) {
            case "curve" -> new double[]{300 * Math.sin(time / 100), 300 * Math.cos(time / 100)};
            case "switchback" -> new double[]{time, 80 * Math.sin(time / 35)};
            default -> new double[]{2 * time, .3 * time};
        };
    }
}
