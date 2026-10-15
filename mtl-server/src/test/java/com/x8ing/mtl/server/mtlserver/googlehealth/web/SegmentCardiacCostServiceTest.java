package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.SegmentCardiacCostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SegmentCardiacCostServiceTest {
    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final GpsTrackDataRepository variants = mock(GpsTrackDataRepository.class);
    private final GpsTrackDataPointRepository points = mock(GpsTrackDataPointRepository.class);
    private final SegmentCardiacCostRepository repository = mock(SegmentCardiacCostRepository.class);
    private final SegmentCardiacCostService service = new SegmentCardiacCostService(tracks, variants, points, repository);
    private final Instant start = Instant.parse("2099-01-01T12:00:00Z");
    private GpsTrack track;
    private GpsTrackDataPoint from;
    private GpsTrackDataPoint to;

    @BeforeEach
    void setup() {
        track = new GpsTrack();
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.BICYCLE);
        from = point(start);
        to = point(start.plusSeconds(60));
        when(tracks.findById(1L)).thenReturn(Optional.of(track));
        when(points.findById(10L)).thenReturn(Optional.of(from));
        when(points.findById(20L)).thenReturn(Optional.of(to));
        when(variants.findById(2L)).thenReturn(Optional.of(GpsTrackData.builder().gpsTrackId(1L).build()));
    }

    @Test
    void queriesOnlyTheValidatedSegmentRange() {
        Instant end = start.plusSeconds(60);
        when(repository.motion(1L, start, end)).thenReturn(List.of(new SegmentCardiacCostCalculator.MotionInterval(start, end, com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, 5.0)));
        when(repository.heartRate(start, end)).thenReturn(List.of(new SegmentCardiacCostCalculator.HeartRateSample(start, 120),
                new SegmentCardiacCostCalculator.HeartRateSample(end, 120)));
        assertThat(service.get(1, 10, 20).available()).isTrue();
        verify(repository).breaks(1L, start, end);
    }

    @Test
    void rejectsEndpointsFromAnotherTrack() {
        when(variants.findById(2L)).thenReturn(Optional.of(GpsTrackData.builder().gpsTrackId(99L).build()));
        assertThatThrownBy(() -> service.get(1, 10, 20)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsEndpointsFromDifferentVariants() {
        to.setGpsTrackDataId(3L);
        assertThatThrownBy(() -> service.get(1, 10, 20)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsReversedOrZeroLengthIntervals() {
        to.setPointTimestamp(from.getPointTimestamp());
        assertThatThrownBy(() -> service.get(1, 10, 20)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(repository);
    }

    @Test
    void returnsNotFoundForAnUnknownPoint() {
        when(points.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(1, 10, 20)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    void explainsMissingTimestampsAndMotorizedActivities() {
        from.setPointTimestamp(null);
        assertThat(service.get(1, 10, 20).unavailableReason()).isEqualTo(SegmentCardiacCostSummary.UnavailableReason.MISSING_TIMESTAMPS);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.CAR);
        assertThat(service.get(1, 10, 20).unavailableReason()).isEqualTo(SegmentCardiacCostSummary.UnavailableReason.MOTORIZED_ACTIVITY);
        verifyNoInteractions(repository);
    }

    private GpsTrackDataPoint point(Instant timestamp) {
        var point = new GpsTrackDataPoint();
        point.setGpsTrackDataId(2L);
        point.setPointTimestamp(Date.from(timestamp));
        return point;
    }
}
