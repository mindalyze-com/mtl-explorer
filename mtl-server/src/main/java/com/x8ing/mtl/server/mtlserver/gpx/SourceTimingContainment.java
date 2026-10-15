package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Retains source observations after importing a geometry-only representation of an unsupported clock.
 */
public final class SourceTimingContainment {
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    private SourceTimingContainment() {
    }

    /** Restore only excluded source timestamps in RAW, retaining the rest of the canonical clock. */
    public static void preserveExcludedTimes(GPXReader.LoadResult result, Map<Integer, Instant> timestamps) {
        if (result.gpsTrack.getLoadStatus() != GpsTrack.LOAD_STATUS.SUCCESS || result.trackRAW == null) return;
        var coordinates = result.trackRAW.getCoordinates();
        List<GpsObservationEvidence> evidence = new ArrayList<>();
        for (int i = 0; i < result.observationEvidence.size(); i++) {
            var observation = result.observationEvidence.get(i);
            Instant original = timestamps.get(observation.sourcePointIndex());
            if (original == null) {
                evidence.add(observation);
                continue;
            }
            coordinates[i].setM(original.getEpochSecond() + original.getNano() / 1_000_000_000.0);
            var decisions = new ArrayList<>(observation.decisions());
            decisions.remove("TIME_MISSING");
            decisions.add("TIME_EXCURSION");
            evidence.add(new GpsObservationEvidence(observation.sourcePointIndex(), observation.recordingSection(),
                    observation.sourceRecordingSection(), observation.fix(), observation.satellites(), observation.hdop(),
                    observation.vdop(), observation.speedMetresPerSecond(), observation.source(),
                    observation.canonicalPointIndex(), List.copyOf(decisions)));
        }
        result.trackRAW = GEOMETRY.createLineString(coordinates);
        result.observationEvidence = List.copyOf(evidence);
        result.gpsTrack.addLoadMessage("An isolated clock spike requires review. Its original timestamp is retained in RAW; only its derived time is unavailable.");
    }

    public static void preserveOriginal(GPXReader.LoadResult result, List<Coordinate> original, Date firstTimestamp, int section) {
        if (result.gpsTrack.getLoadStatus() != GpsTrack.LOAD_STATUS.SUCCESS) return;
        if (original.size() >= 2) result.trackRAW = GEOMETRY.createLineString(original.toArray(Coordinate[]::new));
        result.rawRecordingBreakBeforePointIndexes = List.of();
        List<GpsObservationEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < result.observationEvidence.size(); index++) {
            var observation = result.observationEvidence.get(index);
            var decisions = new ArrayList<>(observation.decisions());
            if (Double.isFinite(original.get(index).getM())) decisions.remove("TIME_MISSING");
            decisions.add("TIMING_UNRELIABLE");
            evidence.add(new GpsObservationEvidence(observation.sourcePointIndex(), observation.recordingSection(),
                    observation.sourceRecordingSection(), observation.fix(), observation.satellites(), observation.hdop(),
                    observation.vdop(), observation.speedMetresPerSecond(), observation.source(),
                    observation.canonicalPointIndex(), List.copyOf(decisions)));
        }
        result.observationEvidence = List.copyOf(evidence);
        if (firstTimestamp != null) {
            result.gpsTrack.setStartDate(firstTimestamp);
            result.gpsTrack.setEndDate(firstTimestamp);
        }
        result.gpsTrack.setTimingUnreliable(true);
        result.gpsTrack.addLoadMessage("Source timing requires review. Original timestamps are retained in RAW; time-based metrics are unavailable.");
    }
}
