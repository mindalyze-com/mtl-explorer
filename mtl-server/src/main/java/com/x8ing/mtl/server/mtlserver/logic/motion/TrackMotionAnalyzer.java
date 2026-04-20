package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;

import java.util.List;

/**
 * Shared motion/stop analysis for GPS tracks. A "stop" is a contiguous run of
 * raw samples where the instantaneous speed is below {@link #STOP_SPEED_KMH}
 * for at least {@link #MIN_STOP_SEC}.
 *
 * <p>Used in two places:
 * <ul>
 *   <li>Per-segment annotation between two crossings (Race / measure tool).</li>
 *   <li>Per-track totals persisted on {@code GpsTrack} at ingest time, so the
 *       client can display an authoritative "stopped time" without trying to
 *       reconstruct it from the simplified track variants (which drop the
 *       low-speed samples the detector depends on).</li>
 * </ul>
 */
public final class TrackMotionAnalyzer {

    /**
     * Speed threshold (km/h) below which a sample counts as "stopped".
     */
    public static final double STOP_SPEED_KMH = 0.5;

    /**
     * Minimum contiguous duration (seconds) for a low-speed run to count as a stop.
     */
    public static final double MIN_STOP_SEC = 30.0;

    private TrackMotionAnalyzer() {
    }

    /**
     * Detects stops across the full point stream, using the timestamp of the
     * first and last point as the range. Convenience wrapper around
     * {@link #detectStopsInRange(List, long, long)}.
     */
    public static SegmentNotes detectStopsInTrack(List<GpsTrackDataPoint> points) {
        if (points == null || points.size() < 2) {
            return new SegmentNotes(0, 0.0, 0.0);
        }
        Long firstMs = firstTimestampMs(points);
        Long lastMs = lastTimestampMs(points);
        if (firstMs == null || lastMs == null || lastMs <= firstMs) {
            return new SegmentNotes(0, 0.0, 0.0);
        }
        return detectStopsInRange(points, firstMs, lastMs);
    }

    /**
     * Detects stops inside a timestamp range of the raw point stream.
     *
     * <p>Algorithm: iterate points whose timestamps lie within
     * {@code [fromMs, toMs]}. For each consecutive pair, compute the
     * instantaneous speed from {@code distanceInMeterSinceStart} deltas
     * (falling back to geometry if distances are absent). A "stopped" run
     * accumulates while consecutive pairs stay below {@link #STOP_SPEED_KMH};
     * when the run ends (or the range ends), the run is counted as a stop iff
     * its duration is &ge; {@link #MIN_STOP_SEC}.
     */
    public static SegmentNotes detectStopsInRange(List<GpsTrackDataPoint> points, long fromMs, long toMs) {
        SegmentNotes notes = new SegmentNotes(0, 0.0, 0.0);
        if (points == null || points.isEmpty()) {
            return notes;
        }

        double stopStartSec = -1.0; // -1 = not in a stop
        double stopEndSec = -1.0;

        GpsTrackDataPoint prev = null;
        for (GpsTrackDataPoint p : points) {
            if (p.getPointTimestamp() == null) continue;
            long ts = p.getPointTimestamp().getTime();
            if (ts < fromMs || ts > toMs) {
                prev = null; // reset so we don't compare across the boundary
                continue;
            }
            if (prev == null) {
                prev = p;
                continue;
            }
            long prevTs = prev.getPointTimestamp().getTime();
            double dtSec = (ts - prevTs) / 1000.0;
            if (dtSec <= 0) {
                prev = p;
                continue;
            }
            Double d1 = prev.getDistanceInMeterSinceStart();
            Double d2 = p.getDistanceInMeterSinceStart();
            double speedKmh;
            if (d1 != null && d2 != null) {
                double dMeters = Math.max(0.0, d2 - d1);
                speedKmh = (dMeters / dtSec) * 3.6;
            } else {
                double dMeters = GPXReader.getDistanceBetweenTwoWGS84(
                        prev.getPointLongLat().getCoordinate(),
                        p.getPointLongLat().getCoordinate());
                speedKmh = (dMeters / dtSec) * 3.6;
            }

            boolean stopped = speedKmh < STOP_SPEED_KMH;
            if (stopped) {
                if (stopStartSec < 0) {
                    stopStartSec = prevTs / 1000.0;
                }
                stopEndSec = ts / 1000.0;
            } else if (stopStartSec >= 0) {
                flushStop(notes, stopStartSec, stopEndSec);
                stopStartSec = -1.0;
                stopEndSec = -1.0;
            }
            prev = p;
        }
        if (stopStartSec >= 0) {
            flushStop(notes, stopStartSec, stopEndSec);
        }
        return notes;
    }

    private static void flushStop(SegmentNotes notes, double startSec, double endSec) {
        double dur = Math.max(0.0, endSec - startSec);
        if (dur >= MIN_STOP_SEC) {
            notes.stopCount++;
            notes.totalStoppedSec += dur;
            if (dur > notes.longestStopSec) {
                notes.longestStopSec = dur;
            }
        }
    }

    private static Long firstTimestampMs(List<GpsTrackDataPoint> points) {
        for (GpsTrackDataPoint p : points) {
            if (p.getPointTimestamp() != null) {
                return p.getPointTimestamp().getTime();
            }
        }
        return null;
    }

    private static Long lastTimestampMs(List<GpsTrackDataPoint> points) {
        for (int i = points.size() - 1; i >= 0; i--) {
            GpsTrackDataPoint p = points.get(i);
            if (p.getPointTimestamp() != null) {
                return p.getPointTimestamp().getTime();
            }
        }
        return null;
    }
}
