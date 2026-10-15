package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.SegmentCardiacCostRepository;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.TriggerPoint;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingPointsResponseDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingsPerTrackDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GpsTrackDataPointDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.HeartRateSample;
import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.MotionInterval;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SegmentHeartRateServiceTest {
    private static final Instant START = Instant.parse("2099-01-01T12:00:00Z");

    @Test
    void enrichesExactVisitsAndGroupsByDirectionUsingCoveredTime() {
        var repository = mock(SegmentCardiacCostRepository.class);
        var service = new SegmentHeartRateService(repository);
        when(repository.heartRate(START, START.plusSeconds(50))).thenReturn(List.of(
                new HeartRateSample(START, 100), new HeartRateSample(START.plusSeconds(60), 160)));
        when(repository.motion(1, START, START.plusSeconds(50))).thenReturn(List.of(
                new MotionInterval(START, START.plusSeconds(60), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.STOPPED, null)));
        when(repository.recordingGaps(1, START, START.plusSeconds(50))).thenReturn(List.of());
        var response = new CrossingPointsResponseDto();
        var track = new CrossingsPerTrackDto();
        track.setCrossings(List.of(crossing("A", 0), crossing("B", 10), crossing("A", 20), crossing("B", 50)));
        response.getCrossings().put(1L, track);
        service.enrich(response);
        assertThat(track.getCrossings().get(1).getHeartRateSinceLastTriggerPoint().averageBpm()).isEqualTo(105);
        assertThat(track.getCrossings().get(3).getHeartRateSinceLastTriggerPoint().averageBpm()).isEqualTo(135);
        assertThat(track.getSegmentHeartRateSummaries()).hasSize(2);
        var combined = track.getSegmentHeartRateSummaries().getFirst();
        assertThat(combined.fromTriggerPoint()).isEqualTo("A");
        assertThat(combined.toTriggerPoint()).isEqualTo("B");
        assertThat(combined.heartRate().averageBpm()).isEqualTo(127.5); // Not the unweighted 120.
        assertThat(combined.heartRate().coveredSeconds()).isEqualTo(40);
        verify(repository).heartRate(START, START.plusSeconds(50));
        verify(repository, never()).breaks(anyLong(), any(), any()); // Stops must not be removed.
    }

    private static CrossingDto crossing(String name, int seconds) {
        var crossing = new CrossingDto();
        crossing.setTriggerPoint(new TriggerPoint(name, null));
        var point = new GpsTrackDataPointDto();
        point.setPointTimestamp(Date.from(START.plusSeconds(seconds)));
        crossing.setGpsTrackDataPoint(point);
        return crossing;
    }
}
