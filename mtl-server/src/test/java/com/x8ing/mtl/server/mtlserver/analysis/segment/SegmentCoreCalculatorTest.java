package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAnalysisFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SegmentCoreCalculatorTest {
    @Test
    void distanceMismatchPreservesIndependentMetricsAndSuppressesDistanceAndSpeed() {
        var a = point(0, 0, 0);
        var b = point(1, 10, 100);
        b.setEnergyTotalWh(2d);
        var selected = attempt(List.of(a, b), 0, 1, 0, 10, 120d);
        var full = assertDoesNotThrow(() -> SegmentCoreCalculator.calculate(selected));
        assertEquals(10d, full.summary().elapsedSeconds());
        assertEquals(2d, full.summary().energyWh());
        assertEquals(10d, full.summary().ascentMeters());
        assertNull(full.summary().distanceMeters());
        assertNull(full.summary().averageSpeedKmh());
        assertEquals("DISTANCE_MISMATCH", full.summary().availability().get("distance").reason());
        assertTrue(full.geometry().stream().allMatch(point -> point.distanceMeters() == null));
        var chart = SegmentChartSeries.build(selected, full, 2, com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode.TIME);
        assertTrue(chart.points().stream().allMatch(point -> point.speedKmh() == null));
        assertEquals(0d, SegmentComparisonCalculator.compare(full, full).finishDifferenceSeconds());
        assertTrue(SegmentComparisonCalculator.compare(full, full).ghostPoints().isEmpty());
    }

    @Test
    void progressAfterAnUnsupportedIntervalHasUnknownTotalDistance() {
        var points = List.of(point(0, 0, 0), point(1, 10, 100), point(2, 20, 200), point(3, 30, 300));
        points.get(2).setIntervalStatus(GpsTrackDataPoint.IntervalStatus.UNKNOWN);
        var selected = attempt(points, 0, 3, 0, 30, null);
        var result = SegmentCoreCalculator.calculate(selected);
        assertNotNull(result.geometry().get(1).distanceMeters());
        assertNull(result.geometry().get(2).distanceMeters());
        assertNull(result.geometry().get(3).distanceMeters());
        assertNull(result.summary().distanceMeters());
        assertEquals(30d, result.summary().elapsedSeconds());
        assertNull(SegmentHealthSeries.distanceAt(result.geometry(), 25));
        var projected = SegmentChartSeries.build(selected, result, 2, com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode.TIME);
        assertNull(projected.geometry().getLast().distanceMeters());
    }

    @Test
    void fractionalEndpointsCarryMeasurementsAndRemainExactOnBothChartAxes() {
        var points = new ArrayList<GpsTrackDataPoint>();
        for (int i = 0; i < 6; i++) {
            var p = point(i, i * 10, i * 100);
            p.setSpeedInKmhMovingWindow(i * 10d);
            p.setSlopePercentageInMovingWindow(i * 2d);
            p.setPowerWatts(200 + i * 50d);
            points.add(p);
        }
        var selected = attempt(points, 1.25, 3.4, 12.5, 34, 215d);
        var full = SegmentCoreCalculator.calculate(selected);
        var start = full.points().getFirst();
        var finish = full.points().getLast();
        assertEquals(512.5, start.altitudeMeters(), 1e-9);
        assertEquals(12.5, start.speedKmh(), 1e-9);
        assertEquals(2.5, start.slopePercent(), 1e-9);
        assertEquals(300d, start.powerWatts());
        assertEquals(534d, finish.altitudeMeters());
        assertEquals(34d, finish.speedKmh());
        assertEquals(6.8, finish.slopePercent(), 1e-9);
        assertEquals(400d, finish.powerWatts());
        for (var axis : com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode.values()) {
            var display = SegmentChartSeries.build(selected, full, 2, axis);
            var first = display.points().getFirst();
            var last = display.points().getLast();
            assertEquals(selected.identity().start(), first.timestamp());
            assertEquals(selected.identity().end(), last.timestamp());
            assertEquals(selected.identity().startCoordinate().getFirst(), first.longitude());
            assertEquals(selected.identity().endCoordinate().getFirst(), last.longitude());
            assertEquals(0d, first.distanceMeters());
            assertEquals(215d, last.distanceMeters(), 1e-9);
            assertEquals(0d, first.elapsedSeconds());
            assertEquals(21.5, last.elapsedSeconds());
            assertEquals(full.geometry(), display.geometry());
        }
    }

    @Test
    void fractionalEnergyExcludesBothOutsideIntervalsAndWholeTrackPrefix() {
        var points = new ArrayList<GpsTrackDataPoint>();
        double[] intervalWork = {0, 1000, 10, 20, 30, 900};
        double cumulative = 5000;
        for (int i = 0; i < intervalWork.length; i++) {
            var p = point(i, i * 10, i * 100);
            p.setEnergyTotalWh(intervalWork[i]);
            cumulative += intervalWork[i];
            p.setEnergyCumulativeWh(cumulative);
            points.add(p);
        }
        var selected = attempt(points, 1.25, 3.4, 12.5, 34, 215d);
        var full = SegmentCoreCalculator.calculate(selected);
        // 75% of the ending-fix work at 2, all at 3, and 40% at 4.
        assertEquals(39.5, full.summary().energyWh(), 1e-9);
        assertEquals(0d, full.points().getFirst().energyWh());
        assertEquals(7.5, full.points().get(1).energyWh(), 1e-9);
        assertEquals(27.5, full.points().get(2).energyWh(), 1e-9);
        assertEquals(39.5, full.points().getLast().energyWh(), 1e-9);
        var reduced = SegmentChartSeries.build(selected, full, 2, null);
        assertEquals(full.summary(), reduced.summary());
        assertEquals(0d, reduced.points().getFirst().energyWh());
        assertEquals(39.5, reduced.points().getLast().energyWh(), 1e-9);
    }

    @Test
    void authoritativeSummaryAndGeometryProgressIgnoreWholeTrackCumulativeDistance() {
        var a = point(0, 100, 1000);
        var b = point(1, 160, 2000);
        b.setDistanceInMeterSinceStart(1800d);
        var result = SegmentCoreCalculator.calculate(attempt(List.of(a, b), 0, 1, 100, 160, 1000d));
        assertEquals(1000d, result.summary().distanceMeters());
        assertEquals(60d, result.summary().elapsedSeconds());
        assertEquals(60d, result.summary().averageSpeedKmh(), 1e-12);
        assertEquals(0d, result.points().getFirst().distanceMeters());
        assertEquals(1000d, result.points().getLast().distanceMeters(), 1e-9);
        assertEquals(60d, result.points().getLast().elapsedSeconds());
    }

    @Test
    void exactEndpointsAndMaximumSurviveChartReduction() {
        var a = point(0, 0, 0);
        var b = point(1, 50, 500);
        var c = point(2, 100, 1000);
        a.setPowerWatts(900d);
        b.setPowerWatts(100d);
        c.setPowerWatts(200d);
        b.setEnergyTotalWh(10d);
        c.setEnergyTotalWh(20d);
        var attempt = attempt(List.of(a, b, c), 0, 2, 0, 100, 1000d);
        var full = SegmentCoreCalculator.calculate(attempt);
        var reduced = SegmentChartSeries.build(attempt, full, 2, null);
        assertEquals(900d, reduced.summary().maximumPowerWatts());
        assertEquals(500d, reduced.points().getFirst().altitudeMeters());
        assertNull(reduced.points().getFirst().speedKmh(), "Display windows must warm before exposing speed");
        assertEquals(30d, reduced.summary().energyWh());
        assertEquals(full.summary(), reduced.summary());
    }

    @Test
    void sameAnchorIdsStillClipFractionalBoundariesAndIntervalEnergy() {
        var a = point(0, 100, 1000);
        var b = point(1, 110, 1100);
        b.setEnergyTotalWh(100d);
        b.setPowerWatts(36000d);
        var result = SegmentCoreCalculator.calculate(attempt(List.of(a, b), .1, .4, 101, 104, 30d));
        assertEquals(3d, result.summary().elapsedSeconds());
        assertEquals(30d, result.summary().energyWh(), 1e-9);
        assertEquals(time(101), result.points().getFirst().timestamp());
        assertEquals(time(104), result.points().getLast().timestamp());
        assertEquals(3d, result.summary().ascentMeters(), 1e-9);
    }

    @Test
    void missingIntervalEnergyNeverBecomesWholeTrackCumulativeEnergy() {
        var a = point(0, 0, 0);
        var b = point(1, 20, 200);
        var c = point(2, 40, 400);
        b.setEnergyCumulativeWh(510d);
        c.setEnergyCumulativeWh(520d);
        var result = SegmentCoreCalculator.calculate(attempt(List.of(a, b, c), 0, 2, 0, 40, 400d));
        assertNull(result.summary().energyWh());
        assertNull(result.points().getFirst().energyWh());
        assertNull(result.points().get(1).energyWh());
        assertNull(result.points().get(2).energyWh());
        assertEquals(SegmentMetricAvailability.State.UNAVAILABLE, result.summary().availability().get("energy").state());
    }

    @Test
    void chartReductionPreservesMetricNullsAndRecordingBreaks() {
        var points = new ArrayList<GpsTrackDataPoint>();
        for (int i = 0; i < 30; i++) {
            var p = point(i, i, i * 10);
            p.setPowerWatts(100d);
            p.setEnergyTotalWh(i == 0 ? 0d : 1d);
            points.add(p);
        }
        points.get(12).setPowerWatts(null);
        points.get(12).setEnergyTotalWh(null);
        points.get(20).setIntervalStatus(GpsTrackDataPoint.IntervalStatus.UNKNOWN);
        var attempt = attempt(points, 0, 29, 0, 29, null);
        var full = SegmentCoreCalculator.calculate(attempt);
        assertEquals(11d, full.points().get(11).energyWh());
        assertNull(full.summary().energyWh(), "Supported work must not become a complete segment total");
        assertEquals(SegmentMetricAvailability.State.PARTIAL, full.summary().availability().get("energy").state());
        assertEquals(27d, full.summary().availability().get("energy").coveredSeconds());
        assertEquals(SegmentMetricAvailability.State.PARTIAL, full.summary().availability().get("power").state());
        for (var axis : com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode.values()) {
            var reduced = SegmentChartSeries.build(attempt, full, 2, axis);
            assertTrue(reduced.points().stream().anyMatch(p -> p.timestamp().equals(time(12)) && p.powerWatts() == null));
            assertTrue(reduced.points().stream().anyMatch(p -> p.timestamp().equals(time(20)) && p.breakBefore()));
            assertTrue(reduced.points().stream().filter(p -> !p.timestamp().isBefore(time(12))).allMatch(p -> p.energyWh() == null));
            assertNull(reduced.summary().ascentMeters());
            assertEquals(full.summary(), reduced.summary());
            assertEquals(full.geometry(), reduced.geometry());
        }
        assertEquals(30, full.geometry().size());
    }

    @Test
    void unreliableTrackKeepsGeometryButSuppressesEstimatedPointMetrics() {
        var a = point(0, 0, 0);
        var b = point(1, 10, 100);
        var c = point(2, 20, 200);
        b.setPowerWatts(100d);
        c.setPowerWatts(100d);
        b.setEnergyTotalWh(1d);
        c.setEnergyTotalWh(1d);
        var attempt = attempt(List.of(a, b, c), 0, 2, 0, 20, 200d);
        attempt.track().setTimingUnreliable(true);
        var result = SegmentCoreCalculator.calculate(attempt);
        assertEquals(3, result.geometry().size());
        assertEquals(200d, result.geometry().getLast().distanceMeters(), 1e-9);
        assertNull(result.summary().energyWh());
        assertNull(result.summary().maximumPowerWatts());
        result.points().forEach(p -> {
            assertNull(p.timestamp());
            assertNull(p.elapsedSeconds());
            assertNull(p.speedKmh());
            assertNull(p.powerWatts());
            assertNull(p.energyWh());
            assertNotNull(p.altitudeMeters());
        });
        assertNotNull(b.getPointTimestamp(), "Canonical rows must remain unchanged");
    }

    @Test
    void stoppedIntervalKeepsElapsedTimeAndZeroDistance() {
        var a = point(0, 0, 0);
        var b = point(1, 30, 100);
        var c = point(2, 60, 100);
        var d = point(3, 90, 200);
        c.setMotionState(TrackMotionAnalyzer.State.STOPPED);
        var result = SegmentCoreCalculator.calculate(attempt(List.of(a, b, c, d), 0, 3, 0, 90, 200d));
        assertEquals(result.points().get(1).distanceMeters(), result.points().get(2).distanceMeters());
        assertEquals(60d, result.points().get(2).elapsedSeconds());
        assertFalse(result.points().get(2).breakBefore());
    }

    @Test
    void nearbyDuplicateSupportsGeometryInTheSameWayAsDiscovery() {
        var points = List.of(point(0, 0, 0), point(1, 4, 40), point(2, 4, 41), point(3, 10, 100));
        points.get(2).setIntervalStatus(GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY);
        var result = SegmentCoreCalculator.calculate(attempt(points, 0, 3, 0, 10, 100d));
        assertTrue(result.geometry().stream().noneMatch(SegmentGeometryPoint::breakBefore));
        assertEquals(100, result.geometry().getLast().distanceMeters(), 0.001);
    }

    @Test
    void stoppedMotionCannotOverrideAnUnsupportedSparseInterval() {
        var points = List.of(point(0, 0, 0), point(1, 180, 0));
        points.getLast().setMotionState(TrackMotionAnalyzer.State.STOPPED);
        var result = SegmentCoreCalculator.calculate(attempt(points, 0, 1, 0, 180, null));
        assertTrue(result.geometry().getLast().breakBefore());
    }

    @Test
    void ambiguousCrossingKeepsGeometryAndEnergyWithoutInventingAnElapsedAxis() {
        var points = List.of(point(0, 0, 0), point(1, 10, 100));
        points.getLast().setEnergyTotalWh(2d);
        var selected = attempt(points, 0, 1, 0, 10, 100d);
        var original = selected.identity();
        var identity = new SegmentAttemptIdentity(original.reference(), original.trackId(), original.fromZone(),
                original.fromVisit(), original.toZone(), original.toVisit(), original.start(), original.end(),
                original.startPosition(), original.endPosition(), original.startCoordinate(), original.endCoordinate(),
                original.revision(), original.zoneRevision(), SegmentTimingStatus.AMBIGUOUS_DWELL);
        var summary = new SegmentAttemptSummary(identity, 100d, null, null, null, null, null, null, Map.of());
        var uncertain = new SegmentAttemptResolver.ResolvedAttempt(summary, selected.track(), points, List.of());
        var result = SegmentCoreCalculator.calculate(uncertain);
        assertEquals(100d, result.summary().distanceMeters());
        assertEquals(2d, result.summary().energyWh());
        assertTrue(result.geometry().stream().allMatch(p -> p.elapsedSeconds() == null));
        assertTrue(result.points().stream().allMatch(p -> p.elapsedSeconds() == null));
        assertNull(SegmentComparisonCalculator.compare(result, result).finishDifferenceSeconds());
        assertTrue(SegmentComparisonCalculator.compare(result, result).ghostPoints().isEmpty());
        for (var axis : com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode.values()) {
            var chart = SegmentChartSeries.build(uncertain, result, 2, axis);
            assertTrue(chart.points().stream().allMatch(p -> p.elapsedSeconds() == null));
        }
    }
}
