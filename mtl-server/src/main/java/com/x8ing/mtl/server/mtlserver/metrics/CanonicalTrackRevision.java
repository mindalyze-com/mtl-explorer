package com.x8ing.mtl.server.mtlserver.metrics;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Content revision also covers point/event changes that do not increment the parent track version.
 */
public final class CanonicalTrackRevision {
    public static final String CALCULATION_VERSION = "segment-analysis-5";

    private CanonicalTrackRevision() {
    }

    public static String of(GpsTrack track, List<GpsTrackDataPoint> points, List<GpsTrackEvent> events) {
        MessageDigest digest = digest();
        add(digest, CALCULATION_VERSION, track.getId(), track.getVersion(), track.getActivityType(), track.isTimingUnreliable(), track.getMotionAnalysisVersion(), track.getMotionAnalysisStatus());
        for (var p : points) {
            add(digest, p.getId(), p.getGpsTrackDataId(), p.getPointIndex(), millis(p.getPointTimestamp()),
                    p.getPointLongLat(), p.getPointAltitude(), p.getIntervalStatus(), p.getMotionState(), p.getMotionReason(), p.getDurationSinceStart(),
                    p.getDurationBetweenPointsInSec(), p.getDistanceInMeterSinceStart(), p.getDistanceInMeterBetweenPoints(),
                    p.getAscentInMeterSinceStart(), p.getDescentInMeterSinceStart(), p.getAscentInMeterBetweenPoints(),
                    p.getSpeedInKmhMovingWindow(), p.getSlopePercentageInMovingWindow(),
                    p.getEnergyTotalWh(), p.getEnergyCumulativeWh(), p.getPowerWatts());
        }
        for (var e : events)
            add(digest, e.getId(), e.getEventType(), e.getSource(), e.getStartPointIndex(),
                    e.getEndPointIndex(), millis(e.getStartTimestamp()), millis(e.getEndTimestamp()));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by the Java platform; failing here is not recoverable.
            org.slf4j.LoggerFactory.getLogger(CanonicalTrackRevision.class)
                    .error("Canonical revision failed: SHA-256 unavailable", exception);
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static void add(MessageDigest digest, Object... values) {
        for (Object value : values) {
            byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
    }

    private static Long millis(Date date) {
        return date == null ? null : date.getTime();
    }
}
