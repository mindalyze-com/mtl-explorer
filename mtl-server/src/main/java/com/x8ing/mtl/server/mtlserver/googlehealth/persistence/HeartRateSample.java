package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;
import java.time.LocalDateTime;

public record HeartRateSample(
        Long dataSourceId,
        Instant sampleTime,
        String originalSampleTime,
        String utcOffset,
        LocalDateTime civilTime,
        int beatsPerMinute,
        String motionContext,
        String sensorLocation,
        String fingerprint,
        String identityKey,
        String googleResourceName,
        String rawJson
) {
    public HeartRateSample withDataSourceId(Long sourceId) {
        return new HeartRateSample(sourceId, sampleTime, originalSampleTime, utcOffset, civilTime,
                beatsPerMinute, motionContext, sensorLocation, fingerprint, identityKey, googleResourceName, rawJson);
    }

    public HeartRateSample(Long source, Instant time, String original, String offset, LocalDateTime civil,
                           int bpm, String motion, String sensor, String fingerprint) {
        this(source, time, original, offset, civil, bpm, motion, sensor, fingerprint, fingerprint, null, null);
    }
}
