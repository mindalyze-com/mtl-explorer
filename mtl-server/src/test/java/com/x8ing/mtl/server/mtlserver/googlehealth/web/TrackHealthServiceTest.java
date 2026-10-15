package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackHealthRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackHeartRateBucketRow;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackHeartRateSummaryRow;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackMovingHeartRateSummaryRow;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TrackHealthServiceTest {

    private static final long TRACK_ID = 42L;
    private static final Instant TRACK_START = Instant.parse("2026-08-29T08:00:00Z");
    private static final Instant TRACK_END = Instant.parse("2026-08-29T10:00:00Z");

    private final GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
    private final TrackHealthRepository trackHealthRepository = mock(TrackHealthRepository.class);
    private final TrackHealthService service = new TrackHealthService(gpsTrackRepository, trackHealthRepository);

    @Test
    void unsupportedSourceTimingNeverQueriesExternalSensorAlignment() {
        GpsTrack track = track();
        track.setTimingUnreliable(true);
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track));
        TrackHealthResponse response = service.get(TRACK_ID, 200);
        assertThat(response.available()).isFalse();
        assertThat(response.trackStartTimestamp()).isNull();
        assertThat(response.trackEndTimestamp()).isNull();
        verifyNoInteractions(trackHealthRepository);
    }

    @Test
    void returnsUnavailableWhenTheTrackHasNoHeartRateSamples() {
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track()));
        when(trackHealthRepository.findHeartRateSummary(TRACK_START, TRACK_END)).thenReturn(Optional.empty());

        TrackHealthResponse response = service.get(TRACK_ID, 200);

        assertThat(response.available()).isFalse();
        assertThat(response.source()).isEqualTo(TrackHealthResponse.Source.GOOGLE_HEALTH);
        assertThat(response.heartRate()).isNull();
        assertThat(response.trackStartTimestamp()).isEqualTo(TRACK_START);
        assertThat(response.trackEndTimestamp()).isEqualTo(TRACK_END);
        verify(trackHealthRepository).findHeartRateSummary(TRACK_START, TRACK_END);
    }

    @Test
    void returnsSummaryAndTimeRelativeGraphPointsForOverlappingSamples() {
        Instant firstSample = TRACK_START.plusSeconds(30);
        Instant lastSample = TRACK_START.plusSeconds(90);
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track()));
        when(trackHealthRepository.findHeartRateSummary(TRACK_START, TRACK_END)).thenReturn(Optional.of(
                new TrackHeartRateSummaryRow(3, 112.5, 86, 143, firstSample, lastSample)));
        when(trackHealthRepository.findMovingHeartRateSummary(TRACK_ID, TRACK_START, TRACK_END))
                .thenReturn(new TrackMovingHeartRateSummaryRow(1, 143.0));
        when(trackHealthRepository.findHeartRateBuckets(TRACK_START, TRACK_END, 200)).thenReturn(List.of(
                new TrackHeartRateBucketRow(firstSample, 90.0, 86, 94, 2),
                new TrackHeartRateBucketRow(lastSample, 143.0, 143, 143, 1)));

        TrackHealthResponse response = service.get(TRACK_ID, 200);

        assertThat(response.available()).isTrue();
        assertThat(response.heartRate().sampleCount()).isEqualTo(3);
        assertThat(response.heartRate().averageBpm()).isEqualTo(112.5);
        assertThat(response.heartRate().movingAverageBpm()).isEqualTo(143);
        assertThat(response.heartRate().movingSampleCount()).isEqualTo(1);
        assertThat(response.heartRate().minimumBpm()).isEqualTo(86);
        assertThat(response.heartRate().maximumBpm()).isEqualTo(143);
        assertThat(response.heartRate().points())
                .extracting(TrackHeartRatePoint::elapsedSeconds)
                .containsExactly(30.0, 90.0);
    }

    @Test
    void clampsRequestedGraphDensity() {
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track()));
        when(trackHealthRepository.findHeartRateSummary(TRACK_START, TRACK_END)).thenReturn(Optional.of(
                new TrackHeartRateSummaryRow(1, 100, 100, 100, TRACK_START, TRACK_START)));
        when(trackHealthRepository.findMovingHeartRateSummary(TRACK_ID, TRACK_START, TRACK_END))
                .thenReturn(new TrackMovingHeartRateSummaryRow(0, null));
        when(trackHealthRepository.findHeartRateBuckets(
                TRACK_START, TRACK_END, TrackHealthService.MAX_MAX_BUCKETS)).thenReturn(List.of());

        TrackHealthResponse response = service.get(TRACK_ID, Integer.MAX_VALUE);

        assertThat(response.heartRate().movingAverageBpm()).isNull();
        assertThat(response.heartRate().movingSampleCount()).isZero();

        verify(trackHealthRepository).findHeartRateBuckets(
                TRACK_START, TRACK_END, TrackHealthService.MAX_MAX_BUCKETS);
    }

    @Test
    void avoidsHealthQueriesWhenTrackTimestampsAreMissing() {
        GpsTrack track = new GpsTrack();
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track));

        TrackHealthResponse response = service.get(TRACK_ID, null);

        assertThat(response.available()).isFalse();
        verifyNoInteractions(trackHealthRepository);
    }

    @Test
    void rejectsUnknownTrack() {
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TRACK_ID, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    private static GpsTrack track() {
        GpsTrack track = new GpsTrack();
        track.setStartDate(Date.from(TRACK_START));
        track.setEndDate(Date.from(TRACK_END));
        return track;
    }
}
