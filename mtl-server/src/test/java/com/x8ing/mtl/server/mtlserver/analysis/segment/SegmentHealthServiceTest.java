package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.SegmentCardiacCostRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.MotionInterval;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.TimeRange;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAnalysisFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SegmentHealthServiceTest {
    @Test
    void longDetectedStopCountsForOrdinaryHeartRateButNotCardiacMovingCoverage() {
        var resolver = mock(SegmentAttemptResolver.class);
        var repository = mock(SegmentCardiacCostRepository.class);
        var points = new ArrayList<GpsTrackDataPoint>();
        points.add(point(0, 0, 0));
        for (int second = 50; second <= 350; second += 50) {
            var sample = point(points.size(), second, 500);
            if (second > 50) sample.setMotionState(TrackMotionAnalyzer.State.STOPPED);
            points.add(sample);
        }
        points.add(point(points.size(), 400, 1000));
        var selected = attempt(points, 0, points.size() - 1, 0, 400, 1000d);
        when(resolver.resolve("attempt")).thenReturn(selected);
        var samples = new ArrayList<HeartRateSample>();
        for (int second = 0; second <= 400; second += 10) samples.add(new HeartRateSample(time(second), 120));
        when(repository.heartRate(time(0), time(400))).thenReturn(samples);
        when(repository.motion(1, time(0), time(400))).thenReturn(List.of(new MotionInterval(time(0), time(50), TrackMotionAnalyzer.State.MOVING, 10d),
                new MotionInterval(time(50), time(350), TrackMotionAnalyzer.State.STOPPED, 0d), new MotionInterval(time(350), time(400), TrackMotionAnalyzer.State.MOVING, 10d)));
        when(repository.recordingGaps(1, time(0), time(400))).thenReturn(List.of());
        when(repository.breaks(1, time(0), time(400))).thenReturn(List.of(new TimeRange(time(50), time(350))));
        var result = new SegmentHealthService(resolver, new SegmentCoreService(resolver), repository).get("attempt", 2);
        assertEquals(400d, result.heartRate().coveredSeconds());
        assertEquals(120d, result.heartRate().averageBpm());
        assertEquals(100d, result.cardiacCost().coveredMovingSeconds());
        assertEquals(SegmentMetricAvailability.State.AVAILABLE, result.availability().state());
    }

    @Test
    void exactAttemptIntervalReachesBothHealthCalculationsWhenCrossingsShareRowAnchor() {
        var resolver = mock(SegmentAttemptResolver.class);
        var core = new SegmentCoreService(resolver);
        var repository = mock(SegmentCardiacCostRepository.class);
        var selected = attempt(List.of(point(0, 100, 1000), point(1, 110, 1100)), .1, .4, 101, 104, 30d);
        selected.points().getLast().setEnergyTotalWh(10d);
        when(resolver.resolve("attempt")).thenReturn(selected);
        when(repository.heartRate(time(101), time(104))).thenReturn(List.of(new HeartRateSample(time(100), 120), new HeartRateSample(time(110), 120)));
        when(repository.motion(1, time(101), time(104))).thenReturn(List.of(new MotionInterval(time(100), time(110), TrackMotionAnalyzer.State.MOVING, 10d)));
        when(repository.recordingGaps(1, time(101), time(104))).thenReturn(List.of());
        when(repository.breaks(1, time(101), time(104))).thenReturn(List.of());
        var result = new SegmentHealthService(resolver, core, repository).get("attempt", 2);
        assertEquals(selected.identity(), result.identity());
        assertEquals(3d, result.heartRate().coveredSeconds());
        assertEquals(3d, result.cardiacCost().coveredMovingSeconds());
        assertEquals(10.8d, result.cardiacCost().mechanicalEnergyKj(), 1e-9);
        assertEquals(6d, result.cardiacCost().estimatedHeartbeats());
        verify(repository).heartRate(time(101), time(104));
        verify(repository).motion(1, time(101), time(104));
    }
}
