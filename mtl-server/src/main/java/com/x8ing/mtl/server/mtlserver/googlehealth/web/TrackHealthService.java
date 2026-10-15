package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackHealthRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackHeartRateSummaryRow;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackMovingHeartRateSummaryRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class TrackHealthService {

    static final int DEFAULT_MAX_BUCKETS = 400;
    static final int MIN_MAX_BUCKETS = 10;
    static final int MAX_MAX_BUCKETS = 2_000;

    private final GpsTrackRepository gpsTrackRepository;
    private final TrackHealthRepository trackHealthRepository;

    public TrackHealthService(
            GpsTrackRepository gpsTrackRepository,
            TrackHealthRepository trackHealthRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.trackHealthRepository = trackHealthRepository;
    }

    public TrackHealthResponse get(long trackId, Integer requestedMaxBuckets) {
        GpsTrack track = gpsTrackRepository.findById(trackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found"));
        if (track.isTimingUnreliable()) return unavailable(trackId, null, null);
        Instant start = instant(track.getStartDate());
        Instant end = instant(track.getEndDate());
        if (start == null || end == null || end.isBefore(start)) {
            return unavailable(trackId, start, end);
        }

        TrackHeartRateSummaryRow summary = trackHealthRepository.findHeartRateSummary(start, end).orElse(null);
        if (summary == null) {
            return unavailable(trackId, start, end);
        }

        int maxBuckets = clampMaxBuckets(requestedMaxBuckets);
        TrackMovingHeartRateSummaryRow movingSummary = trackHealthRepository.findMovingHeartRateSummary(trackId, start, end);
        List<TrackHeartRatePoint> points = trackHealthRepository.findHeartRateBuckets(start, end, maxBuckets).stream()
                .map(bucket -> new TrackHeartRatePoint(
                        bucket.timestamp(),
                        elapsedSeconds(start, bucket.timestamp()),
                        bucket.averageBpm(),
                        bucket.minimumBpm(),
                        bucket.maximumBpm(),
                        bucket.sampleCount()))
                .toList();
        TrackHeartRateSummary heartRate = new TrackHeartRateSummary(
                summary.sampleCount(),
                summary.averageBpm(),
                movingSummary.averageBpm(),
                movingSummary.sampleCount(),
                summary.minimumBpm(),
                summary.maximumBpm(),
                summary.firstSampleTimestamp(),
                summary.lastSampleTimestamp(),
                points);
        return new TrackHealthResponse(
                trackId,
                true,
                TrackHealthResponse.Source.GOOGLE_HEALTH,
                start,
                end,
                heartRate);
    }

    private static TrackHealthResponse unavailable(long trackId, Instant start, Instant end) {
        return new TrackHealthResponse(
                trackId,
                false,
                TrackHealthResponse.Source.GOOGLE_HEALTH,
                start,
                end,
                null);
    }

    private static int clampMaxBuckets(Integer requestedMaxBuckets) {
        int value = requestedMaxBuckets == null ? DEFAULT_MAX_BUCKETS : requestedMaxBuckets;
        return Math.max(MIN_MAX_BUCKETS, Math.min(MAX_MAX_BUCKETS, value));
    }

    private static double elapsedSeconds(Instant start, Instant timestamp) {
        return Duration.between(start, timestamp).toMillis() / 1_000.0;
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
