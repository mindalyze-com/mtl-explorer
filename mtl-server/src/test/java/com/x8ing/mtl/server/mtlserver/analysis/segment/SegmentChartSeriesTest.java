package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.ChartBucketBuilder;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;
import com.x8ing.mtl.server.mtlserver.metrics.chart.ChartSeriesMetrics;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAnalysisFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SegmentChartSeriesTest {
    @Test
    void powerUsesTheWholeTrackWindowBeforeClippingAndSharesBucketAverages() {
        var attempt = hikingAttempt();
        var full = SegmentCoreCalculator.calculate(attempt);
        var display = SegmentChartSeries.build(attempt, full, 3000, XMode.TIME);
        assertEquals(120d, display.points().getFirst().powerWatts(), 1e-9);
        assertTrue(display.points().stream().allMatch(p -> p.powerWatts() < 121), "Raw 1800 W pulses must be averaged");
        assertEquals(1800d, display.summary().maximumPowerWatts());
        assertEquals(full.summary(), display.summary());
        assertEquals(full.geometry(), display.geometry());

        var reduced = SegmentChartSeries.build(attempt, full, 3, XMode.TIME);
        var metrics = ChartSeriesMetrics.compute(attempt.track(), attempt.points(), null);
        var wholeTrack = new ChartBucketBuilder().build(metrics.points(), metrics.rates(), metrics.powerWatts(),
                new ChartBucketBuilder.Config(XMode.TIME, 3, 90d, 150d, null));
        var middle = wholeTrack.buckets().get(1);
        double elapsed = attempt.points().get(middle.representativePointIndex()).getDurationSinceStart() - 90;
        var actual = reduced.points().stream().filter(p -> p.elapsedSeconds() == elapsed).findFirst().orElseThrow();
        assertEquals(middle.metrics().get(MetricKey.POWER_WINDOW_WATTS).getAvg(), actual.powerWatts());
        assertEquals(middle.metrics().get(MetricKey.ALTITUDE_M).getAvg(), actual.altitudeMeters());
        assertEquals(middle.metrics().get(MetricKey.SPEED_WINDOW_KMH).getAvg(), actual.speedKmh());
        assertEquals(0d, reduced.points().getFirst().elapsedSeconds());
        assertEquals(60d, reduced.points().getLast().elapsedSeconds());
        assertEquals(full.summary().energyWh(), reduced.points().getLast().energyWh());
    }

    @Test
    void pointBudgetChangesOnlyDisplayDensityAndSelectedAxisChangesBucketing() {
        var attempt = hikingAttempt();
        // A slow first half makes time and distance buckets select different samples.
        for (int i = 90; i <= 150; i++) {
            double meters = i <= 120 ? 900 + (i - 90) : 930 + (i - 120) * 20;
            var p = attempt.points().get(i);
            p.setPointLongLat(new org.locationtech.jts.geom.GeometryFactory().createPoint(
                    new org.locationtech.jts.geom.Coordinate(meters / 111_320, 0)));
        }
        // Geometry changes also change the authoritative boundaries and distance.
        attempt = attempt(attempt.points(), 90, 150, 90, 150, 630d);
        attempt.track().setActivityType(GpsTrack.ACTIVITY_TYPE.HIKING);
        var full = SegmentCoreCalculator.calculate(attempt);
        var low = SegmentChartSeries.build(attempt, full, 3, XMode.TIME);
        var high = SegmentChartSeries.build(attempt, full, 3000, XMode.TIME);
        var distance = SegmentChartSeries.build(attempt, full, 3, XMode.DISTANCE);
        assertTrue(low.points().size() < high.points().size());
        assertEquals(low.summary(), high.summary());
        assertEquals(low.geometry(), high.geometry());
        assertNotEquals(low.points(), distance.points());
    }

    @Test
    void gapsAndStoppedDistanceLimitsSurviveAggregation() {
        var attempt = hikingAttempt();
        attempt.points().get(140).setIntervalStatus(GpsTrackDataPoint.IntervalStatus.UNKNOWN);
        var stop = attempt.points().get(130);
        stop.setPointLongLat(attempt.points().get(129).getPointLongLat());
        stop.setMotionState(TrackMotionAnalyzer.State.STOPPED);
        var full = SegmentCoreCalculator.calculate(attempt);
        var display = SegmentChartSeries.build(attempt, full, 3, XMode.DISTANCE);
        var gap = display.points().stream().filter(p -> p.timestamp().equals(time(140))).findFirst().orElseThrow();
        assertTrue(gap.breakBefore());
        assertNull(gap.powerWatts());
        var arrival = display.points().stream().filter(p -> p.timestamp().equals(time(129))).findFirst().orElseThrow();
        var departure = display.points().stream().filter(p -> p.timestamp().equals(time(130))).findFirst().orElseThrow();
        assertEquals(arrival.distanceMeters(), departure.distanceMeters());
        assertEquals(full.summary(), display.summary());
        assertEquals(full.geometry(), display.geometry());
    }

    @Test
    void unknownDistanceAfterAGapStillAllowsTimeChartReduction() {
        int lastPointIndex = 5000;
        int gapIndex = 100;
        int budget = 20;
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i <= lastPointIndex; i++) {
            var p = point(i, i, i * 10);
            p.setDurationBetweenPointsInSec(i == 0 ? 0d : 1d);
            p.setDistanceInMeterBetweenPoints(i == 0 ? 0d : 10d);
            p.setAscentInMeterBetweenPoints(0d);
            points.add(p);
        }
        points.get(gapIndex).setIntervalStatus(GpsTrackDataPoint.IntervalStatus.UNKNOWN);
        var attempt = attempt(points, 0, lastPointIndex, 0, lastPointIndex, null);
        var full = SegmentCoreCalculator.calculate(attempt);
        var reduced = SegmentChartSeries.build(attempt, full, budget, XMode.TIME);
        assertTrue(reduced.points().size() <= budget * 2,
                "A single gap must not retain every later source sample: " + reduced.points().size());
        var gap = reduced.points().stream().filter(p -> p.timestamp().equals(time(gapIndex))).findFirst().orElseThrow();
        assertTrue(gap.breakBefore());
        assertTrue(reduced.points().stream().filter(p -> !p.timestamp().isBefore(time(gapIndex)))
                .allMatch(p -> p.distanceMeters() == null));
        assertEquals(time(0), reduced.points().getFirst().timestamp());
        assertEquals(time(lastPointIndex), reduced.points().getLast().timestamp());
        assertEquals(full.summary(), reduced.summary());
        assertEquals(full.geometry(), reduced.geometry());
    }

    private static SegmentAttemptResolver.ResolvedAttempt hikingAttempt() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i <= 150; i++) {
            var p = point(i, i, i * 10);
            p.setDurationBetweenPointsInSec(i == 0 ? 0d : 1d);
            p.setDistanceInMeterBetweenPoints(i == 0 ? 0d : 10d);
            p.setAscentInMeterBetweenPoints(0d);
            p.setPowerWatts(i % 15 == 0 && i > 0 ? 1800d : 0d);
            p.setEnergyTotalWh(p.getPowerWatts() / 3600);
            points.add(p);
        }
        var attempt = attempt(points, 90, 150, 90, 150, 600d);
        attempt.track().setActivityType(GpsTrack.ACTIVITY_TYPE.HIKING);
        return attempt;
    }
}
