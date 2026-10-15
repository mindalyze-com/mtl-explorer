package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.MotionInterval;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.TimeRange;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAnalysisFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SegmentHealthSeriesTest {
    @Test
    void sampleWithoutSupportedMapDistanceIsRetainedForTheTimeAxis() {
        var base = core(100, 100, new double[][]{{0, 0}, {100, 100}});
        var geometry = base.geometry().stream().map(p -> new SegmentGeometryPoint(p.distanceMeters(), p.elapsedSeconds(),
                p.timestamp(), p.longitude(), p.latitude(), true)).toList();
        var full = new SegmentCoreAnalysis(base.identity(), base.summary(), base.points(), geometry, base.sourcePointCount());
        var series = SegmentHealthSeries.build(full, List.of(new HeartRateSample(time(8), 180)), List.of(), 2);
        assertEquals(1, series.size());
        assertNull(series.getFirst().distanceMeters());
        assertEquals(8d, series.getFirst().elapsedSeconds());
        assertEquals(180d, series.getFirst().averageBpm());
    }

    @Test
    void intervalSamplesAreRestrictedBeforeBucketing() {
        var full = SegmentCoreCalculator.calculate(attempt(List.of(point(0, 7, 70), point(1, 9, 90)), 0, 1, 7, 9, 20d));
        var series = SegmentHealthSeries.build(full, List.of(new HeartRateSample(time(2), 100), new HeartRateSample(time(8), 180)), List.of(), 2);
        assertEquals(1, series.size());
        assertEquals(180d, series.getFirst().averageBpm());
        assertEquals(time(8), series.getFirst().timestamp());
        assertEquals(10d, series.getFirst().distanceMeters(), 1e-8);
    }

    @Test
    void summaryRetainsOutsideSupportWhileChartDoesNotAdmitThoseSamples() {
        var full = SegmentCoreCalculator.calculate(attempt(List.of(point(0, 2, 20), point(1, 8, 80)), 0, 1, 2, 8, 60d));
        var support = List.of(new HeartRateSample(time(0), 100), new HeartRateSample(time(10), 200));
        var summary = SegmentHeartRateCalculator.calculate(time(2), time(8),
                List.of(new MotionInterval(time(0), time(10), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, 10d)), support, List.of());
        assertEquals(150d, summary.averageBpm());
        assertEquals(6d, summary.coveredSeconds());
        assertTrue(SegmentHealthSeries.build(full, support, List.of(), 2).isEmpty());
    }

    @Test
    void sparseOrInvalidSamplesBreakBucketsEvenAtLowResolution() {
        var full = core(1000, 1000, new double[][]{{0, 0}, {1000, 1000}});
        var series = SegmentHealthSeries.build(full, List.of(new HeartRateSample(time(2), 100),
                new HeartRateSample(time(80), 150), new HeartRateSample(time(82), 0), new HeartRateSample(time(85), 180)), List.of(), 2);
        assertEquals(3, series.size());
        assertTrue(series.get(1).breakBefore());
        assertTrue(series.get(2).breakBefore());
        assertEquals(180d, series.get(2).averageBpm());
    }

    @Test
    void recordingGapCannotDisappearInsideSingleBucket() {
        var full = core(100, 100, new double[][]{{0, 0}, {100, 100}});
        var series = SegmentHealthSeries.build(full, List.of(new HeartRateSample(time(2), 100), new HeartRateSample(time(8), 180)),
                List.of(new TimeRange(time(4), time(6))), 2);
        assertEquals(2, series.size());
        assertTrue(series.getLast().breakBefore());
    }
}
